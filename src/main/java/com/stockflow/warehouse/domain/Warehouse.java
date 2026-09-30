package com.stockflow.warehouse.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Entity ánh xạ bảng warehouses, đại diện cho kho hàng vật lý trong hệ thống StockFlow.
 */
@Entity
@Table(name = "warehouses")
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WarehouseStatus status;

    /**
     * Constructor mặc định cho JPA.
     */
    protected Warehouse() {
    }

    /**
     * Tạo kho mới từ dữ liệu đã được validate.
     */
    public Warehouse(String code, String name, String address, WarehouseStatus status) {
        this.code = code;
        this.name = name;
        this.address = address;
        this.status = status;
    }

    /**
     * Đặt trạng thái mặc định ACTIVE nếu caller không truyền trạng thái.
     */
    @PrePersist
    public void beforeCreate() {
        if (this.status == null) {
            this.status = WarehouseStatus.ACTIVE;
        }
    }

    /**
     * Lấy id kho.
     */
    public Long getId() {
        return id;
    }

    /**
     * Lấy mã kho duy nhất.
     */
    public String getCode() {
        return code;
    }

    /**
     * Lấy tên kho.
     */
    public String getName() {
        return name;
    }

    /**
     * Lấy địa chỉ kho.
     */
    public String getAddress() {
        return address;
    }

    /**
     * Lấy trạng thái kho.
     */
    public WarehouseStatus getStatus() {
        return status;
    }
}
