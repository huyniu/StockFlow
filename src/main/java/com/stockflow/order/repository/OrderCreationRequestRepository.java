package com.stockflow.order.repository;

import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Giữ khóa đặt hàng bằng UNIQUE và khóa dòng của database, hoạt động qua nhiều thread/instance. */
@Repository
public class OrderCreationRequestRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** Nhận JDBC template; mọi giá trị khóa/hash được bind, không ghép vào câu lệnh SQL. */
    public OrderCreationRequestRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Lần gửi đầu chiếm khóa; lần cạnh tranh chờ transaction trước rồi đọc bản ghi đã có. */
    public boolean claim(Long customerId, String key, String hash) {
        return jdbc.update("""
                INSERT INTO order_creation_requests (customer_id, request_key, request_hash)
                VALUES (:customerId, :key, :hash)
                ON CONFLICT DO NOTHING
                """, parameters(customerId, key).addValue("hash", hash)) == 1;
    }

    /** Chỉ khóa đúng lần đặt hàng, không khóa user hoặc tất cả đơn của khách. */
    public Optional<CreationRequest> findLocked(Long customerId, String key) {
        return jdbc.query("""
                SELECT id,
                       request_hash,
                       order_id
                FROM order_creation_requests
                WHERE customer_id = :customerId
                  AND request_key = :key
                FOR UPDATE
                """, parameters(customerId, key), (row, index) -> new CreationRequest(
                row.getLong("id"), row.getString("request_hash"), row.getObject("order_id", Long.class)))
                .stream().findFirst();
    }

    /** Gắn đơn thành công vào khóa trong cùng transaction với reserve và movements. */
    public void complete(Long id, Long orderId) {
        int updated = jdbc.update("""
                UPDATE order_creation_requests
                SET order_id = :orderId
                WHERE id = :id
                  AND order_id IS NULL
                """, new MapSqlParameterSource("id", id).addValue("orderId", orderId));
        if (updated != 1) {
            throw new IllegalStateException("Không thể ghi nhận khóa đặt hàng đã hoàn tất.");
        }
    }

    /** Bộ tham số dùng chung luôn bao gồm chủ sở hữu, tránh đọc khóa của tài khoản khác. */
    private MapSqlParameterSource parameters(Long customerId, String key) {
        return new MapSqlParameterSource("customerId", customerId).addValue("key", key);
    }

    /** Dữ liệu tối thiểu của một lần gửi; không chứa thông tin liên hệ của người nhận. */
    public record CreationRequest(Long id, String hash, Long orderId) {}
}
