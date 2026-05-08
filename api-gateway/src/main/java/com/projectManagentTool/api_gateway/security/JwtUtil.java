package com.projectManagentTool.api_gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    public Claims validateAndExtract(String token) {
        return Jwts.parser()                    // ← parser() not parserBuilder()
                .verifyWith(getSigningKey())         // ← verifyWith() not setSigningKey()
                .build()
                .parseSignedClaims(token)           // ← parseSignedClaims() not parseClaimsJws()
                .getPayload();                      // ← getPayload() not getBody()
    }

    private SecretKey getSigningKey() {         // ← SecretKey not Key
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }
}