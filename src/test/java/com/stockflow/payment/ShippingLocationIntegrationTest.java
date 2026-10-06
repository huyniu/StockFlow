package com.stockflow.payment;

import static com.stockflow.order.support.CheckoutTestData.orderRequest;
import static com.stockflow.user.support.UserTestFixtures.verifiedUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.catalog.domain.*;
import com.stockflow.catalog.repository.*;
import com.stockflow.common.config.VNPayConfig;
import com.stockflow.inventory.dto.StockInRequest;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.order.dto.CreateOrderRequest;
import com.stockflow.order.dto.OrderResponse;
import com.stockflow.order.service.OrderService;
import com.stockflow.payment.util.VNPayUtil;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.*;
import com.stockflow.warehouse.domain.*;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.LinkedMultiValueMap;

@SpringBootTest(properties = "ghn.token=MOCK_TOKEN")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShippingLocationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired VNPayConfig.Properties config;
    @Autowired JwtTokenProvider jwt;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired WarehouseRepository warehouses;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired InventoryService inventoryService;
    @Autowired OrderService orders;
    @Autowired JdbcTemplate jdbc;
    User customer;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.stockflow.shipping.client.GhnClient ghn;
    OrderResponse order;

    @BeforeEach
    void setup() {
        String key = UUID.randomUUID().toString();
        customer = user("CUSTOMER");
        User admin = user("ADMIN");
        var category = categories.save(new Category("VNPay " + key, key));
        var product = products.save(new Product(category, key, "Sản phẩm VNPay", new BigDecimal("125000.50"), ProductStatus.ACTIVE));
        var warehouse = warehouses.save(new Warehouse(key, "Kho VNPay", "Hà Nội", WarehouseStatus.ACTIVE));
        inventoryService.stockIn(new StockInRequest(product.getId(), warehouse.getId(), 5, "Fixture VNPay"), admin.getId());
        order = orders.createOrder(orderRequest(warehouse.getId(), List.of(new CreateOrderRequest.Item(product.getId(), 2))), customer.getId());
    }

    @Test void carrierPayloadUsesSavedDistrictAndWard() throws Exception {
        var created=place(shippingRequest(new BigDecimal("30000")),201,null);
        Long id=created.path("id").asLong();
        orders.confirmPaymentSimulation(id,customer.getId());
        User staff=user("ADMIN"); orders.packOrder(id,staff.getId());
        mvc.perform(post("/api/v1/orders/"+id+"/ghn-ship").header("Authorization",bearer(staff))).andExpect(status().isOk());
        var captor=org.mockito.ArgumentCaptor.forClass(com.stockflow.shipping.dto.GhnCreateOrderRequest.class);
        org.mockito.Mockito.verify(ghn).createShippingOrder(captor.capture());
        assertThat(captor.getValue().payload()).containsEntry("to_district_id",1450).containsEntry("to_ward_code","20907")
            .containsEntry("from_district_id",1450).containsEntry("cod_amount",0);
    }
    @Test void anonymousLocationsArePublicAndDependent() throws Exception {
        mvc.perform(get("/api/v1/locations/provinces")).andExpect(status().isOk()).andExpect(jsonPath("$[0].ProvinceID").value(201));
        mvc.perform(get("/api/v1/locations/districts").param("province_id","201")).andExpect(status().isOk()).andExpect(jsonPath("$[0].DistrictID").value(1450));
        mvc.perform(get("/api/v1/locations/wards").param("district_id","1450")).andExpect(status().isOk()).andExpect(jsonPath("$[0].WardCode").value("20907"));
        mvc.perform(get("/api/v1/locations/districts").param("province_id","999999")).andExpect(jsonPath("$").isEmpty());
    }
    @Test void anonymousFeeReturnsMockThirtyThousand() throws Exception {
        mvc.perform(post("/api/v1/locations/calculate-fee").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
            "warehouse_id",order.warehouseId(),"to_district_id",1450,"to_ward_code","20907","weight",500))))
            .andExpect(status().isOk()).andExpect(jsonPath("shipping_fee").value(30000)).andExpect(jsonPath("service_type_id").value(2));
    }
    @Test void feeRejectsWardFromDifferentDistrict() throws Exception {
        mvc.perform(post("/api/v1/locations/calculate-fee").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
            "warehouse_id",order.warehouseId(),"to_district_id",1442,"to_ward_code","20907","weight",500))))
            .andExpect(status().isBadRequest());
    }
    @Test void orderStoresFeeAndVnpayIncludesIt() throws Exception {
        var request=shippingRequest(new BigDecimal("30000.00"));
        var result=place(request,201,null);
        Long id=result.path("id").asLong();
        assertThat(result.path("shipping_fee").decimalValue()).isEqualByComparingTo("30000");
        assertThat(result.path("total_amount").decimalValue()).isEqualByComparingTo("280001");
        assertThat(jdbc.queryForMap("SELECT to_district_id,to_ward_code,shipping_fee FROM orders WHERE id=?",id))
            .containsEntry("to_district_id",1450).containsEntry("to_ward_code","20907").containsEntry("shipping_fee",new BigDecimal("30000.00"));
        order=orders.getOrder(id,customer.getId());
        assertThat(parseUrl(paymentUrl(customer)).get("vnp_Amount")).isEqualTo("28000100");
    }
    @Test void missingFeeIsComputedForStructuredDestination() throws Exception {
        var result=place(shippingRequest(null),201,null);
        assertThat(result.path("shipping_fee").decimalValue()).isEqualByComparingTo("30000");
    }
    @Test void rejectsTamperedFeeWithoutCreatingOrder() throws Exception {
        Long before=jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE customer_id=?",Long.class,customer.getId());
        place(shippingRequest(BigDecimal.ONE),409,null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE customer_id=?",Long.class,customer.getId())).isEqualTo(before);
    }
    @Test void legacyContractStillHasZeroShippingFee() {
        assertThat(orders.getOrder(order.id(),customer.getId()).shippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(order.totalAmount()).isEqualByComparingTo("250001");
    }
    @Test void idempotencyIncludesShippingDestinationAndFee() throws Exception {
        String key="shipping-"+UUID.randomUUID();
        var request=shippingRequest(new BigDecimal("30000.00"));
        var first=place(request,201,key); var replay=place(request,201,key);
        assertThat(first.path("id")).isEqualTo(replay.path("id"));
        var changed=new CreateOrderRequest(request.warehouseId(),request.items(),request.delivery(),1450,"20908",request.shippingFee());
        place(changed,409,key);
    }
    @Test void negativeFeeAndPartialDestinationAreRejected() throws Exception {
        place(shippingRequest(new BigDecimal("-1")),400,null);
        var r=shippingRequest(null);
        place(new CreateOrderRequest(r.warehouseId(),r.items(),r.delivery(),1450,null,null),400,null);
    }
    private CreateOrderRequest shippingRequest(BigDecimal fee) {
        var old=orderRequest(order.warehouseId(),List.of(new CreateOrderRequest.Item(order.items().get(0).productId(),2)));
        return new CreateOrderRequest(old.warehouseId(),old.items(),old.delivery(),1450,"20907",fee);
    }
    private com.fasterxml.jackson.databind.JsonNode place(CreateOrderRequest body,int expected,String key) throws Exception {
        var request=post("/api/v1/orders").header("Authorization",bearer(customer)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if(key!=null) request.header("Idempotency-Key",key);
        return json.readTree(mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }
    private User user(String role) {
        return users.save(verifiedUser(UUID.randomUUID() + "@vnpay.test", "fixture-hash", "Khách VNPay", roles.findByName(role).orElseThrow()));
    }

    private String bearer(User actor) { return "Bearer " + jwt.generateToken(actor); }

    private ResultActions create(User actor) throws Exception {
        return mvc.perform(post("/api/v1/payments/vnpay/create").header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("order_id", order.id()))));
    }

    private String paymentUrl(User actor) throws Exception {
        return json.readTree(create(actor).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .path("payment_url").asText();
    }

    private Map<String, String> parseUrl(String url) {
        Map<String, String> parameters = new TreeMap<>();
        for (String part : URI.create(url).getRawQuery().split("&")) {
            String[] pair = part.split("=", 2);
            parameters.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return parameters;
    }

    private Map<String, String> callback(String response, String status) throws Exception {
        Map<String, String> sent = parseUrl(paymentUrl(customer));
        Map<String, String> callback = new TreeMap<>();
        callback.put("vnp_TmnCode", config.tmnCode());
        callback.put("vnp_TxnRef", sent.get("vnp_TxnRef"));
        callback.put("vnp_Amount", sent.get("vnp_Amount"));
        callback.put("vnp_ResponseCode", response);
        callback.put("vnp_TransactionStatus", status);
        callback.put("vnp_TransactionNo", Long.toString(10000000 + order.id()));
        callback.put("vnp_BankCode", "NCB");
        callback.put("vnp_PayDate", DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))));
        sign(callback);
        return callback;
    }

    private void sign(Map<String, String> parameters) throws Exception {
        parameters.remove("vnp_SecureHash");
        parameters.put("vnp_SecureHash", independentSignature(parameters));
    }

    /** Ký callback độc lập với util production để phát hiện lỗi encode/sắp xếp. */
    private String independentSignature(Map<String, String> parameters) throws Exception {
        var encoded = new ArrayList<String>();
        for (var entry : new TreeMap<>(parameters).entrySet()) {
            encoded.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(config.hashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(String.join("&", encoded).getBytes(StandardCharsets.UTF_8)));
    }

    private ResultActions send(Map<String, String> parameters) throws Exception {
        var query = new LinkedMultiValueMap<String, String>();
        parameters.forEach(query::add);
        return mvc.perform(get("/api/v1/payments/vnpay/return").params(query));
    }

    private void assertOrderStatus(String expected) {
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?", String.class, order.id())).isEqualTo(expected);
    }
    private int dispatchCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE reference_id=? AND type='DISPATCH'", Integer.class, order.id());
    }
    private String transactionStatus() {
        return jdbc.queryForObject("SELECT status FROM payment_transactions WHERE order_id=?", String.class, order.id());
    }
}
