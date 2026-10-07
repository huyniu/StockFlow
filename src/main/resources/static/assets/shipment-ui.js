(() => {
    'use strict';
    window.StockFlowShipment = {
        mode(shipment) {
            if (!shipment) return 'MANUAL';
            // Legacy simulated codes are always treated as simulated, even without new API metadata.
            if (shipment.tracking_code?.startsWith('GHN_HAN_')) return 'SIMULATED';
            return shipment.carrier_mode || 'MANUAL';
        },
        describe(shipment) {
            return {
                SIMULATED: 'Vận đơn mô phỏng · Không có shipper đến lấy hàng.',
                GHN_SANDBOX: 'Vận đơn GHN thử nghiệm · Không có shipper đến lấy hàng.',
                GHN_PRODUCTION: 'Vận đơn GHN thật · Theo dõi việc lấy hàng trên GHN.',
                MANUAL: 'Vận đơn được cửa hàng cập nhật thủ công.',
            }[this.mode(shipment)] || 'Chưa xác định loại vận đơn; vui lòng liên hệ cửa hàng.';
        },
        trackingUrl(shipment, status) {
            if (this.mode(shipment) !== 'GHN_PRODUCTION' || !['SHIPPED', 'DELIVERED'].includes(status)) return null;
            return 'https://donhang.ghn.vn/?order_code=' + encodeURIComponent(shipment.tracking_code);
        },
    };
})();
