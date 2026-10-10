package com.stockflow.auth.security;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import org.junit.jupiter.api.Test;

class JwtSecretConfigurationTest {
    @Test void refusesPublishedDemoSigningKeyEvenWhenPadded() {
        assertThatIllegalArgumentException().isThrownBy(()->new JwtTokenProvider("stockflow-demo-secret-for-local-use-only-2026",3600000));
        assertThatIllegalArgumentException().isThrownBy(()->new JwtTokenProvider(" stockflow-demo-secret-for-local-use-only-2026 ",3600000));
    }
}
