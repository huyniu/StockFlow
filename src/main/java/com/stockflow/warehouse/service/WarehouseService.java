package com.stockflow.warehouse.service;

import com.stockflow.common.exception.ConflictException;
import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.warehouse.dto.CreateWarehouseRequest;
import com.stockflow.warehouse.dto.WarehouseResponse;
import com.stockflow.warehouse.repository.WarehouseRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service quản lý kho hàng, bao gồm xem danh sách và tạo kho mới.
 */
@Service
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    /**
     * Inject repository kho để service xử lý nghiệp vụ trước khi lưu dữ liệu.
     */
    public WarehouseService(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    /**
     * Lấy danh sách kho cho ADMIN, MANAGER và WAREHOUSE_STAFF.
     */
    @Transactional(readOnly = true)
    public List<WarehouseResponse> listWarehouses() {
        return warehouseRepository.findAll().stream()
                .map(WarehouseResponse::from)
                .toList();
    }

    /**
     * Tạo kho mới, kiểm tra code duy nhất để trả lỗi nghiệp vụ dễ hiểu.
     */
    @Transactional
    public WarehouseResponse createWarehouse(CreateWarehouseRequest request) {
        String code = request.code().trim().toUpperCase();
        if (warehouseRepository.existsByCode(code)) {
            throw new ConflictException("Mã kho đã tồn tại.");
        }

        Warehouse warehouse = warehouseRepository.save(new Warehouse(
                code,
                request.name().trim(),
                request.address().trim(),
                request.status()));
        return WarehouseResponse.from(warehouse);
    }
}
