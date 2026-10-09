package com.stockflow.order.service;

import com.stockflow.common.mail.MailDeliveryService;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OrderMailDispatcher {
    private final JdbcTemplate jdbc;
    private final MailDeliveryService delivery;
    private final boolean enabled;
    public OrderMailDispatcher(JdbcTemplate jdbc, MailDeliveryService delivery,
            @Value("${app.mail.order-notifications-enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.delivery = delivery;
        this.enabled = enabled;
    }
    @Scheduled(fixedDelayString="${app.mail.dispatch-delay-ms:10000}",initialDelayString="${app.mail.dispatch-delay-ms:10000}")
    public void dispatch() {
        if(!enabled || !delivery.isConfigured())return;
        var rows=jdbc.queryForList("SELECT id FROM order_notification_outbox WHERE sent_at IS NULL AND attempts<5 AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY id LIMIT 20",Long.class);
        for(Long id:rows) sendOne(id);
    }
    public void sendOne(Long id) {
        if(!delivery.isConfigured())return;
        // Atomic lease prevents two app instances from concurrently sending the same queued message.
        int claimed=jdbc.update("UPDATE order_notification_outbox SET attempts=attempts+1,next_attempt_at=? WHERE id=? AND sent_at IS NULL AND attempts<5 AND next_attempt_at<=CURRENT_TIMESTAMP",Timestamp.from(Instant.now().plusSeconds(300)),id);
        if(claimed==0)return;
        var row=jdbc.queryForMap("SELECT recipient,subject,message FROM order_notification_outbox WHERE id=?",id);
        try {
            delivery.send((String)row.get("recipient"), (String)row.get("subject"), (String)row.get("message"));
            jdbc.update("UPDATE order_notification_outbox SET sent_at=CURRENT_TIMESTAMP WHERE id=?",id);
        }
        catch(org.springframework.mail.MailException e) {org.slf4j.LoggerFactory.getLogger(getClass()).warn("Order email {} failed; retry is queued",id);}
    }
}
