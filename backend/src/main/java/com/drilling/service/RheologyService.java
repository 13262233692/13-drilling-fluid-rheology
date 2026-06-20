package com.drilling.service;

import com.drilling.buffer.DataBuffer;
import com.drilling.buffer.RheologyData;
import com.drilling.modbus.DensitometerReader;
import com.drilling.modbus.ViscometerReader;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class RheologyService {

    private static final Logger log = LoggerFactory.getLogger(RheologyService.class);

    private static final int MAX_HISTORY_SIZE = 600;

    private final ViscometerReader viscometerReader;
    private final DensitometerReader densitometerReader;
    private final DataBuffer dataBuffer;
    private final SimpMessagingTemplate messagingTemplate;
    private final int pushInterval;

    private final ConcurrentLinkedDeque<RheologyData> history = new ConcurrentLinkedDeque<>();

    public RheologyService(
            ViscometerReader viscometerReader,
            DensitometerReader densitometerReader,
            DataBuffer dataBuffer,
            SimpMessagingTemplate messagingTemplate,
            @Value("${websocket.push-interval:200}") int pushInterval) {
        this.viscometerReader = viscometerReader;
        this.densitometerReader = densitometerReader;
        this.dataBuffer = dataBuffer;
        this.messagingTemplate = messagingTemplate;
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

        log.info("RheologyService initialized: callbacks wired, push interval={}ms", pushInterval);
    }

    @Scheduled(fixedDelayString = "${websocket.push-interval:200}")
    public void pushToClients() {
        try {
            RheologyData filtered = dataBuffer.getFilteredData();
            if (filtered == null) {
                return;
            }

            history.addLast(filtered);
            while (history.size() > MAX_HISTORY_SIZE) {
                history.pollFirst();
            }

            messagingTemplate.convertAndSend("/topic/rheology", filtered);
        } catch (Exception e) {
            log.error("Error pushing rheology data to WebSocket clients", e);
        }
    }

    public RheologyData getCurrentData() {
        return dataBuffer.getFilteredData();
    }

    public RheologyData getRawData() {
        return dataBuffer.getLatestRawData();
    }

    public List<RheologyData> getHistory() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }
}
