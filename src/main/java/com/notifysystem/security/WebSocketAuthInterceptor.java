package com.notifysystem.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * WebSocket Authentication Interceptor
 *
 * Standard HTTP security filters do NOT apply to WebSocket upgrade requests.
 * This interceptor fills the gap by authenticating STOMP CONNECT frames
 * using the JWT token passed in the "Authorization" STOMP header.
 *
 * After authentication, the Principal is set on the session.
 * Spring then uses it to route messages to /user/{username}/queue/...
 *
 * Client must send:
 *   stompClient.connectHeaders = { Authorization: "Bearer <token>" }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        // Only process CONNECT frames (authentication handshake)
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    String username   = jwtUtil.extractUsername(token);
                    UserDetails ud    = userDetailsService.loadUserByUsername(username);

                    if (jwtUtil.validateToken(token, ud)) {
                        UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
                        accessor.setUser(auth);
                        log.info("WebSocket CONNECT authenticated: user={}", username);
                    } else {
                        log.warn("WebSocket CONNECT rejected: invalid token for user={}", username);
                    }
                } catch (Exception e) {
                    log.warn("WebSocket CONNECT auth error: {}", e.getMessage());
                }
            } else {
                log.debug("WebSocket CONNECT without Authorization header (anonymous)");
            }
        }

        return message;
    }
}
