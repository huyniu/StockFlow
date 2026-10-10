package com.stockflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.stockflow.auth.service.EmailService;
import com.stockflow.common.mail.MailDeliveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;

@ExtendWith(OutputCaptureExtension.class)
class EmailServiceSecurityTest {
    @Test void otpIsSentOnlyInEmailAndNeverLoggedEvenOnDeliveryFailure(CapturedOutput output) {
        var delivery=mock(MailDeliveryService.class);when(delivery.isConfigured()).thenReturn(true);
        var service=new EmailService(delivery);
        service.sendVerificationOtp("private@security.test","918273");
        verify(delivery).send(eq("private@security.test"),anyString(),contains("918273"));
        doThrow(new MailSendException("private@security.test OTP 918273")).when(delivery).send(anyString(),anyString(),anyString());
        service.sendVerificationOtp("private@security.test","918273");
        when(delivery.isConfigured()).thenReturn(false);service.sendVerificationOtp("private@security.test","918273");
        assertThat(output.getAll()).doesNotContain("918273","private@security.test","EMAIL_OTP");
    }
}
