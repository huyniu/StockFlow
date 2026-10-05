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
class VNPayIntegrationTest {
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

    @Test
    void createsSignedUrlAndPendingTransaction() throws Exception {
        String url = paymentUrl(customer);
        Map<String, String> parameters = parseUrl(url);
        assertThat(URI.create(url).getHost()).isEqualTo("sandbox.vnpayment.vn");
        assertThat(parameters).containsEntry("vnp_Amount", "25000100")
                .containsEntry("vnp_Version", "2.1.0").containsEntry("vnp_Command", "pay")
                .containsEntry("vnp_CurrCode", "VND").containsEntry("vnp_TmnCode", config.tmnCode())
                .containsEntry("vnp_ReturnUrl", config.returnUrl()).containsEntry("vnp_IpAddr", "127.0.0.1")
                .containsEntry("vnp_Locale", "vn").containsEntry("vnp_OrderType", "other");
        assertThat(parameters.get("vnp_TxnRef")).startsWith(order.id() + "_");
        assertThat(parameters.get("vnp_OrderInfo")).isEqualTo("ThanhToanDonHang" + order.id())
                .doesNotContain(" ", "+", "%20");
        assertThat(parameters.get("vnp_CreateDate")).matches("[0-9]{14}");
        assertThat(parameters.get("vnp_ExpireDate")).matches("[0-9]{14}");
        String signature = parameters.remove("vnp_SecureHash");
        assertThat(signature).matches("[0-9a-f]{128}").isEqualTo(independentSignature(parameters));
        assertThat(jdbc.queryForMap("SELECT status,payment_method,amount,txn_ref FROM payment_transactions WHERE order_id=?", order.id()))
                .containsEntry("status", "PENDING").containsEntry("payment_method", "VNPAY")
                .containsEntry("amount", new BigDecimal("250001.00")).containsEntry("txn_ref", parameters.get("vnp_TxnRef"));
    }

