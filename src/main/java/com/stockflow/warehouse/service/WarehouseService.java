package com.stockflow.warehouse.service;

import com.stockflow.common.exception.ConflictException;
import com.stockflow.common.exception.ForbiddenException;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.user.domain.User;
import com.stockflow.warehouse.domain.Warehouse;
import com.stockflow.warehouse.domain.WarehouseStatus;
import com.stockflow.warehouse.dto.CreateWarehouseRequest;
import com.stockflow.warehouse.dto.UpdateWarehouseAddressRequest;
import com.stockflow.warehouse.dto.WarehouseOrderOptionResponse;
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

    /** Lựa chọn kho đang hoạt động phục vụ đặt hàng; không thay đổi quyền xem danh sách kho nội bộ. */
    @Transactional(readOnly = true)
    public List<WarehouseOrderOptionResponse> orderOptions() {
        return warehouseRepository.findByStatusOrderByIdAsc(WarehouseStatus.ACTIVE).stream()
                .map(WarehouseOrderOptionResponse::from)
                .toList();
    }

    /** Staff chỉ nhận kho được phân công; quản lý/admin nhận mọi kho, quyền được kiểm tra lại tại nghiệp vụ. */
    @Transactional(readOnly = true)
    public List<WarehouseOrderOptionResponse> operatingOptions(User actor) {
        String role = actor.getRole().getName();
        boolean unrestricted = role.equals("ADMIN") || role.equals("MANAGER");
        if (!unrestricted && !role.equals("WAREHOUSE_STAFF")) {
            throw new ForbiddenException("Bạn không có quyền xem lựa chọn kho vận hành.");
        }
        return warehouseRepository.findOperatingWarehouses(actor.getId(), unrestricted).stream()
                .map(WarehouseOrderOptionResponse::from)
                .toList();
    }

    /** ADMIN đổi địa chỉ kho nội bộ; địa chỉ này không bao giờ xuất hiện trên storefront. */
    @Transactional
    public WarehouseResponse updateAddress(Long id, UpdateWarehouseAddressRequest request) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy kho."));
        warehouse.changeAddress(request.address().trim());
        return WarehouseResponse.from(warehouse);
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
