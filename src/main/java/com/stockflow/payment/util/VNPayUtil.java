package com.stockflow.payment.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class VNPayUtil {
    private VNPayUtil() {}

    public record EncodedParameters(String hashData, String query) {}

    /** Encode đúng một lần từ giá trị gốc; cả hashData/query dùng cùng thứ tự và cùng encoding. */
    public static EncodedParameters buildParameters(Map<String, String> parameters) {
        List<String> fieldNames = new ArrayList<>(parameters.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        for (String fieldName : fieldNames) {
            if (fieldName.equals("vnp_SecureHash") || fieldName.equals("vnp_SecureHashType")) continue;
            String fieldValue = parameters.get(fieldName);
            if (fieldValue == null || fieldValue.isEmpty()) continue;
            String encodedKey = encode(fieldName);
            String encodedValue = encode(fieldValue);
            if (hashData.length() > 0) {
                hashData.append('&');
                query.append('&');
            }
            hashData.append(fieldName).append('=').append(encodedValue);
            query.append(encodedKey).append('=').append(encodedValue);
        }
        return new EncodedParameters(hashData.toString(), query.toString());
    }

    public static String sortedQuery(Map<String, String> parameters) {
        return buildParameters(parameters).query();
    }

    public static String hmacSha512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Không thể ký yêu cầu VNPay.", exception);
        }
    }

    /** Secret ngắn không được hiện toàn bộ hay làm substring vượt giới hạn. */
    public static String maskedSecret(String secret) {
        int length = secret == null ? 0 : secret.length();
        String masked = length > 8 ? secret.substring(0, 4) + "..." + secret.substring(length - 4) : "****";
        return masked + " (độ dài: " + length + ")";
    }

    public static boolean validSignature(String secret, Map<String, String> parameters) {
        String supplied = parameters.get("vnp_SecureHash");
        if (supplied == null || !supplied.matches("[a-fA-F0-9]{128}")) return false;
        byte[] actual = HexFormat.of().parseHex(hmacSha512(secret, buildParameters(parameters).hashData()));
        return MessageDigest.isEqual(actual, HexFormat.of().parseHex(supplied));
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.US_ASCII.toString());
        } catch (java.io.UnsupportedEncodingException exception) {
            throw new IllegalStateException("US-ASCII không khả dụng.", exception);
        }
    }
}