    @Test
    void validSuccessConfirmsOrderAndDispatchesExactlyOnce() throws Exception {
        Map<String, String> callback = callback("00", "00");
        send(callback).andExpect(status().isFound()).andExpect(header().string("Location",
                config.storefrontUrl() + "#orders?payment_status=success&order_id=" + order.id()));
        send(callback).andExpect(status().isFound());
        assertOrderStatus("CONFIRMED");
        assertThat(dispatchCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM inventories WHERE warehouse_id=?", Integer.class, order.warehouseId())).isZero();
        assertThat(jdbc.queryForObject("SELECT method FROM payments WHERE order_id=?", String.class, order.id())).isEqualTo("VNPAY");
        assertThat(jdbc.queryForMap("SELECT status,transaction_code,response_code,bank_code FROM payment_transactions WHERE order_id=?", order.id()))
                .containsEntry("status", "SUCCESS").containsEntry("transaction_code", callback.get("vnp_TransactionNo"))
                .containsEntry("response_code", "00").containsEntry("bank_code", "NCB");
        assertThat(jdbc.queryForObject("SELECT pay_date FROM payment_transactions WHERE order_id=?", java.sql.Timestamp.class, order.id())).isNotNull();
    }

    @Test
    void forgedSignatureReturns400WithoutChangingOrderOrLedger() throws Exception {
        Map<String, String> callback = callback("00", "00");
        callback.put("vnp_SecureHash", "0".repeat(128));
        send(callback).andExpect(status().isBadRequest());
        assertOrderStatus("PENDING");
        assertThat(dispatchCount()).isZero();
        assertThat(transactionStatus()).isEqualTo("PENDING");
    }

    @Test
    void signedAmountMismatchIsRejected() throws Exception {
        Map<String, String> callback = callback("00", "00");
        callback.put("vnp_Amount", "100");
        sign(callback);
        send(callback).andExpect(status().isBadRequest());
        assertOrderStatus("PENDING");
        assertThat(dispatchCount()).isZero();
    }

    @Test
    void failedPaymentStoresFailureAndKeepsReservation() throws Exception {
        send(callback("24", "02")).andExpect(status().isFound()).andExpect(header().string("Location",
                config.storefrontUrl() + "#orders?payment_status=failed&order_id=" + order.id()));
        assertOrderStatus("PENDING");
        assertThat(transactionStatus()).isEqualTo("FAILED");
        assertThat(dispatchCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT reserved_quantity FROM inventories WHERE warehouse_id=?", Integer.class, order.warehouseId())).isEqualTo(2);
    }

    @Test
    void response00WithFailedTransactionStatusDoesNotConfirmOrder() throws Exception {
        send(callback("00", "02")).andExpect(status().isFound());
        assertOrderStatus("PENDING");
        assertThat(transactionStatus()).isEqualTo("FAILED");
        assertThat(dispatchCount()).isZero();
    }

    @Test
    void anotherCustomerCannotCreatePaymentUrl() throws Exception {
        create(user("CUSTOMER")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_transactions WHERE order_id=?", Integer.class, order.id())).isZero();
    }

    @Test
    void anonymousCustomerCannotCreatePaymentUrl() throws Exception {
        mvc.perform(post("/api/v1/payments/vnpay/create").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("order_id", order.id())))).andExpect(status().isUnauthorized());
    }

    @Test
    void simulatedPaymentRemainsAvailableAndCannotBeDispatchedAgainByVNPay() throws Exception {
        Map<String, String> callback = callback("00", "00");
        mvc.perform(post("/api/v1/orders/" + order.id() + "/payment-simulations/confirm")
                .header("Authorization", bearer(customer))).andExpect(status().isOk());
        send(callback).andExpect(status().isFound());
        assertOrderStatus("CONFIRMED");
        assertThat(dispatchCount()).isEqualTo(1);
        assertThat(transactionStatus()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT method FROM payments WHERE order_id=?", String.class, order.id())).isEqualTo("SIMULATED_BANKING");
        create(customer).andExpect(status().isConflict());
    }

    @Test
    void expiredOrderCannotBeConfirmedBySuccessfulReturn() throws Exception {
        Map<String, String> callback = callback("00", "00");
        jdbc.update("UPDATE orders SET reservation_expires_at=DATEADD('MINUTE', -1, CURRENT_TIMESTAMP) WHERE id=?", order.id());
        send(callback).andExpect(status().isFound());
        assertOrderStatus("EXPIRED");
        assertThat(transactionStatus()).isEqualTo("FAILED");
        assertThat(dispatchCount()).isZero();
    }

    @Test
    void validSignatureForUnknownReferenceIsRejected() throws Exception {
        Map<String, String> callback = callback("00", "00");
        callback.put("vnp_TxnRef", order.id() + "_1");
        sign(callback);
        send(callback).andExpect(status().isBadRequest());
        assertThat(dispatchCount()).isZero();
    }

    @Test
    void duplicateCallbackParametersAreRejected() throws Exception {
        var parameters = new LinkedMultiValueMap<String, String>();
        callback("00", "00").forEach(parameters::add);
        parameters.add("vnp_Amount", "1");
        mvc.perform(get("/api/v1/payments/vnpay/return").params(parameters)).andExpect(status().isBadRequest());
        assertThat(dispatchCount()).isZero();
    }

    @Test
    void concurrentCallbacksDispatchOnlyOnce() throws Exception {
        Map<String, String> callback = callback("00", "00");
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Integer> task = () -> {
                start.await(10, java.util.concurrent.TimeUnit.SECONDS);
                return send(callback).andReturn().getResponse().getStatus();
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            start.countDown();
            assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(302);
            assertThat(second.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(302);
            assertThat(dispatchCount()).isEqualTo(1);
            assertThat(transactionStatus()).isEqualTo("SUCCESS");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void signedCallbackForAnotherMerchantIsRejected() throws Exception {
        Map<String, String> callback = callback("00", "00");
        callback.put("vnp_TmnCode", "OTHER123");
        sign(callback);
        send(callback).andExpect(status().isBadRequest());
        assertOrderStatus("PENDING");
        assertThat(dispatchCount()).isZero();
    }

    @Test
    void hmacMatchesRfc4231Sha512TestVector() {
        assertThat(VNPayUtil.hmacSha512("\u000b".repeat(20), "Hi There")).isEqualTo(
                "87aa7cdea5ef619d4ff0b4241a1d6cb02379f4e2ce4ec2787ad0b30545e17cde"
                + "daa833b7d6b8a702038b274eaea3f4e4be9d914eeb61f1702e696c203a126854");
        assertThat(VNPayUtil.sortedQuery(Map.of("vnp_Z", "a b", "vnp_A", "a+b&", "vnp_SecureHash", "ignored")))
                .isEqualTo("vnp_A=a%2Bb%26&vnp_Z=a+b");
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("vnp_Z", "");
        parameters.put("vnp_SecureHashType", "HMACSHA512");
        parameters.put("vnp_ReturnUrl", "http://localhost:8080/return?a=1&b=a+b");
        parameters.put("vnp_OrderInfo", "Thanh toan don hang 1");
        parameters.put("vnp_SecureHash", "excluded");
        parameters.put("vnp_A key", "+%&=");
        parameters.put("vnp_Null", null);
        var encoded = VNPayUtil.buildParameters(parameters);
        assertThat(encoded.hashData()).isEqualTo(
                "vnp_A key=%2B%25%26%3D&vnp_OrderInfo=Thanh+toan+don+hang+1"
                + "&vnp_ReturnUrl=http%3A%2F%2Flocalhost%3A8080%2Freturn%3Fa%3D1%26b%3Da%2Bb");
        assertThat(encoded.query()).isEqualTo(
                "vnp_A+key=%2B%25%26%3D&vnp_OrderInfo=Thanh+toan+don+hang+1"
                + "&vnp_ReturnUrl=http%3A%2F%2Flocalhost%3A8080%2Freturn%3Fa%3D1%26b%3Da%2Bb");
        assertThat(parameters).containsEntry("vnp_SecureHash", "excluded").containsEntry("vnp_SecureHashType", "HMACSHA512");
        parameters.put("vnp_SecureHash", VNPayUtil.hmacSha512("test-secret", encoded.hashData()));
        assertThat(VNPayUtil.validSignature("test-secret", parameters)).isTrue();
        assertThat(VNPayUtil.buildParameters(Map.of("vnp_OrderInfo", "Đơn hàng 1")).hashData())
                .isEqualTo("vnp_OrderInfo=%3F%3Fn+h%3Fng+1");
        assertThat(VNPayUtil.maskedSecret("XWMV" + "A".repeat(24) + "TFFC"))
                .isEqualTo("XWMV...TFFC (độ dài: 32)");
        assertThat(VNPayUtil.maskedSecret("short")).isEqualTo("**** (độ dài: 5)");
        assertThat(VNPayUtil.maskedSecret(null)).isEqualTo("**** (độ dài: 0)");
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
