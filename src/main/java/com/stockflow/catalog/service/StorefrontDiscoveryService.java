package com.stockflow.catalog.service;

import com.stockflow.catalog.domain.Product;
import com.stockflow.catalog.dto.ProductResponse;
import com.stockflow.catalog.repository.ProductRepository;
import com.stockflow.catalog.repository.StorefrontDiscoveryRepository;
import com.stockflow.common.exception.BadRequestException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Trả sản phẩm công khai theo thứ hạng bán chạy, dùng giá và cấu hình catalog hiện tại. */
@Service
public class StorefrontDiscoveryService {

    private final StorefrontDiscoveryRepository discovery;
    private final ProductRepository products;

    /** Nhận truy vấn xếp hạng và repository catalog; không gọi báo cáo quản trị để phục vụ khách. */
    public StorefrontDiscoveryService(StorefrontDiscoveryRepository discovery, ProductRepository products) {
        this.discovery = discovery;
        this.products = products;
    }

    /** Giới hạn 1–12 model để payload/bộ ảnh nhỏ, thứ hạng bằng nhau dùng ID làm thứ tự ổn định. */
    @Transactional(readOnly = true)
    public List<ProductResponse> bestsellers(int limit) {
        if (limit < 1 || limit > 12) {
            throw new BadRequestException("Số sản phẩm bán chạy phải từ 1 đến 12.");
        }
        var ids = discovery.bestsellerIds(limit);
        var byId = products.findAllById(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return ids.stream().filter(byId::containsKey).map(id -> ProductResponse.from(byId.get(id))).toList();
    }
}
