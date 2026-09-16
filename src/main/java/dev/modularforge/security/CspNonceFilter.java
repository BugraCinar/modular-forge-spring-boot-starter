package dev.modularforge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/** Binds each server-rendered page's inline code to this response only. */
public class CspNonceFilter extends OncePerRequestFilter {
    private final SecureRandom random = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String nonce = Base64.getEncoder().encodeToString(bytes);
        request.setAttribute("cspNonce", nonce);
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self' 'nonce-" + nonce
                + "'; style-src 'self' 'nonce-" + nonce
                + "'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
        chain.doFilter(request, response);
    }
}
