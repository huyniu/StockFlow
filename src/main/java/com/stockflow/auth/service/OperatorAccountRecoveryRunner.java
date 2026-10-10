package com.stockflow.auth.service;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Disabled by default; explicit owner configuration only, after demo seeding. */
@Component
@Order(10)
@ConditionalOnProperty(name = "app.operator-recovery.enabled", havingValue = "true")
public class OperatorAccountRecoveryRunner implements ApplicationRunner {
    private static final Logger LOG = LoggerFactory.getLogger(OperatorAccountRecoveryRunner.class);
    private final OperatorAccountRecoveryService recovery;
    private final Environment environment;
    public OperatorAccountRecoveryRunner(OperatorAccountRecoveryService recovery, Environment environment) {
        this.recovery = recovery; this.environment = environment;
    }
    @Override public void run(ApplicationArguments arguments) {
        boolean changed = recovery.recover(environment.getProperty("OPERATOR_RECOVERY_REQUEST_ID"), Map.of(
                "admin@stockflow.com", environment.getProperty("OPERATOR_RECOVERY_ADMIN_PASSWORD", ""),
                "manager@stockflow.com", environment.getProperty("OPERATOR_RECOVERY_MANAGER_PASSWORD", ""),
                "staff.hn@stockflow.com", environment.getProperty("OPERATOR_RECOVERY_STAFF_PASSWORD", "")));
        LOG.info("[OPERATOR_RECOVERY] {}", changed ? "Three existing operators recovered; old tokens invalidated."
                : "Request already completed; accounts unchanged.");
    }
}
