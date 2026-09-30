package com.stockflow.order.repository;
import com.stockflow.order.domain.Shipment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
/** Tra cứu vận đơn để xác minh điều kiện hủy trước giao hàng. */
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
 /** Tìm vận đơn duy nhất của một đơn hàng. */
 Optional<Shipment> findByOrderId(Long orderId);
}
