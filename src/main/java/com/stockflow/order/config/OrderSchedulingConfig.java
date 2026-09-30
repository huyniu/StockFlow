package com.stockflow.order.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
/** Bật tác vụ định kỳ để tự động nhả hàng của đơn hết hạn. */
@Configuration @EnableScheduling
public class OrderSchedulingConfig {}
