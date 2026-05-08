package com.projectManagentTool.api_gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private final JwtUtil jwtUtil;

    // Constructor Injection
    // Spring automatically injects JwtUtil bean here
    public JwtAuthFilter(JwtUtil jwtUtil) {
        super(Config.class);
        this.jwtUtil = jwtUtil;
    }

    @Override
    public GatewayFilter apply(Config config) {

        // This filter runs for every incoming request
        return (exchange, chain) -> {

            // Get incoming HTTP request
            ServerHttpRequest request = exchange.getRequest();

            // Extract Authorization header
            String authHeader = request.getHeaders()
                    .getFirst(HttpHeaders.AUTHORIZATION);

            // Validate header existence and Bearer format
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return onError(exchange, HttpStatus.UNAUTHORIZED);
            }

            // Remove "Bearer " prefix
            String token = authHeader.substring(7);

            try {

                // Validate JWT and extract claims
                Claims claims = jwtUtil.validateAndExtract(token);

                // Add authenticated user details to request headers
                // Downstream microservices can trust these headers
                ServerHttpRequest mutatedRequest = request.mutate()
                        .header("X-User-Id", claims.getSubject())
                        .header("X-User-Email", claims.get("email", String.class))
                        .header("X-User-Role", claims.get("role", String.class))
                        .build();

                // Forward modified request to downstream service
                return chain.filter(
                        exchange.mutate()
                                .request(mutatedRequest)
                                .build()
                );

            } catch (JwtException e) {

                // JWT invalid / expired / tampered
                return onError(exchange, HttpStatus.UNAUTHORIZED);
            }
        };
    }

    // Centralized error response handler
    private Mono<Void> onError(ServerWebExchange exchange, HttpStatusCode status) {

        exchange.getResponse().setStatusCode(status);

        return exchange.getResponse().setComplete();
    }

    // Required configuration class for Gateway Filter
    public static class Config {
    }
}