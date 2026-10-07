package com.stockflow.auth.service;

import static org.assertj.core.api.Assertions.*;
import com.stockflow.common.exception.UnauthorizedException;
import io.jsonwebtoken.Jwts;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class GoogleTokenVerifierTest {
    static KeyPair key;
    GoogleTokenVerifier verifier;
    Map<String,Object> claims;
    @BeforeAll static void key() throws Exception { var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); key=generator.generateKeyPair(); }
    @BeforeEach void setup() {
        verifier=new GoogleTokenVerifier("test.apps.googleusercontent.com",NimbusJwtDecoder.withPublicKey((RSAPublicKey)key.getPublic()).build());
        claims=new HashMap<>(Map.of("iss","https://accounts.google.com","aud",List.of("test.apps.googleusercontent.com"),"sub","google-subject","email","test@gmail.com","email_verified",true,"nonce","browser-nonce"));
    }
    String token(PrivateKey signingKey,Instant expiry) {
        return Jwts.builder().claims(claims).issuedAt(Date.from(Instant.now())).expiration(Date.from(expiry)).signWith(signingKey,Jwts.SIG.RS256).compact();
    }
    String token() { return token(key.getPrivate(),Instant.now().plusSeconds(600)); }
    @Test void verifiesActualRsaSignatureAndGoogleClaims() { assertThat(verifier.verify(token(),"browser-nonce").getSubject()).isEqualTo("google-subject"); }
    @Test void rejectsForgedSignature() throws Exception { var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); assertThatThrownBy(()->verifier.verify(token(generator.generateKeyPair().getPrivate(),Instant.now().plusSeconds(600)),"browser-nonce")).isInstanceOf(UnauthorizedException.class); }
    @Test void rejectsExpiredToken() { assertThatThrownBy(()->verifier.verify(token(key.getPrivate(),Instant.now().minusSeconds(120)),"browser-nonce")).isInstanceOf(UnauthorizedException.class); }
    @Test void rejectsWrongIssuer() { claims.put("iss","https://attacker.example"); assertThatThrownBy(()->verifier.verify(token(),"browser-nonce")).isInstanceOf(UnauthorizedException.class); }
    @Test void rejectsAnotherApplicationAudience() { claims.put("aud",List.of("other.apps.googleusercontent.com")); assertThatThrownBy(()->verifier.verify(token(),"browser-nonce")).isInstanceOf(UnauthorizedException.class); }
    @Test void rejectsUnverifiedEmail() { claims.put("email_verified",false); assertThatThrownBy(()->verifier.verify(token(),"browser-nonce")).isInstanceOf(UnauthorizedException.class); }
    @Test void rejectsMissingOrWrongBrowserNonce() {
        assertThatThrownBy(()->verifier.verify(token(),null)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(()->verifier.verify(token(),"other-browser")).isInstanceOf(UnauthorizedException.class);
    }
    @Test void rejectsWrongAuthorizedParty() { claims.put("azp","other.apps.googleusercontent.com"); assertThatThrownBy(()->verifier.verify(token(),"browser-nonce")).isInstanceOf(UnauthorizedException.class); }
    @Test void acceptsLegacyGoogleIssuer() { claims.put("iss","accounts.google.com"); assertThat(verifier.verify(token(),"browser-nonce").getSubject()).isEqualTo("google-subject"); }
}
