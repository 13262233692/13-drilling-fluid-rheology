package com.drilling.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class WebSocketConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConnectionManager.class);

    private static final long ZOMBIE_THRESHOLD_MS = 60_000L;
    private static final int MAX_SESSIONS = 200;
    private static final int EVICT_BATCH_SIZE = 20;
    private static final int THROTTLE_THRESHOLD = 100;
    private static final String RHEOLOGY_TOPIC = "/topic/rheology";

    private final ConcurrentHashMap<String, SessionMeta> sessions = new ConcurrentHashMap<>();

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            return;
        }
        String forwardedFor = accessor.getFirstNativeHeader("X-Forwarded-For");
        String realIp = accessor.getFirstNativeHeader("X-Real-IP");
        final String clientIp = (forwardedFor != null && !forwardedFor.isEmpty()) ? forwardedFor : realIp;
        final long now = System.currentTimeMillis();
        sessions.computeIfAbsent(sessionId, id -> new SessionMeta(id, now, clientIp));
        log.debug("Session connect registered: sessionId={}, ip={}", sessionId, clientIp);
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            return;
        }
        sessions.computeIfPresent(sessionId, (id, meta) -> {
            meta.lastActiveAt.set(System.currentTimeMillis());
            return meta;
        });
        log.debug("Session connected: sessionId={}", sessionId);
    }

    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String destination = accessor.getDestination();
        if (sessionId == null || destination == null) {
            return;
        }
        if (RHEOLOGY_TOPIC.equals(destination)) {
            sessions.computeIfPresent(sessionId, (id, meta) -> {
                meta.subscriptionCount.incrementAndGet();
                meta.lastActiveAt.set(System.currentTimeMillis());
                return meta;
            });
            log.debug("Session subscribed to rheology topic: sessionId={}", sessionId);
        }
    }

    @EventListener
    public void handleSessionUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            return;
        }
        sessions.computeIfPresent(sessionId, (id, meta) -> {
            int count = meta.subscriptionCount.decrementAndGet();
            if (count < 0) {
                meta.subscriptionCount.set(0);
            }
            meta.lastActiveAt.set(System.currentTimeMillis());
            return meta;
        });
        log.debug("Session unsubscribed: sessionId={}", sessionId);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId == null) {
            return;
        }
        SessionMeta removed = sessions.remove(sessionId);
        if (removed != null) {
            log.info("Session disconnected and removed: sessionId={}, ip={}, durationMs={}",
                    sessionId, removed.clientIp, System.currentTimeMillis() - removed.connectedAt);
        }
    }

    @Scheduled(fixedDelay = 10000, initialDelay = 30000)
    public void cleanZombieSessions() {
        long now = System.currentTimeMillis();

        sessions.entrySet().removeIf(entry -> {
            SessionMeta meta = entry.getValue();
            boolean isZombie = (now - meta.lastActiveAt.get()) > ZOMBIE_THRESHOLD_MS;
            if (isZombie) {
                log.warn("Evicting zombie session: sessionId={}, ip={}, idleMs={}",
                        entry.getKey(), meta.clientIp, now - meta.lastActiveAt.get());
            }
            return isZombie;
        });

        int currentSize = sessions.size();
        if (currentSize > MAX_SESSIONS) {
            int toRemove = Math.min(EVICT_BATCH_SIZE, currentSize - MAX_SESSIONS + EVICT_BATCH_SIZE);
            List<String> oldestIds = sessions.values().stream()
                    .sorted(Comparator.comparingLong(m -> m.connectedAt))
                    .limit(toRemove)
                    .map(m -> m.sessionId)
                    .collect(Collectors.toList());
            oldestIds.forEach(id -> {
                SessionMeta evicted = sessions.remove(id);
                if (evicted != null) {
                    log.warn("Evicting oldest session due to capacity: sessionId={}, ip={}, connectedAt={}",
                            id, evicted.clientIp, evicted.connectedAt);
                }
            });
        }

        log.debug("Cleanup sweep complete: activeSessions={}, subscribers={}",
                getActiveSessionCount(), getSubscriberCount());
    }

    public void touch(String sessionId) {
        if (sessionId == null) {
            return;
        }
        SessionMeta meta = sessions.get(sessionId);
        if (meta != null) {
            meta.lastActiveAt.set(System.currentTimeMillis());
        }
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    public int getSubscriberCount() {
        AtomicInteger sum = new AtomicInteger(0);
        sessions.forEachValue(1, meta -> sum.addAndGet(meta.subscriptionCount.get()));
        int total = sum.get();
        return Math.max(total, 0);
    }

    public boolean shouldThrottle() {
        return getSubscriberCount() >= THROTTLE_THRESHOLD;
    }

    static class SessionMeta {
        final String sessionId;
        final long connectedAt;
        final AtomicLong lastActiveAt;
        final String clientIp;
        final AtomicInteger subscriptionCount;

        SessionMeta(String sessionId, long connectedAt, String clientIp) {
            this.sessionId = sessionId;
            this.connectedAt = connectedAt;
            this.lastActiveAt = new AtomicLong(connectedAt);
            this.clientIp = clientIp;
            this.subscriptionCount = new AtomicInteger(0);
        }
    }
}
