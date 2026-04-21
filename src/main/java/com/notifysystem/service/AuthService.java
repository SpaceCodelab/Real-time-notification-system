package com.notifysystem.service;

import com.notifysystem.dto.AuthResponse;
import com.notifysystem.dto.LoginRequest;
import com.notifysystem.dto.RegisterRequest;
import com.notifysystem.model.User;
import com.notifysystem.repository.UserRepository;
import com.notifysystem.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Authentication Service
 *
 * Handles user registration and login.
 * Delegates credential verification to Spring's AuthenticationManager
 * so the full security chain (BCrypt, account status, etc.) is respected.
 *
 * On successful register:
 *  - Persists user to DB
 *  - Sends a welcome notification via WebSocket if user is connected
 *  - Returns JWT for immediate use
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository        userRepository;
    private final PasswordEncoder       passwordEncoder;
    private final JwtUtil               jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService    userDetailsService;
    private final NotificationService   notificationService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username '" + request.getUsername() + "' is already taken.");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email '" + request.getEmail() + "' is already registered.");
        }

        User user = User.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .role("ROLE_USER")
            .enabled(true)
            .createdAt(LocalDateTime.now())
            .build();

        userRepository.save(user);
        log.info("New user registered: username={}", user.getUsername());

        // Persist welcome notification (delivered via WS on next connect)
        notificationService.sendWelcomeNotification(user);

        String token = generateToken(user.getUsername());
        return buildResponse(user, token, "Registration successful");
    }

    public AuthResponse login(LoginRequest request) {
        // Delegates to DaoAuthenticationProvider → BCrypt check
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        log.info("User logged in: username={}", user.getUsername());

        String token = generateToken(user.getUsername());
        return buildResponse(user, token, "Login successful");
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private String generateToken(String username) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return jwtUtil.generateToken(userDetails);
    }

    private AuthResponse buildResponse(User user, String token, String message) {
        return AuthResponse.builder()
            .token(token)
            .username(user.getUsername())
            .email(user.getEmail())
            .role(user.getRole())
            .message(message)
            .build();
    }
}
