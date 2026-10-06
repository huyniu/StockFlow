package com.stockflow.review;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.user.domain.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReviewController {
    private final ReviewService service;
    public ReviewController(ReviewService service) { this.service = service; }
    public record Request(@JsonProperty("product_id") @NotNull @Positive Long productId,
                          @NotNull @Min(1) @Max(5) Integer rating,
                          @JsonProperty("service_rating") @NotNull @Min(1) @Max(5) Integer serviceRating,
                          @NotBlank @Size(max=2000) String comment) {}

    @PostMapping("/api/v1/orders/{id}/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewService.Review create(@PathVariable Long id, @AuthenticationPrincipal User user, @Valid @RequestBody Request request) {
        return service.create(id, request.productId(), user.getId(), request.rating(), request.serviceRating(), request.comment());
    }
    @GetMapping("/api/v1/orders/{id}/reviews")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<ReviewService.Review> forOrder(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return service.forOrder(id, user.getId());
    }
    @GetMapping("/api/v1/products/{id}/reviews")
    public ReviewService.ReviewPage forProduct(@PathVariable Long id, @RequestParam(defaultValue="0") int page) {
        return service.forProduct(id, page);
    }
}
