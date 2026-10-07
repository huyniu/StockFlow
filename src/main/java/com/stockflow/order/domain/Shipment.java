package com.stockflow.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Vận đơn duy nhất của một đơn hàng. Service khóa order và kiểm tra quyền/chuyển trạng thái trước khi cập nhật.
 * shipped_at được giữ nguyên sau giao/hoàn để nhận biết hàng đã từng rời kho.
 */
@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "tracking_code", nullable = false, unique = true, length = 100)
    private String trackingCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ShipmentStatus status;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "carrier_mode", nullable = false, length = 30)
    private String carrierMode = "MANUAL";

    public String getCarrierMode() { return carrierMode; }
    public void setCarrierMode(String carrierMode) { this.carrierMode = carrierMode; }

    /** Hàm khởi tạo cho JPA. */
    protected Shipment() {
    }

    /** Cấp mã ngay lúc đóng gói để đáp ứng tracking_code NOT NULL của schema hiện có. */
    public Shipment(Long orderId, String trackingCode) {
        this.orderId = orderId;
        this.trackingCode = trackingCode;
        this.status = ShipmentStatus.PREPARING;
    }

    /** Ghi nhận xuất giao; service đã kiểm tra PACKED và vận đơn PREPARING. */
    public void ship(String trackingCode, Instant now) {
        this.trackingCode = trackingCode;
        this.status = ShipmentStatus.SHIPPED;
        this.shippedAt = now;
    }

    /** Ghi nhận giao thành công, giữ thời điểm xuất giao để theo dõi toàn bộ hành trình. */
    public void deliver(Instant now) {
        this.status = ShipmentStatus.DELIVERED;
        this.deliveredAt = now;
    }

    /** Đánh dấu hàng đã được nhận trả sau giao; việc hoàn kho/hoàn tiền nằm trong cùng transaction ở service. */
    public void receiveReturn() {
        this.status = ShipmentStatus.RETURNED;
    }

    /** Chặn hủy khi vận đơn hoặc thời điểm xuất giao cho thấy hàng đã rời kho. */
    public boolean hasShipped() {
        return status != ShipmentStatus.PREPARING || shippedAt != null;
    }

    /** Lấy mã đơn hàng để ghép vận đơn theo lô cho lịch sử khách hàng. */
    public Long getOrderId() {
        return orderId;
    }

    /** Lấy mã vận đơn đã cấp hoặc được chọn khi xuất giao. */
    public String getTrackingCode() {
        return trackingCode;
    }

    /** Lấy trạng thái vận chuyển. */
    public ShipmentStatus getStatus() {
        return status;
    }

    /** Lấy thời điểm hàng rời kho. */
    public Instant getShippedAt() {
        return shippedAt;
    }

    /** Lấy thời điểm giao thành công. */
    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}
