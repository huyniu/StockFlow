package com.stockflow.order.repository;
import com.stockflow.order.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
/** Lưu các dòng mặt hàng và snapshot giá thuộc đơn hàng. */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {}
