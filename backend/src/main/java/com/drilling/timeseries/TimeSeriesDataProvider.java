package com.drilling.timeseries;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class TimeSeriesDataProvider {

    private static final Logger log = LoggerFactory.getLogger(TimeSeriesDataProvider.class);

    private static final int POINTS_24H = 5760;
    private static final int INTERVAL_SECONDS = 15;
    private static final double ECD_BASELINE = 1.3;
    private static final double FRICTION_BASELINE_MIN = 2.5;
    private static final double FRICTION_BASELINE_MAX = 4.0;
    private static final double WOB_BASELINE_MIN = 8.0;
    private static final double WOB_BASELINE_MAX = 12.0;

    private final ThreadPoolTaskExecutor wsChannelExecutor;

    public TimeSeriesDataProvider(@Qualifier("wsChannelExecutor") ThreadPoolTaskExecutor wsChannelExecutor) {
        this.wsChannelExecutor = wsChannelExecutor;
    }

    @Async("wsChannelExecutor")
    public CompletableFuture<List<ECDData>> fetchECDLast24h(long wellId) {
        try {
            Thread.sleep(200 + ThreadLocalRandom.current().nextInt(300));

            List<ECDData> data = new ArrayList<>(POINTS_24H);
            long now = System.currentTimeMillis();
            long startTime = now - (long) POINTS_24H * INTERVAL_SECONDS * 1000;

            double ecd = ECD_BASELINE;
            boolean hasAnomaly = (wellId % 3 == 0);
            int anomalyStartIndex = POINTS_24H - 480;

            for (int i = 0; i < POINTS_24H; i++) {
                long timestamp = startTime + (long) i * INTERVAL_SECONDS * 1000;
                double noise = ThreadLocalRandom.current().nextGaussian() * 0.02;
                double drift = Math.sin(i / 120.0) * 0.03;
                ecd = ECD_BASELINE + noise + drift;

                if (hasAnomaly && i >= anomalyStartIndex) {
                    int stepsSinceAnomaly = i - anomalyStartIndex;
                    int intervalsPer15Min = 60;
                    int periods = stepsSinceAnomaly / intervalsPer15Min;
                    double anomalyMultiplier = Math.pow(1.05, periods);
                    ecd = ECD_BASELINE * anomalyMultiplier + noise;
                }

                data.add(new ECDData(timestamp, ecd));
            }

            log.debug("Fetched {} ECD points for wellId={}", data.size(), wellId);
            return CompletableFuture.completedFuture(data);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("ECD fetch interrupted for wellId={}", wellId, e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        } catch (Exception e) {
            log.error("Error fetching ECD data for wellId={}", wellId, e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    @Async("wsChannelExecutor")
    public CompletableFuture<List<TimeSeriesPoint>> fetchFrictionDragLast24h(long wellId) {
        try {
            Thread.sleep(200 + ThreadLocalRandom.current().nextInt(300));

            List<TimeSeriesPoint> data = new ArrayList<>(POINTS_24H);
            long now = System.currentTimeMillis();
            long startTime = now - (long) POINTS_24H * INTERVAL_SECONDS * 1000;

            double frictionBase = (FRICTION_BASELINE_MIN + FRICTION_BASELINE_MAX) / 2.0;
            boolean hasAnomaly = (wellId % 3 == 0);
            int anomalyStartIndex = POINTS_24H - 480;

            for (int i = 0; i < POINTS_24H; i++) {
                long timestamp = startTime + (long) i * INTERVAL_SECONDS * 1000;
                double ecdNoise = Math.sin(i / 120.0) * 0.03;
                double correlatedComponent = ecdNoise * 8.0;
                double noise = ThreadLocalRandom.current().nextGaussian() * 0.15;
                double friction = frictionBase + correlatedComponent + noise;

                if (hasAnomaly && i >= anomalyStartIndex) {
                    int stepsSinceAnomaly = i - anomalyStartIndex;
                    int intervalsPer15Min = 60;
                    int periods = stepsSinceAnomaly / intervalsPer15Min;
                    double anomalyMultiplier = Math.pow(1.04, periods);
                    friction = frictionBase * anomalyMultiplier + noise;
                }

                friction = Math.max(FRICTION_BASELINE_MIN * 0.5, Math.min(FRICTION_BASELINE_MAX * 2.0, friction));
                data.add(new TimeSeriesPoint(timestamp, friction));
            }

            log.debug("Fetched {} friction points for wellId={}", data.size(), wellId);
            return CompletableFuture.completedFuture(data);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Friction fetch interrupted for wellId={}", wellId, e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        } catch (Exception e) {
            log.error("Error fetching friction data for wellId={}", wellId, e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    @Async("wsChannelExecutor")
    public CompletableFuture<List<TimeSeriesPoint>> fetchWeightOnBitLast24h(long wellId) {
        try {
            Thread.sleep(200 + ThreadLocalRandom.current().nextInt(300));

            List<TimeSeriesPoint> data = new ArrayList<>(POINTS_24H);
            long now = System.currentTimeMillis();
            long startTime = now - (long) POINTS_24H * INTERVAL_SECONDS * 1000;

            double wobBase = (WOB_BASELINE_MIN + WOB_BASELINE_MAX) / 2.0;
            boolean hasAnomaly = (wellId % 3 == 0);
            int anomalyStartIndex = POINTS_24H - 480;

            for (int i = 0; i < POINTS_24H; i++) {
                long timestamp = startTime + (long) i * INTERVAL_SECONDS * 1000;

                int cyclePos = i % 240;
                double drillingPattern;
                if (cyclePos < 180) {
                    drillingPattern = wobBase + Math.sin(cyclePos / 30.0) * 1.5;
                } else {
                    drillingPattern = wobBase * 0.3 + ThreadLocalRandom.current().nextDouble() * 0.5;
                }

                double noise = ThreadLocalRandom.current().nextGaussian() * 0.3;
                double wob = drillingPattern + noise;

                if (hasAnomaly && i >= anomalyStartIndex) {
                    wob += (i - anomalyStartIndex) * 0.002;
                }

                wob = Math.max(0.5, Math.min(WOB_BASELINE_MAX * 1.5, wob));
                data.add(new TimeSeriesPoint(timestamp, wob));
            }

            log.debug("Fetched {} WOB points for wellId={}", data.size(), wellId);
            return CompletableFuture.completedFuture(data);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("WOB fetch interrupted for wellId={}", wellId, e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        } catch (Exception e) {
            log.error("Error fetching WOB data for wellId={}", wellId, e);
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }
}
