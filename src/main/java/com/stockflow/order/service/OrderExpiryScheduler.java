package com.stockflow.order.service;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
/** Quét đơn hết hạn mỗi phút; lỗi một đơn không làm rollback hoặc bỏ qua các đơn khác. */
@Component
@ConditionalOnProperty(name = "app.orders.expiry.enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpiryScheduler {
 private static final Logger log = LoggerFactory.getLogger(OrderExpiryScheduler.class);
 private final OrderService orders;
 /** Nhận service qua proxy để mỗi expireOrder thực sự có transaction riêng. */
 public OrderExpiryScheduler(OrderService orders) { this.orders = orders; }
 /** Quét theo lô và xử lý lại an toàn khi nhiều instance cùng chạy scheduler. */
 @Scheduled(fixedDelayString = "${app.orders.expiry.delay-ms:60000}", initialDelayString = "${app.orders.expiry.delay-ms:60000}")
 public void expireReservations() {
  for (Long id : orders.expiredOrderIds()) {
   try { orders.expireOrder(id); }
   catch (RuntimeException exception) { log.error("Không thể hết hạn đơn {}; sẽ thử lại ở lần quét sau.", id, exception); }
  }
 }
}
