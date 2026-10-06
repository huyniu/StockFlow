# GHN Sandbox fulfillment

Set `GHN_TOKEN`, `GHN_SHOP_ID` and optionally `GHN_SENDER_PHONE` in the application's environment (Compose forwards these variables). Default token `MOCK_TOKEN` never contacts GHN. Warehouse has no phone field yet: the configured sender phone is used for all warehouses; if omitted, GHN uses the shop profile.

`POST /api/v1/orders/{id}/ghn-ship` requires an assigned WAREHOUSE_STAFF or MANAGER/ADMIN. Only PACKED orders with a paid payment and preparing shipment can be submitted. JWT authentication and database warehouse assignments remain authoritative.

The carrier request uses the warehouse name/address, the order's immutable recipient details, `client_order_code=STOCKFLOW-{id}`, COD zero, seller-paid shipping and a default 500g / 15 x 15 x 10cm parcel. These dimensions are Sandbox defaults, not measured product dimensions. Comma-separated addresses are mapped as street, ward, district, province (old format), or street, ward, province (new format). Unstructured addresses may be rejected by GHN and trigger fallback; supply complete valid locality names for a real Sandbox order.

The endpoint calls GHN's `v2/shipping-order/create` via RestClient with Token, ShopId and JSON headers, 3s connect and 5s read timeouts. HTTP errors, malformed/business-error responses and network timeouts fall back to `GHN_HAN_{orderId}_{random4}`. A simulated code does **not** mean GHN accepted an actual shipment, and will not have a real tracking journey. The UI identifies simulated codes. Timeout fallback cannot cancel a request already accepted by GHN; check the stable client_order_code before any manual resubmission.

Tracking is persisted in the existing `shipments.tracking_code` and order becomes SHIPPED. An order lock and stable carrier client_order_code prevent repeated/concurrent endpoint calls from creating duplicate dispatches. V20 adds `shipping_dispatch_events`: one ORDER_DISPATCH audit record with tracking code, actor and timestamp per order. Existing inventory ledger DISPATCH remains the single inventory deduction recorded at payment confirmation; shipping never deducts stock again.

The dashboard retains manual ship and adds a GHN button for PACKED orders, then selects the SHIPPED queue. SHIPPED/DELIVERED details link to GHN tracking. No GHN webhook synchronization or cancellation integration is included.

## Checkout and shipping fees (V21)

Checkout loads provinces, districts and wards from the public `/api/v1/locations/provinces`, `/districts?province_id=...`, `/wards?district_id=...` endpoints. `POST /api/v1/locations/calculate-fee` accepts `warehouse_id`, `to_district_id`, `to_ward_code`, `weight`; it returns `shipping_fee` and `service_type_id=2`.

Set `GHN_FROM_DISTRICT_ID` to the actual shop district (default 1450). For multiple warehouses, configure `ghn.warehouse-districts` as a map of StockFlow warehouse IDs to their actual GHN district IDs. Without an override, each warehouse uses the configured default district. Confirm these IDs with GHN before using real fees.

The checkout parcel uses a fixed 500g weight and 15 x 15 x 10cm dimensions, not measured SKU dimensions. The backend checks the destination ward, recalculates the fee and rejects a client fee that differs from the quote. V21 persists destination codes and shipping fee; the total and VNPay amount include the fee. Existing requests without destination codes or fee keep zero shipping fee. Fulfillment passes the saved district and ward codes directly to GHN; legacy orders retain address parsing.

`MOCK_TOKEN` uses sample locations and a 30,000 VND fee without contacting GHN. GHN connection/API failures also use fallback data and fees. These sample location codes and fees are for independent demo/testing and do not establish an actual GHN service quote.

API reference: https://developer.ghn.vn/vi/docs/order/create

