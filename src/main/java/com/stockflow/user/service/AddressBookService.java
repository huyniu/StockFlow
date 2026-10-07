package com.stockflow.user.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.common.exception.*;
import com.stockflow.shipping.client.GhnClient;
import com.stockflow.user.domain.*;
import com.stockflow.user.repository.UserRepository;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressBookService {
    public record Request(@NotBlank @Size(max=80) String label,
        @NotBlank @Size(max=150) @JsonProperty("recipient_name") String recipientName,
        @NotBlank @Pattern(regexp="\\+?[0-9]{8,15}") @JsonProperty("recipient_phone") String recipientPhone,
        @NotNull @Positive @JsonProperty("province_id") Integer provinceId,
        @NotNull @Positive @JsonProperty("district_id") Integer districtId,
        @NotBlank @Pattern(regexp="[0-9]{1,20}") @JsonProperty("ward_code") String wardCode,
        @NotBlank @Size(max=300) @JsonProperty("street_address") String streetAddress,
        @JsonProperty("is_default") boolean isDefault) {}
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final GhnClient ghn;
    public AddressBookService(UserRepository users, JdbcTemplate jdbc, GhnClient ghn) { this.users=users; this.jdbc=jdbc; this.ghn=ghn; }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> list(Long userId) {
        return jdbc.queryForList("SELECT * FROM customer_addresses WHERE user_id=? ORDER BY is_default DESC,id",userId)
                .stream().map(this::safe).toList();
    }
    private Map<String,Object> safe(Map<String,Object> row) {
        var result=new LinkedHashMap<>(row); result.remove("user_id"); return result;
    }
    private User lock(Long id) {
        var user=users.findLockedById(id).orElseThrow(()->new UnauthorizedException("Vui lòng đăng nhập."));
        if(user.getStatus()!=UserStatus.ACTIVE) throw new UnauthorizedException("Tài khoản không hoạt động.");
        return user;
    }
    private Map<String,Object> own(Long userId,Long id) {
        return jdbc.queryForList("SELECT * FROM customer_addresses WHERE id=? AND user_id=?",id,userId).stream().findFirst()
            .orElseThrow(()->new ResourceNotFoundException("Không tìm thấy địa chỉ."));
    }
    @Transactional
    public Map<String,Object> save(Long userId,Long id,Request input) {
        lock(userId);
        if(id!=null) own(userId,id);
        int count=jdbc.queryForObject("SELECT COUNT(*) FROM customer_addresses WHERE user_id=?",Integer.class,userId);
        if(id==null && count>=20) throw new BadRequestException("Bạn có thể lưu tối đa 20 địa chỉ.");
        var province=ghn.getProvinces().stream().filter(p->p.id()==input.provinceId()).findFirst().orElseThrow(()->new BadRequestException("Tỉnh/thành không hợp lệ."));
        var district=ghn.getDistricts(input.provinceId()).stream().filter(d->d.id()==input.districtId()).findFirst().orElseThrow(()->new BadRequestException("Quận/huyện không hợp lệ."));
        var ward=ghn.getWards(input.districtId()).stream().filter(w->w.code().equals(input.wardCode())).findFirst().orElseThrow(()->new BadRequestException("Phường/xã không hợp lệ."));
        if(id==null) {
            var holder=new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.update(connection->{var statement=connection.prepareStatement("INSERT INTO customer_addresses(user_id,label,recipient_name,recipient_phone,province_id,province_name,district_id,district_name,ward_code,ward_name,street_address) VALUES(?,?,?,?,?,?,?,?,?,?,?)",new String[]{"id"});
                Object[] values={userId,input.label().strip(),input.recipientName().strip(),input.recipientPhone(),province.id(),province.name(),district.id(),district.name(),ward.code(),ward.name(),input.streetAddress().strip()};
                for(int i=0;i<values.length;i++) statement.setObject(i+1,values[i]); return statement;},holder);
            id=holder.getKey().longValue();
        } else jdbc.update("UPDATE customer_addresses SET label=?,recipient_name=?,recipient_phone=?,province_id=?,province_name=?,district_id=?,district_name=?,ward_code=?,ward_name=?,street_address=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",
                input.label().strip(),input.recipientName().strip(),input.recipientPhone(),province.id(),province.name(),district.id(),district.name(),ward.code(),ward.name(),input.streetAddress().strip(),id,userId);
        var row=own(userId,id);
        if(input.isDefault() || count==0 || Boolean.TRUE.equals(row.get("is_default"))) makeDefault(userId,id);
        return safe(own(userId,id));
    }
    @Transactional
    public Map<String,Object> makeDefault(Long userId,Long id) {
        lock(userId); var row=own(userId,id);
        jdbc.update("UPDATE customer_addresses SET is_default=FALSE WHERE user_id=?",userId);
        jdbc.update("UPDATE customer_addresses SET is_default=TRUE,updated_at=CURRENT_TIMESTAMP WHERE id=?",id);
        copyToProfile(userId,row); return safe(own(userId,id));
    }
    @Transactional
    public void delete(Long userId,Long id) {
        lock(userId); var row=own(userId,id); jdbc.update("DELETE FROM customer_addresses WHERE id=? AND user_id=?",id,userId);
        if(Boolean.TRUE.equals(row.get("is_default"))) {
            var remaining=jdbc.queryForList("SELECT id FROM customer_addresses WHERE user_id=? ORDER BY id LIMIT 1",Long.class,userId);
            if(remaining.isEmpty()) users.updateDefaultAddress(userId,null,null,null,null,null,null,null);
            else makeDefault(userId,remaining.get(0));
        }
    }
    private void copyToProfile(Long userId,Map<String,Object> row) {
        users.updateDefaultAddress(userId,((Number)row.get("province_id")).intValue(),(String)row.get("province_name"),
                ((Number)row.get("district_id")).intValue(),(String)row.get("district_name"),(String)row.get("ward_code"),(String)row.get("ward_name"),(String)row.get("street_address"));
    }
    /** Keep the pre-existing profile API usable without silently maintaining two different defaults. */
    @Transactional
    public void synchronizeLegacy(Long userId,DefaultAddress address) {
        var user=lock(userId);
        var defaults=jdbc.queryForList("SELECT id FROM customer_addresses WHERE user_id=? AND is_default=TRUE",Long.class,userId);
        if(address==null) {jdbc.update("UPDATE customer_addresses SET is_default=FALSE WHERE user_id=?",userId);return;}
        if(defaults.isEmpty() && jdbc.queryForObject("SELECT COUNT(*) FROM customer_addresses WHERE user_id=?",Integer.class,userId)>=20)
            throw new BadRequestException("Sổ địa chỉ đã đủ 20 địa chỉ. Hãy chọn một địa chỉ đã lưu làm mặc định.");
        if(defaults.isEmpty()) jdbc.update("INSERT INTO customer_addresses(user_id,label,recipient_name,recipient_phone,province_id,province_name,district_id,district_name,ward_code,ward_name,street_address,is_default) VALUES(?,?,?,?,?,?,?,?,?,?,?,TRUE)",
                userId,"Địa chỉ mặc định",user.getFullName(),user.getPhone(),address.provinceId,address.provinceName,address.districtId,address.districtName,address.wardCode,address.wardName,address.streetAddress);
        else jdbc.update("UPDATE customer_addresses SET province_id=?,province_name=?,district_id=?,district_name=?,ward_code=?,ward_name=?,street_address=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                address.provinceId,address.provinceName,address.districtId,address.districtName,address.wardCode,address.wardName,address.streetAddress,defaults.get(0));
    }
}
