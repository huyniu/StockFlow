package com.stockflow.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.stockflow.common.config.MailProperties;
import com.stockflow.common.mail.MailDeliveryService;
import com.stockflow.order.service.OrderMailDispatcher;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Real outbox SQL + HTTP request contract, isolated from any external email service. */
class BrevoOrderMailIntegrationTest {
    private SingleConnectionDataSource database;
    private JdbcTemplate jdbc;
    private RestClient http;
    private MockRestServiceServer server;
    private OrderMailDispatcher dispatcher;

    @BeforeEach
    void setup() {
        database = new SingleConnectionDataSource("jdbc:h2:mem:mail_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "", true);
        jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE orders(id BIGINT PRIMARY KEY, status VARCHAR(30) NOT NULL)");
        jdbc.update("INSERT INTO orders(id,status) VALUES(1,'CONFIRMED')");
        new ResourceDatabasePopulator(new FileSystemResource(
                "src/test/resources/db/migration/V29__create_order_notification_outbox.sql")).execute(database);
        jdbc.update("INSERT INTO order_notification_outbox(order_id,event_type,recipient,subject,message) "
                + "VALUES(1,'CONFIRMED','customer@example.test','Đơn hàng đã xác nhận','Mã đơn: SF-1')");
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        http = builder.build();
        dispatcher = dispatcher("test-key", true);
    }

    private OrderMailDispatcher dispatcher(String key, boolean enabled) {
        var properties = new MailProperties("brevo", "sender@example.test", "StockFlow", key, null, null);
        var delivery = new MailDeliveryService(properties,
                new DefaultListableBeanFactory().getBeanProvider(JavaMailSender.class), http);
        return new OrderMailDispatcher(jdbc, delivery, enabled);
    }

    private long id() {
        return jdbc.queryForObject("SELECT id FROM order_notification_outbox", Long.class);
    }

    private int attempts() {
        return jdbc.queryForObject("SELECT attempts FROM order_notification_outbox", Integer.class);
    }

    private Object sentAt() {
        return jdbc.queryForObject("SELECT sent_at FROM order_notification_outbox", Object.class);
    }

    @AfterEach
    void cleanup() {
        database.destroy();
        server.verify();
    }

    @Test
    void acceptedHttpsEmailIsMarkedSentAndNotSentAgain() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(header("api-key", "test-key"))
                .andExpect(jsonPath("$.textContent").value("Mã đơn: SF-1"))
                .andExpect(jsonPath("$.to[0].email").value("customer@example.test"))
                .andRespond(withSuccess("{\"messageId\":\"accepted\"}", MediaType.APPLICATION_JSON));
        dispatcher.dispatch();
        dispatcher.dispatch();
        dispatcher.sendOne(id());
        assertThat(sentAt()).isNotNull();
        assertThat(attempts()).isOne();
    }

    @Test
    void failedHttpsEmailKeepsOrderAndOutboxForRetry() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withSuccess("{\"messageId\":\"accepted-after-retry\"}", MediaType.APPLICATION_JSON));
        dispatcher.sendOne(id());
        assertThat(sentAt()).isNull();
        assertThat(attempts()).isOne();
        dispatcher.sendOne(id()); // lease delays retries; no immediate duplicate request
        assertThat(attempts()).isOne();
        jdbc.update("UPDATE order_notification_outbox SET next_attempt_at=CURRENT_TIMESTAMP");
        dispatcher.dispatch();
        assertThat(sentAt()).isNotNull();
        assertThat(attempts()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=1", String.class)).isEqualTo("CONFIRMED");
    }

    @Test
    void missingApiKeyDoesNotConsumeRetryBudget() {
        var unavailable = dispatcher("", true);
        unavailable.dispatch();
        unavailable.sendOne(id());
        assertThat(attempts()).isZero();
        assertThat(sentAt()).isNull();
    }

    @Test
    void disabledSchedulerDoesNotSendOrderEmails() {
        dispatcher("test-key", false).dispatch();
        assertThat(attempts()).isZero();
        assertThat(sentAt()).isNull();
    }

    @Test
    void exhaustedRetryBudgetDoesNotSendAgain() {
        jdbc.update("UPDATE order_notification_outbox SET attempts=5");
        dispatcher.dispatch();
        dispatcher.sendOne(id());
        assertThat(attempts()).isEqualTo(5);
        assertThat(sentAt()).isNull();
    }
}
