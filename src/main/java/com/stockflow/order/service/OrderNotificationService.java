package com.stockflow.order.service;

import com.stockflow.order.domain.Order;
import com.stockflow.user.repository.UserRepository;
import java.text.NumberFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** The email is queued in the order transaction; network failure cannot roll back fulfillment. */
@Service
public class OrderNotificationService {
    private final JdbcTemplate jdbc; private final UserRepository users; private final boolean enabled;
    public OrderNotificationService(JdbcTemplate jdbc,UserRepository users,@Value("${app.mail.order-notifications-enabled:true}") boolean enabled) {this.jdbc=jdbc;this.users=users;this.enabled=enabled;}
    public void queue(Order order,String event) {
        if(!enabled)return;
        var user=users.findById(order.getCustomerId()).orElseThrow();
        String status=switch(event){case "CONFIRMED"->"Đơn hàng đã được xác nhận";case "SHIPPED"->"Đơn hàng đã được cập nhật sang đang giao";case "DELIVERED"->"Đơn hàng đã được giao thành công";default->throw new IllegalArgumentException("Invalid event");};
        String text="Chào "+user.getFullName()+",\n"+status+".\nMã đơn: "+order.getOrderCode()+"\nTổng thanh toán: "+NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(order.getTotalAmount())+" đ\n"
            +"Vui lòng mở mục Đơn hàng trong tài khoản StockFlow để xem phương thức thanh toán, loại vận đơn và chi tiết giao hàng. Đơn COD được thanh toán khi nhận hàng; vận đơn thử nghiệm/mô phỏng không có shipper đến lấy hàng.";
        jdbc.update("INSERT INTO order_notification_outbox(order_id,event_type,recipient,subject,message) VALUES(?,?,?,?,?)",order.getId(),event,user.getEmail(),"StockFlow - "+status,text);
    }
}
