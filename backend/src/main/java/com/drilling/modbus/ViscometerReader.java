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
public class ViscometerReader {

    private static final Logger log = LoggerFactory.getLogger(ViscometerReader.class);

    private static final int DIAL_REGISTER_COUNT = 6;
    private static final int GEL_REGISTER_COUNT = 2;
    private static final int TOTAL_REGISTER_COUNT = DIAL_REGISTER_COUNT + GEL_REGISTER_COUNT;

    private final ModbusTcpClient client;
    private final int unitId;
    private final int pollInterval;

    private Consumer<ViscometerRawData> callback;

    public ViscometerReader(
            @Value("${modbus.viscometer.host}") String host,
            @Value("${modbus.viscometer.port}") int port,
            @Value("${modbus.viscometer.unit-id}") int unitId,
            @Value("${modbus.viscometer.poll-interval}") int pollInterval) {
        this.client = new ModbusTcpClient(host, port);
        this.unitId = unitId;
        this.pollInterval = pollInterval;
    }

    public void setCallback(Consumer<ViscometerRawData> callback) {
        this.callback = callback;
    }

    @Scheduled(fixedDelayString = "${modbus.viscometer.poll-interval}")
    public void poll() {
        try {
            int[] registers = client.readHoldingRegisters(unitId, 0, TOTAL_REGISTER_COUNT);
            if (registers == null || registers.length < TOTAL_REGISTER_COUNT) {
                log.warn("Incomplete register response from viscometer: expected {}, got {}",
                        TOTAL_REGISTER_COUNT, registers == null ? 0 : registers.length);
                return;
            }

            double theta600 = registers[0];
            double theta300 = registers[1];
            double theta200 = registers[2];
            double theta100 = registers[3];
            double theta6 = registers[4];
            double theta3 = registers[5];
            double gel10s = registers[6];
            double gel10min = registers[7];

            double pv = theta600 - theta300;
            double yp = theta300 - pv;
            double av = theta600 / 2.0;

            ViscometerRawData data = new ViscometerRawData(
                    theta600, theta300, theta200, theta100, theta6, theta3,
                    gel10s, gel10min, pv, yp, av, Instant.now());

            if (callback != null) {
                callback.accept(data);
            }
        } catch (Exception e) {
            log.error("Error reading viscometer data", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        client.disconnect();
    }

    public static class ViscometerRawData {
        private final double theta600;
        private final double theta300;
        private final double theta200;
        private final double theta100;
        private final double theta6;
        private final double theta3;
        private final double gel10s;
        private final double gel10min;
        private final double pv;
        private final double yp;
        private final double av;
        private final Instant timestamp;

        public ViscometerRawData(double theta600, double theta300, double theta200,
                                 double theta100, double theta6, double theta3,
                                 double gel10s, double gel10min, double pv,
                                 double yp, double av, Instant timestamp) {
            this.theta600 = theta600;
            this.theta300 = theta300;
            this.theta200 = theta200;
            this.theta100 = theta100;
            this.theta6 = theta6;
            this.theta3 = theta3;
            this.gel10s = gel10s;
            this.gel10min = gel10min;
            this.pv = pv;
            this.yp = yp;
            this.av = av;
            this.timestamp = timestamp;
        }

        public double getTheta600() { return theta600; }
        public double getTheta300() { return theta300; }
        public double getTheta200() { return theta200; }
        public double getTheta100() { return theta100; }
        public double getTheta6() { return theta6; }
        public double getTheta3() { return theta3; }
        public double getGel10s() { return gel10s; }
        public double getGel10min() { return gel10min; }
        public double getPv() { return pv; }
        public double getYp() { return yp; }
        public double getAv() { return av; }
        public Instant getTimestamp() { return timestamp; }
    }
}
