package com.drilling.service;

import com.drilling.buffer.DataBuffer;
import com.drilling.buffer.RheologyData;
import com.drilling.modbus.DensitometerReader;
import com.drilling.modbus.ViscometerReader;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class RheologyService {

    private static final Logger log = LoggerFactory.getLogger(RheologyService.class);

    private static final int MAX_HISTORY_SIZE = 600;
    private static final long PUSH_OVERHEAD_THRESHOLD_NS = 100_000_000L;

    private final ViscometerReader viscometerReader;
    private final DensitometerReader densitometerReader;
    private final DataBuffer dataBuffer;
    private final SimpMessagingTemplate messagingTemplate;
    private final ThreadPoolTaskExecutor wsPushExecutor;
    private final WebSocketConnectionManager connectionManager;
    private final int pushInterval;

    private final CopyOnWriteArrayList<RheologyData> history = new CopyOnWriteArrayList<>();
    private final AtomicReference<RheologyData> latestCache = new AtomicReference<>(null);
    private final AtomicLong lastPushSuccessAt = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong pushInFlightNanos = new AtomicLong(0L);
    private final AtomicBoolean pushRunning = new AtomicBoolean(false);
    private final AtomicLong droppedFrames = new AtomicLong(0L);
    private final AtomicLong pushedFrames = new AtomicLong(0L);

    public RheologyService(
            ViscometerReader viscometerReader,
            DensitometerReader densitometerReader,
            DataBuffer dataBuffer,
            SimpMessagingTemplate messagingTemplate,
            @Qualifier("wsPushExecutor") ThreadPoolTaskExecutor wsPushExecutor,
            WebSocketConnectionManager connectionManager,
            @Value("${websocket.push-interval:200}") int pushInterval) {
        this.viscometerReader = viscometerReader;
        this.densitometerReader = densitometerReader;
        this.dataBuffer = dataBuffer;
        this.messagingTemplate = messagingTemplate;
        this.wsPushExecutor = wsPushExecutor;
        this.connectionManager = connectionManager;
        this.pushInterval = pushInterval;
    }

    @PostConstruct
    public void init() {
        viscometerReader.setCallback(data -> {
            try {
                dataBuffer.pushViscometerData(data);
            } catch (Exception e) {
                log.error("Error processing viscometer data in buffer", e);
            }
        });

        densitometerReader.setCallback(data -> {
            try {
                dataBuffer.pushDensitometerData(data);
            } catch (Exception e) {
                log.error("Error processing densitometer data in buffer", e);
            }
        });

        log.info("RheologyService initialized: callbacks wired, push interval={}ms, history cap={}",
                pushInterval, MAX_HISTORY_SIZE);
    }

    @Scheduled(fixedDelayString = "${websocket.push-interval:200}")
    public void pushToClients() {
        RheologyData filtered = dataBuffer.getFilteredData();
        if (filtered == null) {
            return;
        }

        latestCache.lazySet(filtered);
        appendHistory(filtered);

        int subscribers = connectionManager.getSubscriberCount();
        if (subscribers <= 0) {
            return;
        }

        if (!pushRunning.compareAndSet(false, true)) {
            long dropped = droppedFrames.incrementAndGet();
            if (dropped % 100 == 0) {
                log.warn("Backpressure: previous push still in-flight, dropped frame #{} (subscribers={})",
                        dropped, subscribers);
            }
            return;
        }

        boolean throttle = connectionManager.shouldThrottle();
        long startNanos = System.nanoTime();

        try {
            wsPushExecutor.submit(() -> {
                long pushStartNanos = System.nanoTime();
                try {
                    RheologyData current = latestCache.get();
                    if (current == null) {
                        return;
                    }
                    messagingTemplate.convertAndSend("/topic/rheology", current);
                    pushedFrames.incrementAndGet();
                    lastPushSuccessAt.lazySet(System.currentTimeMillis());
                } catch (Exception e) {
                    log.error("Exception in WebSocket push worker", e);
                } finally {
                    long elapsed = System.nanoTime() - pushStartNanos;
                    pushInFlightNanos.addAndGet(elapsed);
                    pushRunning.lazySet(false);
                }
            });
        } catch (Exception e) {
            log.error("Failed to submit push task to wsPushExecutor (subscribers={})", subscribers, e);
            pushRunning.lazySet(false);
        }

        long schedulerElapsed = System.nanoTime() - startNanos;
        if (schedulerElapsed > PUSH_OVERHEAD_THRESHOLD_NS) {
            log.warn("Push scheduler overhead exceeded {}ms, actual={}ms, throttle={}",
                    PUSH_OVERHEAD_THRESHOLD_NS / 1_000_000, schedulerElapsed / 1_000_000, throttle);
        }
    }

    private void appendHistory(RheologyData data) {
        history.add(data);
        int size = history.size();
        if (size > MAX_HISTORY_SIZE) {
            int overflow = size - MAX_HISTORY_SIZE;
            if (overflow > 10) {
                List<RheologyData> retained = new ArrayList<>(
                        history.subList(overflow, history.size()));
                history.clear();
                history.addAll(retained);
            } else {
                for (int i = 0; i < overflow; i++) {
                    history.remove(0);
                }
            }
        }
    }

    public RheologyData getCurrentData() {
        RheologyData cached = latestCache.get();
        return cached != null ? cached : dataBuffer.getFilteredData();
    }

    public RheologyData getRawData() {
        return dataBuffer.getLatestRawData();
    }

    public List<RheologyData> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public long getPushedFrames() {
        return pushedFrames.get();
    }

    public long getDroppedFrames() {
        return droppedFrames.get();
    }

    public long getPushInFlightNanos() {
        return pushInFlightNanos.get();
    }
}
