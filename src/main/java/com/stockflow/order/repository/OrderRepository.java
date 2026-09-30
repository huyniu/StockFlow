package com.stockflow.order.repository;
import com.stockflow.order.domain.*;
import java.time.Instant;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
/** Truy vấn đơn theo chủ sở hữu và khóa từng đơn khi thay đổi vòng đời. */
public interface OrderRepository extends JpaRepository<Order, Long> {
 /** Lịch sử chỉ lấy các đơn thuộc khách hàng hiện tại. */
 Page<Order> findByCustomerId(Long customerId, Pageable pageable);
 /** Khóa đơn để thanh toán, hủy và hết hạn không cùng thay đổi trạng thái. */
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select o from Order o where o.id = :id")
 Optional<Order> findLockedById(@Param("id") Long id);
 /** Quét theo lô các đơn hết hạn, không nạp toàn bộ dữ liệu vào bộ nhớ. */
 @Query("select o.id from Order o where o.status = :status and o.reservationExpiresAt <= :now order by o.reservationExpiresAt, o.id")
 List<Long> findExpiredIds(@Param("status") OrderStatus status, @Param("now") Instant now, Pageable pageable);
}
