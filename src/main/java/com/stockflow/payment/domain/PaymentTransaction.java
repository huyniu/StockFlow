package com.stockflow.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {
    public enum Status { PENDING, SUCCESS, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id", nullable = false)
    private Long orderId;
    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;
    @Column(name = "txn_ref", nullable = false, unique = true, length = 100)
    private String txnRef;
    @Column(name = "transaction_code", length = 100)
    private String transactionCode;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Status status;
    @Column(name = "response_code", length = 10)
    private String responseCode;
    @Column(name = "bank_code", length = 20)
    private String bankCode;
    @Column(name = "pay_date")
    private Instant payDate;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentTransaction() {}

    public PaymentTransaction(Long orderId, String txnRef, BigDecimal amount, Instant now) {
        this.orderId = orderId;
        this.txnRef = txnRef;
        this.amount = amount;
        this.createdAt = now;
        this.paymentMethod = "VNPAY";
        this.status = Status.PENDING;
    }

    public Long getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public String getTransactionCode() { return transactionCode; }

    public void complete(boolean success, String responseCode, String transactionCode, String bankCode, Instant payDate) {
        this.status = success ? Status.SUCCESS : Status.FAILED;
        this.responseCode = responseCode;
        this.transactionCode = transactionCode;
        this.bankCode = bankCode;
        this.payDate = payDate;
    }
}
