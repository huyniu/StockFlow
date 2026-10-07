package com.stockflow.order.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.order.service.ReturnRequestService;
import com.stockflow.user.domain.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/returns")
public class ReturnRequestController {
    public record Create(@NotNull @Positive @JsonProperty("order_id") Long orderId,@NotBlank @Pattern(regexp="RETURN|EXCHANGE") String kind,@NotBlank @Size(min=10,max=2000) String reason) {}
    public record Review(@NotBlank @Pattern(regexp="APPROVED|REJECTED") String decision,@NotBlank @Size(max=2000) String note) {}
    private final ReturnRequestService service;
    public ReturnRequestController(ReturnRequestService service){this.service=service;}
    @GetMapping @PreAuthorize("hasAnyRole('CUSTOMER','MANAGER','ADMIN')") public List<Map<String,Object>> list(@AuthenticationPrincipal User user){return service.list(user.getId());}
    @GetMapping("/{id}") @PreAuthorize("hasAnyRole('CUSTOMER','MANAGER','ADMIN')") public Map<String,Object> get(@PathVariable Long id,@AuthenticationPrincipal User user){return service.get(id,user.getId());}
    @PostMapping @PreAuthorize("hasRole('CUSTOMER')") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> create(@AuthenticationPrincipal User user,@Valid @RequestBody Create body){return service.create(body.orderId(),user.getId(),body.kind(),body.reason());}
    @PostMapping("/{id}/review") @PreAuthorize("hasAnyRole('MANAGER','ADMIN')") public Map<String,Object> review(@PathVariable Long id,@AuthenticationPrincipal User user,@Valid @RequestBody Review body){return service.review(id,user.getId(),body.decision(),body.note());}
    @PostMapping("/{id}/receive") @PreAuthorize("hasAnyRole('MANAGER','ADMIN')") public Map<String,Object> receive(@PathVariable Long id,@AuthenticationPrincipal User user){return service.receive(id,user.getId());}
    @PostMapping(value="/{id}/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasRole('CUSTOMER')") public Map<String,Object> upload(@PathVariable Long id,@AuthenticationPrincipal User user,@RequestPart("file") MultipartFile file)throws java.io.IOException{return service.addImage(id,user.getId(),file);}
    @GetMapping("/{id}/images/{imageId}") @PreAuthorize("hasAnyRole('CUSTOMER','MANAGER','ADMIN')") public ResponseEntity<byte[]> image(@PathVariable Long id,@PathVariable Long imageId,@AuthenticationPrincipal User user){
        var row=service.image(id,imageId,user.getId());return ResponseEntity.ok().contentType(MediaType.parseMediaType((String)row.get("mime_type"))).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body((byte[])row.get("image_data"));
    }
}
