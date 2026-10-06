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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VNPayIpnIntegrationTest {
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

    @Test void validIpnAndReplayDispatchOnce() throws Exception {
        var p=callback("00","00");
        ipn(p).andExpect(status().isOk()).andExpect(jsonPath("RspCode").value("00"));
        ipn(p).andExpect(status().isOk()).andExpect(jsonPath("RspCode").value("02"));
        assertOrderStatus("CONFIRMED"); assertThat(dispatchCount()).isEqualTo(1);
    }
    @Test void returnThenIpnIsAcknowledgedWithoutDoubleDispatch() throws Exception {
        var p=callback("00","00"); send(p).andExpect(status().isFound());
        ipn(p).andExpect(jsonPath("RspCode").value("02"));
        assertThat(dispatchCount()).isEqualTo(1);
    }
    @Test void invalidSignatureRejectedWithoutMutation() throws Exception {
        var p=callback("00","00"); p.put("vnp_SecureHash","0".repeat(128));
        ipn(p).andExpect(status().isOk()).andExpect(jsonPath("RspCode").value("97"));
        assertOrderStatus("PENDING"); assertThat(dispatchCount()).isZero();
    }
    @Test void wrongAmountRejectedWithoutMutation() throws Exception {
        var p=callback("00","00"); p.put("vnp_Amount","100"); sign(p);
        ipn(p).andExpect(jsonPath("RspCode").value("04"));
        assertThat(transactionStatus()).isEqualTo("PENDING"); assertThat(dispatchCount()).isZero();
    }
    @Test void unknownReferenceReturns01() throws Exception {
        var p=callback("00","00"); p.put("vnp_TxnRef","999999999_999999999"); sign(p);
        ipn(p).andExpect(jsonPath("RspCode").value("01")); assertThat(dispatchCount()).isZero();
    }
    @Test void declinedPaymentAcknowledgedWithoutDispatch() throws Exception {
        var p=callback("24","02");
        ipn(p).andExpect(jsonPath("RspCode").value("00"));
        assertThat(transactionStatus()).isEqualTo("FAILED"); assertOrderStatus("PENDING");
        assertThat(dispatchCount()).isZero();
    }
    @Test void duplicateParametersRejected() throws Exception {
        var p=callback("00","00"); var q=new LinkedMultiValueMap<String,String>(); p.forEach(q::add);
        q.add("vnp_Amount", "100");
        mvc.perform(get("/api/v1/payments/vnpay/ipn").params(q)).andExpect(status().isOk()).andExpect(jsonPath("RspCode").value("97"));
        assertThat(dispatchCount()).isZero();
    }
    @Test void malformedSignedNotificationReturns99() throws Exception {
        var p=callback("00","00"); p.put("vnp_PayDate","invalid"); sign(p);
        ipn(p).andExpect(jsonPath("RspCode").value("99"));
        assertThat(transactionStatus()).isEqualTo("PENDING"); assertThat(dispatchCount()).isZero();
    }
    private ResultActions ipn(Map<String,String> p) throws Exception {
        var query=new LinkedMultiValueMap<String,String>(); p.forEach(query::add);
        return mvc.perform(get("/api/v1/payments/vnpay/ipn").params(query));
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
