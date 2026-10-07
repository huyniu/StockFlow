package com.stockflow.order;

import static com.stockflow.user.support.UserTestFixtures.verifiedUser;
import static com.stockflow.order.support.CheckoutTestData.orderRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockflow.auth.security.JwtTokenProvider;
import com.stockflow.catalog.domain.*;
import com.stockflow.catalog.repository.*;
import com.stockflow.inventory.dto.StockInRequest;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.order.dto.*;
import com.stockflow.order.service.*;
import com.stockflow.user.domain.User;
import com.stockflow.user.repository.*;
import com.stockflow.warehouse.domain.*;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(properties={"app.mail.order-notifications-enabled=true","app.mail.dispatch-delay-ms=3600000","spring.datasource.url=jdbc:h2:mem:post_purchase;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class PostPurchaseIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JwtTokenProvider jwt;
    @Autowired CategoryRepository categories; @Autowired ProductRepository products;
    @Autowired WarehouseRepository warehouses; @Autowired UserRepository users; @Autowired RoleRepository roles;
    @Autowired InventoryService inventory; @Autowired OrderService orders; @Autowired JdbcTemplate jdbc;
    @Autowired OrderMailDispatcher dispatcher; @Autowired PlatformTransactionManager tx;
    @MockitoBean JavaMailSender mail;
    User customer,other,admin,staff; OrderResponse order; Long productId,warehouseId;
    @BeforeEach void setup(){
        reset(mail);String key=UUID.randomUUID().toString();
        var category=categories.save(new Category(key,key));
        var product=products.save(new Product(category,key,"Sản phẩm",new BigDecimal("10000"),ProductStatus.ACTIVE));
        var warehouse=warehouses.save(new Warehouse(key,"Kho","Địa chỉ",WarehouseStatus.ACTIVE));
        productId=product.getId();warehouseId=warehouse.getId();
        customer=user("CUSTOMER");other=user("CUSTOMER");admin=user("ADMIN");staff=user("WAREHOUSE_STAFF");
        inventory.stockIn(new StockInRequest(productId,warehouseId,20,"Initial"),admin.getId());
        order=orders.createOrder(orderRequest(warehouseId,List.of(new CreateOrderRequest.Item(productId,2))),customer.getId());
    }
    User user(String role){return users.save(verifiedUser(UUID.randomUUID()+"@postpurchase.test","hash","Khách",roles.findByName(role).orElseThrow()));}
    void deliver(){orders.confirmPaymentSimulation(order.id(),customer.getId());orders.packOrder(order.id(),admin.getId());orders.shipOrder(order.id(),new ShipOrderRequest("SHIP-"+order.id()),admin.getId());orders.deliverOrder(order.id(),admin.getId());}
    ResultActions call(String method,String path,Object body,User actor)throws Exception{
        var request=method.equals("GET")?get(path):post(path);request.header("Authorization","Bearer "+jwt.generateToken(actor));
        if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));return mvc.perform(request);
    }
    long create(String kind)throws Exception{
        return json.readTree(call("POST","/api/v1/returns",Map.of("order_id",order.id(),"kind",kind,"reason","Sản phẩm bị lỗi, cần hỗ trợ"),customer).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asLong();
    }
    int returns(){return jdbc.queryForObject("SELECT COUNT(*) FROM inventory_movements WHERE reference_type='ORDER' AND reference_id=? AND type='RETURN_RESTOCK'",Integer.class,order.id());}
    @Test void requestRequiresDeliveredOrderAndOwnership()throws Exception{
        call("POST","/api/v1/returns",Map.of("order_id",order.id(),"kind","RETURN","reason","Cần kiểm tra lại sản phẩm"),customer).andExpect(status().isConflict());
        deliver();call("POST","/api/v1/returns",Map.of("order_id",order.id(),"kind","RETURN","reason","Cần kiểm tra lại sản phẩm"),other).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM return_requests WHERE order_id=?",Integer.class,order.id())).isZero();
    }
    @Test void approvalDoesNotRestockUntilReceiptAndReceiptIsIdempotent()throws Exception{
        deliver();long id=create("RETURN");assertThat(returns()).isZero();
        call("POST","/api/v1/returns/"+id+"/receive",null,admin).andExpect(status().isConflict());
        call("POST","/api/v1/returns/"+id+"/review",Map.of("decision","APPROVED","note","Đồng ý nhận lại hàng để kiểm tra"),admin).andExpect(status().isOk());
        assertThat(returns()).isZero();assertThat(orders.getOrder(order.id(),customer.getId()).status().name()).isEqualTo("DELIVERED");
        for(int i=0;i<2;i++)call("POST","/api/v1/returns/"+id+"/receive",null,admin).andExpect(status().isOk()).andExpect(jsonPath("status").value("RECEIVED"));
        assertThat(returns()).isOne();assertThat(orders.getOrder(order.id(),customer.getId()).status().name()).isEqualTo("RETURNED");
        assertThat(jdbc.queryForObject("SELECT available_quantity+reserved_quantity FROM inventories WHERE product_id=? AND warehouse_id=?",Integer.class,productId,warehouseId)).isEqualTo(20);
    }
    @Test void rejectedRequestCannotBeReceivedAndPreservesInventory()throws Exception{
        deliver();long id=create("RETURN");
        call("POST","/api/v1/returns/"+id+"/review",Map.of("decision","REJECTED","note","Thông tin chưa đủ căn cứ"),admin).andExpect(status().isOk());
        call("POST","/api/v1/returns/"+id+"/receive",null,admin).andExpect(status().isConflict());assertThat(returns()).isZero();
    }
    @Test void customersAndWarehouseStaffCannotApprove()throws Exception{
        deliver();long id=create("EXCHANGE");
        for(User actor:List.of(customer,staff))call("POST","/api/v1/returns/"+id+"/review",Map.of("decision","APPROVED","note","Đồng ý"),actor).andExpect(status().isForbidden());
        call("GET","/api/v1/returns/"+id,null,other).andExpect(status().isForbidden());
        call("GET","/api/v1/returns",null,other).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void duplicateSubmissionKeepsOneRequest()throws Exception{
        deliver();long id=create("EXCHANGE");assertThat(create("EXCHANGE")).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM return_requests WHERE order_id=?",Integer.class,order.id())).isOne();
    }
    byte[] png()throws Exception{
        var image=new java.awt.image.BufferedImage(4,4,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var buffer=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",buffer);return buffer.toByteArray();
    }
    ResultActions upload(long id,byte[] bytes,User actor)throws Exception{
        return mvc.perform(multipart("/api/v1/returns/"+id+"/images").file(new MockMultipartFile("file","photo.png","image/png",bytes)).header("Authorization","Bearer "+jwt.generateToken(actor)));
    }
    @Test void evidenceImagesArePrivateAndValidated()throws Exception{
        deliver();long id=create("RETURN");upload(id,"<svg onload='alert(1)'/>".getBytes(),customer).andExpect(status().isBadRequest());
        upload(id,png(),other).andExpect(status().isForbidden());
        var result=upload(id,png(),customer).andExpect(status().isOk()).andReturn();
        long imageId=json.readTree(result.getResponse().getContentAsString()).path("images").get(0).path("id").asLong();String path="/api/v1/returns/"+id+"/images/"+imageId;
        mvc.perform(get(path)).andExpect(status().isUnauthorized());call("GET",path,null,other).andExpect(status().isForbidden());
        call("GET",path,null,admin).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void imageLimitAndReviewFreezeAreEnforced()throws Exception{
        deliver();long id=create("RETURN");for(int i=0;i<5;i++)upload(id,png(),customer).andExpect(status().isOk());upload(id,png(),customer).andExpect(status().isBadRequest());
        call("POST","/api/v1/returns/"+id+"/review",Map.of("decision","APPROVED","note","Đồng ý"),admin).andExpect(status().isOk());upload(id,png(),customer).andExpect(status().isConflict());
    }
    @Test void notificationsAreQueuedOnceForEachCommittedTransition(){
        deliver();orders.deliverOrder(order.id(),admin.getId());
        var events=jdbc.queryForList("SELECT event_type FROM order_notification_outbox WHERE order_id=? ORDER BY id",String.class,order.id());
        assertThat(events).containsExactly("CONFIRMED","SHIPPED","DELIVERED");verifyNoInteractions(mail);
    }
    @Test void rollbackDoesNotLeaveNotification(){
        new TransactionTemplate(tx).executeWithoutResult(status->{orders.confirmPaymentSimulation(order.id(),customer.getId());status.setRollbackOnly();});
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_notification_outbox WHERE order_id=?",Integer.class,order.id())).isZero();
        assertThat(orders.getOrder(order.id(),customer.getId()).status().name()).isEqualTo("PENDING");
    }
    @Test void mailDeliveryRetriesWithoutChangingOrderAndDoesNotSendAgain(){
        orders.confirmPaymentSimulation(order.id(),customer.getId());Long id=jdbc.queryForObject("SELECT id FROM order_notification_outbox WHERE order_id=?",Long.class,order.id());
        doThrow(new MailSendException("SMTP offline")).doNothing().when(mail).send(any(SimpleMailMessage.class));
        dispatcher.sendOne(id);assertThat(orders.getOrder(order.id(),customer.getId()).status().name()).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT sent_at FROM order_notification_outbox WHERE id=?",Object.class,id)).isNull();
        dispatcher.sendOne(id);verify(mail,times(1)).send(any(SimpleMailMessage.class));
        jdbc.update("UPDATE order_notification_outbox SET next_attempt_at=CURRENT_TIMESTAMP WHERE id=?",id);dispatcher.sendOne(id);dispatcher.sendOne(id);
        var capture=ArgumentCaptor.forClass(SimpleMailMessage.class);verify(mail,times(2)).send(capture.capture());
        assertThat(capture.getValue().getTo()).containsExactly(customer.getEmail());assertThat(capture.getValue().getText()).contains(order.orderCode());
        assertThat(jdbc.queryForObject("SELECT sent_at FROM order_notification_outbox WHERE id=?",Object.class,id)).isNotNull();
    }
}
