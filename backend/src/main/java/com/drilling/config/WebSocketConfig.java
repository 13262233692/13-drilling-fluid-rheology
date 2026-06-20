package com.drilling.config;

import com.drilling.service.WebSocketConnectionManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ThreadPoolTaskExecutor wsChannelExecutor;
    private final TaskScheduler wsPushScheduler;
    private final WebSocketConnectionManager connectionManager;

    public WebSocketConfig(
            @Qualifier("wsChannelExecutor") ThreadPoolTaskExecutor wsChannelExecutor,
            @Qualifier("wsPushScheduler") TaskScheduler wsPushScheduler,
            WebSocketConnectionManager connectionManager) {
        this.wsChannelExecutor = wsChannelExecutor;
        this.wsPushScheduler = wsPushScheduler;
        this.connectionManager = connectionManager;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(wsChannelExecutor);
        registration.interceptors(activityTrackingInterceptor());
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(wsChannelExecutor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic")
                .setTaskScheduler(wsPushScheduler)
                .setHeartbeatValue(new long[]{10000, 10000});
        config.setPreservePublishOrder(true);
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/rheology")
                .setAllowedOriginPatterns("*")
                .withSockJS()
                .setStreamBytesLimit(131072)
                .setHttpMessageCacheSize(1000)
                .setSessionCookieNeeded(false);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setSendTimeLimit(10000)
                .setSendBufferSizeLimit(512 * 1024)
                .setMessageSizeLimit(64 * 1024);
    }

    @Bean
    public ChannelInterceptor activityTrackingInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
                if (sessionId != null) {
                    connectionManager.touch(sessionId);
                }
                return message;
            }
        };
    }
}
