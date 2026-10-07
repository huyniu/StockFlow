package com.stockflow.order.service;

import com.stockflow.common.exception.*;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.repository.OrderRepository;
import com.stockflow.user.repository.UserRepository;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReturnRequestService {
    private final JdbcTemplate jdbc;private final OrderRepository orders;private final UserRepository users;private final OrderService fulfillment;
    public ReturnRequestService(JdbcTemplate jdbc,OrderRepository orders,UserRepository users,OrderService fulfillment){this.jdbc=jdbc;this.orders=orders;this.users=users;this.fulfillment=fulfillment;}
    private boolean manager(Long userId){var user=users.findById(userId).orElseThrow();return Set.of("ADMIN","MANAGER").contains(user.getRole().getName());}
    private Map<String,Object> own(Long id,Long userId) {
        var row=jdbc.queryForList("SELECT * FROM return_requests WHERE id=?",id).stream().findFirst().orElseThrow(()->new ResourceNotFoundException("Không tìm thấy yêu cầu."));
        if(((Number)row.get("customer_id")).longValue()!=userId && !manager(userId)) throw new ForbiddenException("Bạn không được xem yêu cầu này.");
        return row;
    }
    private Map<String,Object> details(Map<String,Object> row) {
        var result=new LinkedHashMap<>(row);result.put("images",jdbc.queryForList("SELECT id,mime_type FROM return_request_images WHERE request_id=? ORDER BY id",row.get("id")));return result;
    }
    @Transactional(readOnly=true) public List<Map<String,Object>> list(Long userId) {
        var rows=manager(userId)?jdbc.queryForList("SELECT r.*,o.order_code FROM return_requests r JOIN orders o ON o.id=r.order_id ORDER BY r.id DESC LIMIT 100"):
            jdbc.queryForList("SELECT r.*,o.order_code FROM return_requests r JOIN orders o ON o.id=r.order_id WHERE r.customer_id=? ORDER BY r.id DESC LIMIT 100",userId);
        return rows.stream().map(this::details).toList();
    }
    @Transactional(readOnly=true) public Map<String,Object> get(Long id,Long userId){return details(own(id,userId));}
    @Transactional public Map<String,Object> create(Long orderId,Long userId,String kind,String reason) {
        var order=orders.findLockedById(orderId).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy đơn."));
        if(!order.getCustomerId().equals(userId))throw new ForbiddenException("Bạn chỉ được yêu cầu cho đơn của mình.");
        if(order.getStatus()!=OrderStatus.DELIVERED)throw new ConflictException("Chỉ yêu cầu đổi trả sau khi đã nhận hàng.");
        var existing=jdbc.queryForList("SELECT * FROM return_requests WHERE order_id=?",orderId);
        if(!existing.isEmpty())return details(existing.get(0));
        var holder=new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(c->{var s=c.prepareStatement("INSERT INTO return_requests(order_id,customer_id,kind,reason) VALUES(?,?,?,?)",new String[]{"id"});s.setLong(1,orderId);s.setLong(2,userId);s.setString(3,kind);s.setString(4,reason.strip());return s;},holder);
        return get(holder.getKey().longValue(),userId);
    }
    @Transactional public Map<String,Object> review(Long id,Long userId,String decision,String note) {
        if(!manager(userId))throw new ForbiddenException("Chỉ quản lý được duyệt yêu cầu.");
        var row=own(id,userId);orders.findLockedById(((Number)row.get("order_id")).longValue()).orElseThrow();
        row=own(id,userId);
        if(row.get("status").equals(decision))return details(row);
        if(!row.get("status").equals("PENDING"))throw new ConflictException("Yêu cầu đã được xử lý.");
        jdbc.update("UPDATE return_requests SET status=?,resolution_note=?,reviewed_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",decision,note.strip(),userId,id);
        return get(id,userId);
    }
    @Transactional public Map<String,Object> receive(Long id,Long userId) {
        if(!manager(userId))throw new ForbiddenException("Chỉ quản lý được xác nhận đã nhận lại hàng.");
        var row=own(id,userId);orders.findLockedById(((Number)row.get("order_id")).longValue()).orElseThrow();row=own(id,userId);
        if(row.get("status").equals("RECEIVED"))return details(row);
        if(!row.get("status").equals("APPROVED"))throw new ConflictException("Yêu cầu chưa được duyệt.");
        fulfillment.returnOrder(((Number)row.get("order_id")).longValue(),userId);
        jdbc.update("UPDATE return_requests SET status='RECEIVED',updated_at=CURRENT_TIMESTAMP WHERE id=?",id);
        return get(id,userId);
    }
    @Transactional public Map<String,Object> addImage(Long id,Long userId,MultipartFile file) throws java.io.IOException {
        var row=own(id,userId);
        if(((Number)row.get("customer_id")).longValue()!=userId)throw new ForbiddenException("Chỉ người gửi yêu cầu được thêm ảnh.");
        orders.findLockedById(((Number)row.get("order_id")).longValue()).orElseThrow();row=own(id,userId);
        if(!row.get("status").equals("PENDING"))throw new ConflictException("Chỉ bổ sung ảnh khi yêu cầu chờ duyệt.");
        if(file.isEmpty()||file.getSize()>2*1024*1024)throw new BadRequestException("Ảnh tối đa 2 MB.");
        int count=jdbc.queryForObject("SELECT COUNT(*) FROM return_request_images WHERE request_id=?",Integer.class,id);
        if(count>=5)throw new BadRequestException("Tối đa 5 ảnh mỗi yêu cầu.");
        byte[] data=file.getBytes();
        String type=data.length>=8&&data[0]==(byte)137&&data[1]==80&&data[2]==78&&data[3]==71?"png":data.length>=3&&data[0]==(byte)255&&data[1]==(byte)216&&data[2]==(byte)255?"jpeg":null;
        if(type==null)throw new BadRequestException("Chỉ chấp nhận ảnh PNG hoặc JPEG.");
        try(var stream=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(data))) {
            var readers=javax.imageio.ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new BadRequestException("Ảnh không hợp lệ.");
            var reader=readers.next();try {reader.setInput(stream);if((long)reader.getWidth(0)*reader.getHeight(0)>20_000_000)throw new BadRequestException("Ảnh quá lớn.");
                var image=reader.read(0);var output=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,type,output);data=output.toByteArray();
                if(data.length>2*1024*1024)throw new BadRequestException("Ảnh sau xử lý vượt 2 MB.");
            }finally{reader.dispose();}
        }catch(javax.imageio.IIOException e){throw new BadRequestException("Ảnh không hợp lệ.");}
        jdbc.update("INSERT INTO return_request_images(request_id,mime_type,image_data) VALUES(?,?,?)",id,"image/"+type,data);
        return get(id,userId);
    }
    @Transactional(readOnly=true) public Map<String,Object> image(Long requestId,Long imageId,Long userId) {
        own(requestId,userId);
        return jdbc.queryForList("SELECT mime_type,image_data FROM return_request_images WHERE id=? AND request_id=?",imageId,requestId).stream().findFirst().orElseThrow(()->new ResourceNotFoundException("Không tìm thấy ảnh."));
    }
}
