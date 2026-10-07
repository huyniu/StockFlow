package com.stockflow.auth.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Reloadable public auth URLs reuse StockFlow's existing JWT/OTP/Google flows. */
@Controller
public class AuthPageController {
    @GetMapping({"/login", "/register"})
    public String authPage() { return "forward:/index.html"; }
}
