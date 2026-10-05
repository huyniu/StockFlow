package com.stockflow.common.exception;

public class EmailNotVerifiedException extends RuntimeException {
    private final String email;
    public EmailNotVerifiedException(String email) {
        super("Vui lòng xác thực email.");
        this.email = email;
    }
    public String getEmail() { return email; }
}
