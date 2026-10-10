package com.stockflow.auth.security;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {
    @Test void ignoresAllClientHeadersWithoutTrustedProxy() {
        var request=request("203.0.113.4","198.51.100.5");
        request.addHeader("Forwarded","for=198.51.100.6");request.addHeader("X-Real-IP","198.51.100.7");
        assertThat(new ClientIpResolver("","none").resolve(request)).isEqualTo("203.0.113.4");
    }
    @Test void walksTrustedChainFromRightToLeftAndIgnoresForgedPrefix() {
        assertThat(new ClientIpResolver("10.0.0.0/24,10.0.1.0/24","none")
                .resolve(request("10.0.0.2","198.51.100.99, 203.0.113.4, 10.0.1.2"))).isEqualTo("203.0.113.4");
    }
    @Test void untrustedSocketCannotActivateAllowlistWithHeader() {
        assertThat(new ClientIpResolver("10.0.0.0/24","none")
                .resolve(request("203.0.113.4","198.51.100.99, 10.0.0.2"))).isEqualTo("203.0.113.4");
    }
    @Test void malformedForwardingFallsBackToSocketWithoutDnsLookup() {
        var resolver=new ClientIpResolver("10.0.0.0/24","none");
        for(String header:new String[]{"attacker.example","203.0.113.4,","999.0.0.1","203.0.113.4:1234","x".repeat(1025)})
            assertThat(resolver.resolve(request("10.0.0.2",header))).isEqualTo("10.0.0.2");
    }
    @Test void supportsNumericIpv6PeersAndTrustedProxies() {
        var resolver=new ClientIpResolver("fd00::/64","none");
        assertThat(resolver.resolve(request("fd00::2","2001:db8::4"))).isEqualTo("2001:db8:0:0:0:0:0:4");
    }
    @Test void refusesWildcardTrustAndAutomaticForwardedRewriting() {
        assertThatIllegalArgumentException().isThrownBy(()->new ClientIpResolver("0.0.0.0/0","none"));
        assertThatIllegalArgumentException().isThrownBy(()->new ClientIpResolver("::/0","none"));
        assertThatIllegalArgumentException().isThrownBy(()->new ClientIpResolver("proxy.example","none"));
        assertThatIllegalArgumentException().isThrownBy(()->new ClientIpResolver("","framework"));
    }
    private MockHttpServletRequest request(String peer,String chain){
        var request=new MockHttpServletRequest();request.setRemoteAddr(peer);request.addHeader("X-Forwarded-For",chain);return request;
    }
}
