package com.stockflow.review;

import com.stockflow.common.exception.*;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.repository.OrderRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    private final JdbcTemplate jdbc;
    private final OrderRepository orders;
    public ReviewService(JdbcTemplate jdbc, OrderRepository orders) { this.jdbc = jdbc; this.orders = orders; }

    public record Review(Long id, Long product_id, int rating, int service_rating, String comment,
                         String customer_name, Instant created_at) {}
    public record ReviewPage(List<Review> content, long total_elements, double average_rating,
                             double average_service_rating, int page, int size) {}
    private static final String SELECT = "SELECT r.*, u.full_name AS customer_name FROM product_reviews r JOIN users u ON u.id=r.customer_id ";
    private final org.springframework.jdbc.core.RowMapper<Review> mapper = (rs, row) -> new Review(
            rs.getLong("id"), rs.getLong("product_id"), rs.getInt("rating"), rs.getInt("service_rating"),
            rs.getString("comment"), rs.getString("customer_name"), rs.getTimestamp("created_at").toInstant());

    @Transactional
    public Review create(Long orderId, Long productId, Long customerId, int rating, int serviceRating, String comment) {
        var order = orders.findLockedById(orderId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        if (!order.getCustomerId().equals(customerId)) throw new ForbiddenException("Bạn chỉ được đánh giá đơn của mình.");
        if (order.getStatus() != OrderStatus.DELIVERED) throw new ConflictException("Chỉ được đánh giá sau khi đơn đã giao thành công.");
        if (order.getItems().stream().noneMatch(item -> item.getProductId().equals(productId)))
            throw new BadRequestException("Sản phẩm không nằm trong đơn hàng này.");
        if (rating < 1 || rating > 5 || serviceRating < 1 || serviceRating > 5 || comment == null
                || comment.isBlank() || comment.length() > 2000) throw new BadRequestException("Vui lòng chọn 1–5 sao và nhập nhận xét tối đa 2.000 ký tự.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM product_reviews WHERE order_id=? AND product_id=?", Long.class, orderId, productId) > 0)
            throw new ConflictException("Bạn đã đánh giá sản phẩm trong đơn này.");
        jdbc.update("INSERT INTO product_reviews(order_id,product_id,customer_id,rating,service_rating,comment) VALUES (?,?,?,?,?,?)",
                orderId, productId, customerId, rating, serviceRating, comment.trim());
        return jdbc.queryForObject(SELECT + "WHERE r.order_id=? AND r.product_id=?", mapper, orderId, productId);
    }

    @Transactional(readOnly = true)
    public List<Review> forOrder(Long orderId, Long customerId) {
        var order = orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng."));
        if (!order.getCustomerId().equals(customerId)) throw new ForbiddenException("Bạn chỉ được xem đánh giá đơn của mình.");
        return jdbc.query(SELECT + "WHERE r.order_id=? ORDER BY r.id", mapper, orderId);
    }

    @Transactional(readOnly = true)
    public ReviewPage forProduct(Long productId, int page) {
        if (page < 0 || page > 100000) throw new BadRequestException("Trang không hợp lệ.");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=?", Long.class, productId) == 0)
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm.");
        var parents = jdbc.queryForList("SELECT product_id FROM product_variants WHERE sku_product_id=?", Long.class, productId);
        Long root = parents.isEmpty() ? productId : parents.get(0);
        String filter = "WHERE r.product_id=? OR r.product_id IN (SELECT sku_product_id FROM product_variants WHERE product_id=?)";
        var stats = jdbc.queryForMap("SELECT COUNT(*) AS total, COALESCE(AVG(r.rating * 1.0),0) AS average, COALESCE(AVG(r.service_rating * 1.0),0) AS service FROM product_reviews r " + filter, root, root);
        return new ReviewPage(jdbc.query(SELECT + filter + " ORDER BY r.created_at DESC, r.id DESC LIMIT 20 OFFSET ?", mapper, root, root, page * 20),
                ((Number) stats.get("total")).longValue(), ((Number) stats.get("average")).doubleValue(),
                ((Number) stats.get("service")).doubleValue(), page, 20);
    }
}
