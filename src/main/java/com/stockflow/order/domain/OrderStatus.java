package com.stockflow.order.domain;
/** Các trạng thái vòng đời đơn hàng. */
public enum OrderStatus { PENDING, CONFIRMED, PACKED, SHIPPED, DELIVERED, CANCELLED, EXPIRED, RETURNED }
