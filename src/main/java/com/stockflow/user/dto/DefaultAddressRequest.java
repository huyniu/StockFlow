package com.stockflow.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

public record DefaultAddressRequest(
        @NotNull @Positive @JsonProperty("province_id") Integer provinceId,
        @NotNull @Positive @JsonProperty("district_id") Integer districtId,
        @NotBlank @Size(max=20) @Pattern(regexp="[0-9]+") @JsonProperty("ward_code") String wardCode,
        @NotBlank @Size(max=300) @Pattern(regexp="[^\\p{Cntrl}]+") @JsonProperty("street_address") String streetAddress) {
    public DefaultAddressRequest {
        wardCode = wardCode == null ? null : wardCode.strip();
        streetAddress = streetAddress == null ? null : streetAddress.strip();
    }
}
