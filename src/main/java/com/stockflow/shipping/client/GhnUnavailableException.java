package com.stockflow.shipping.client;

import com.stockflow.common.exception.AppException;
import org.springframework.http.HttpStatus;

public class GhnUnavailableException extends AppException {
    public GhnUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE,
                "Chưa kết nối được GHN. Không tạo vận đơn hoặc dùng phí giả. Vui lòng kiểm tra lại với GHN trước khi thử lại.");
    }
}
