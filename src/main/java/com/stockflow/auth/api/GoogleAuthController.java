package com.stockflow.auth.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockflow.auth.dto.AuthResponse;
import com.stockflow.auth.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth/google")
public class GoogleAuthController {
    private static final String COOKIE="stockflow_google_nonce";
    private final GoogleTokenVerifier verifier;
    private final GoogleLoginNonce nonces;
    private final GoogleAuthService service;
    public GoogleAuthController(GoogleTokenVerifier verifier,GoogleLoginNonce nonces,GoogleAuthService service) {
        this.verifier=verifier; this.nonces=nonces; this.service=service;
    }
    public record Configuration(boolean enabled,@JsonProperty("client_id") String clientId,String nonce) {}
    public record Login(@NotBlank @Size(max=12000) String credential) {}
    private ResponseCookie cookie(String nonce,long age,HttpServletRequest request) {
        return ResponseCookie.from(COOKIE,nonce).httpOnly(true).secure(request.isSecure()).sameSite("Strict")
            .path("/api/v1/auth/google").maxAge(age).build();
    }
    @GetMapping("/config")
    public ResponseEntity<Configuration> config(HttpServletRequest request) {
        if (!verifier.enabled()) return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Configuration(false,"",null));
        String nonce=nonces.issue();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.SET_COOKIE,cookie(nonce,300,request).toString())
            .body(new Configuration(true,verifier.clientId(),nonce));
    }
    @PostMapping
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody Login body,
            @CookieValue(name=COOKIE,required=false) String nonce,HttpServletRequest request) {
        AuthResponse result=service.login(body.credential(),nonce);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.SET_COOKIE,cookie("",0,request).toString()).body(result);
    }
}
