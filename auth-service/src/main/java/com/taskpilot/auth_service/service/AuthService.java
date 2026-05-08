package com.taskpilot.auth_service.service;

import com.taskpilot.auth_service.dto.request.LoginRequest;
import com.taskpilot.auth_service.dto.request.RegisterRequest;
import com.taskpilot.auth_service.dto.response.AuthResponse;
import com.taskpilot.auth_service.entity.RefreshToken;
import com.taskpilot.auth_service.entity.User;
import com.taskpilot.auth_service.exception.AuthException;
import com.taskpilot.auth_service.repository.RefreshTokenRepository;
import com.taskpilot.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AuthException("Email already registered", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .role(User.Role.USER)
                .enabled(true)
                .build();

        userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(
                        request.getEmail().toLowerCase().trim())
                .orElseThrow(() ->
                        new AuthException("Invalid credentials", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // Same message for wrong email OR wrong password
            // Never reveal which one failed — prevents user enumeration
            throw new AuthException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }

        if (!user.isEnabled()) {
            throw new AuthException("Account is disabled", HttpStatus.FORBIDDEN);
        }

        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String tokenHash = DigestUtils.sha256Hex(rawRefreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() ->
                        new AuthException("Invalid refresh token", HttpStatus.UNAUTHORIZED));

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(stored);
            throw new AuthException("Refresh token expired", HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() ->
                        new AuthException("User not found", HttpStatus.NOT_FOUND));

        // Rotate: always delete old token and issue a new one
        refreshTokenRepository.delete(stored);
        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String tokenHash = DigestUtils.sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(refreshTokenRepository::delete);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateRefreshToken();

        RefreshToken tokenEntity = RefreshToken.builder()
                .userId(user.getId())
                .tokenHash(DigestUtils.sha256Hex(rawRefreshToken))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        refreshTokenRepository.save(tokenEntity);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .userId(user.getId().toString())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }
}