package com.stockflow.payment.repository;

import com.stockflow.payment.domain.PaymentTransaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findByTxnRef(String txnRef);

    @Query("select t.orderId from PaymentTransaction t where t.txnRef = :ref")
    Optional<Long> findOrderIdByTxnRef(@Param("ref") String txnRef);
}
