import com.stockflow.payment.util.VNPayUtil;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** Standalone diagnostic; credentials must be supplied through environment variables. */
public class VNPayDiagnostic {
    public static void main(String[] args) throws Exception {
        String tmn = Objects.requireNonNull(System.getenv("VNPAY_TMN_CODE"), "VNPAY_TMN_CODE required").trim();
        String secret = Objects.requireNonNull(System.getenv("VNPAY_HASH_SECRET"), "VNPAY_HASH_SECRET required").trim();
        Path output = Path.of("target/vnpay-diagnostic");
        Files.createDirectories(output);
        long stamp = System.currentTimeMillis();
        for (int variant = 1; variant <= 4; variant++) {
            var now = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
            var date = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
            Map<String, String> p = new TreeMap<>();
            p.put("vnp_Version", "2.1.0"); p.put("vnp_Command", "pay"); p.put("vnp_TmnCode", tmn);
            p.put("vnp_Amount", "1000000"); p.put("vnp_CurrCode", "VND"); p.put("vnp_Locale", "vn");
            p.put("vnp_TxnRef", Long.toString(stamp + variant));
            p.put("vnp_OrderInfo", "ThanhToanDonHangDiagnostic"); p.put("vnp_OrderType", "other");
            p.put("vnp_ReturnUrl", "http://localhost:8080/api/v1/payments/vnpay/return");
            p.put("vnp_IpAddr", "127.0.0.1"); p.put("vnp_CreateDate", date.format(now));
            if (variant != 2) p.put("vnp_ExpireDate", date.format(now.plusMinutes(15)));
            var encoded = VNPayUtil.buildParameters(p);
            String hashData = variant == 4
                    ? p.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&"))
                    : encoded.hashData();
            String signature = VNPayUtil.hmacSha512(secret, hashData);
            if (variant == 3) signature = signature.toUpperCase(Locale.ROOT);
            String query = encoded.query() + (variant == 1 ? "&vnp_SecureHashType=HMACSHA512" : "")
                    + "&vnp_SecureHash=" + signature;
            Files.writeString(output.resolve("variant-" + variant + "-url.txt"),
                    "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?" + query);
            Files.writeString(output.resolve("variant-" + variant + "-hash-data.txt"), hashData);
        }
        System.out.println("Generated four variants for TMN " + tmn + "; secret not logged.");
    }
}
