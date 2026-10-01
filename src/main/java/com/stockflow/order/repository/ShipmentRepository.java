package com.stockflow.order.repository;

import com.stockflow.order.domain.Shipment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tra cứu vận đơn cho fulfillment, kiểm tra hủy trước giao và DTO theo dõi của khách. */
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    /** Tìm vận đơn duy nhất của một đơn hàng; thao tác ghi luôn thực hiện sau khi khóa order. */
    Optional<Shipment> findByOrderId(Long orderId);

    /** Lấy vận đơn theo lô, tránh thêm một truy vấn shipment cho mỗi đơn ở /orders/my. */
    List<Shipment> findByOrderIdIn(Collection<Long> orderIds);

    /** Kiểm tra mã vận đơn đã thuộc đơn khác, còn constraint UNIQUE bảo vệ cạnh tranh tại database. */
    boolean existsByTrackingCodeAndOrderIdNot(String trackingCode, Long orderId);
}
