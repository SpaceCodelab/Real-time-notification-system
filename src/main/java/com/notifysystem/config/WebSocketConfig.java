package com.notifysystem.config;

import com.notifysystem.security.WebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket Configuration
 *
 * Architecture:
 *  - Endpoint  : /ws  (SockJS fallback enabled)
 *  - Topics    : /topic/broadcast  → all subscribers
 *  - Queue     : /user/{name}/queue/notifications → per-user private channel
 *  - App prefix: /app  → inbound messages routed to @MessageMapping methods
 *
 * Message flow:
 *  Client → /app/notify → WebSocketController → NotificationService
 *         → SimpMessagingTemplate → /user/{name}/queue/notifications → Client
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // In-memory broker handling /topic and /queue destinations
        // For production scale: replace with RabbitMQ/Kafka broker relay
        config.enableSimpleBroker("/topic", "/queue");

        // Prefix for messages from client → server (@MessageMapping handlers)
        config.setApplicationDestinationPrefixes("/app");

        // Prefix for user-specific destinations
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
            .addEndpoint("/ws")
            .setAllowedOriginPatterns("*")   // Restrict in production
            .withSockJS();                    // SockJS fallback for older browsers
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // JWT authentication interceptor for WebSocket CONNECT frames
        registration.interceptors(webSocketAuthInterceptor);
    }
}
