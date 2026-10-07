package com.stockflow.user.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Địa chỉ hồ sơ; đơn hàng giữ bản chụp riêng. */
@Embeddable
public class DefaultAddress {
    @Column(name="default_province_id") @JsonProperty("province_id") public Integer provinceId;
    @Column(name="default_province_name", length=150) @JsonProperty("province_name") public String provinceName;
    @Column(name="default_district_id") @JsonProperty("district_id") public Integer districtId;
    @Column(name="default_district_name", length=150) @JsonProperty("district_name") public String districtName;
    @Column(name="default_ward_code", length=20) @JsonProperty("ward_code") public String wardCode;
    @Column(name="default_ward_name", length=150) @JsonProperty("ward_name") public String wardName;
    @Column(name="default_street_address", length=300) @JsonProperty("street_address") public String streetAddress;
    protected DefaultAddress() {}
    public DefaultAddress(Integer provinceId, String provinceName, Integer districtId, String districtName,
            String wardCode, String wardName, String streetAddress) {
        this.provinceId=provinceId; this.provinceName=provinceName; this.districtId=districtId;
        this.districtName=districtName; this.wardCode=wardCode; this.wardName=wardName; this.streetAddress=streetAddress;
    }
}
