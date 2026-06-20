package com.drilling.modbus;

import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.function.Consumer;

@Component
public class DensitometerReader {

    private static final Logger log = LoggerFactory.getLogger(DensitometerReader.class);

    private static final int REGISTER_COUNT = 2;

    private final ModbusTcpClient client;
    private final int unitId;

    private Consumer<DensitometerRawData> callback;

    public DensitometerReader(
            @Value("${modbus.densitometer.host}") String host,
            @Value("${modbus.densitometer.port}") int port,
            @Value("${modbus.densitometer.unit-id}") int unitId,
            @Value("${modbus.densitometer.poll-interval}") int pollInterval) {
        this.client = new ModbusTcpClient(host, port);
        this.unitId = unitId;
    }

    public void setCallback(Consumer<DensitometerRawData> callback) {
        this.callback = callback;
    }

    @Scheduled(fixedDelayString = "${modbus.densitometer.poll-interval}")
    public void poll() {
        try {
            int[] registers = client.readHoldingRegisters(unitId, 0, REGISTER_COUNT);
            if (registers == null || registers.length < REGISTER_COUNT) {
                log.warn("Incomplete register response from densitometer: expected {}, got {}",
                        REGISTER_COUNT, registers == null ? 0 : registers.length);
                return;
            }

            double density = registers[0] / 100.0;
            double temperature = registers[1] / 10.0;

            DensitometerRawData data = new DensitometerRawData(density, temperature, Instant.now());

            if (callback != null) {
                callback.accept(data);
            }
        } catch (Exception e) {
            log.error("Error reading densitometer data", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        client.disconnect();
    }

    public static class DensitometerRawData {
        private final double density;
        private final double temperature;
        private final Instant timestamp;

        public DensitometerRawData(double density, double temperature, Instant timestamp) {
            this.density = density;
            this.temperature = temperature;
            this.timestamp = timestamp;
        }

        public double getDensity() { return density; }
        public double getTemperature() { return temperature; }
        public Instant getTimestamp() { return timestamp; }
    }
}
