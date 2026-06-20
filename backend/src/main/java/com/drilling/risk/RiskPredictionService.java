package com.drilling.risk;

import com.drilling.service.WebSocketConnectionManager;
import com.drilling.timeseries.ARIMAPredictor;
import com.drilling.timeseries.ECDData;
import com.drilling.timeseries.TimeSeriesDataProvider;
import com.drilling.timeseries.TimeSeriesPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RiskPredictionService {

    private static final Logger log = LoggerFactory.getLogger(RiskPredictionService.class);

    private static final int ARIMA_HISTORY_POINTS = 288;
    private static final int FORECAST_STEPS = 24;
    private static final int TREND_CALCULATION_POINTS = 60;
    private static final int HISTORY_DISPLAY_POINTS = 120;
    private static final int INTERVAL_SECONDS = 15;
    private static final int MAX_HISTORY_SIZE = 100;

    private final TimeSeriesDataProvider dataProvider;
    private final ARIMAPredictor arimaPredictor;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketConnectionManager connectionManager;
    private final double fracturePressureThreshold;
    private final double anomalyThreshold;
    private final long pollInterval;

    private final ConcurrentHashMap<String, RiskPrediction> activeAlerts = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<RiskPrediction> alertHistory = new ConcurrentLinkedDeque<>();
    private final AtomicLong lastPredictionAt = new AtomicLong(0);
    private final ExecutorService predictionExecutor = Executors.newSingleThreadExecutor();

    public RiskPredictionService(
            TimeSeriesDataProvider dataProvider,
            ARIMAPredictor arimaPredictor,
            SimpMessagingTemplate messagingTemplate,
            WebSocketConnectionManager connectionManager,
            @Value("${fracture.pressure.threshold:1.85}") double fracturePressureThreshold,
            @Value("${risk.anomaly.threshold:0.8}") double anomalyThreshold,
            @Value("${risk.poll-interval:30000}") long pollInterval) {
        this.dataProvider = dataProvider;
        this.arimaPredictor = arimaPredictor;
        this.messagingTemplate = messagingTemplate;
        this.connectionManager = connectionManager;
        this.fracturePressureThreshold = fracturePressureThreshold;
        this.anomalyThreshold = anomalyThreshold;
        this.pollInterval = pollInterval;
    }

    @Scheduled(fixedDelayString = "${risk.poll-interval:30000}")
    public void runRiskAssessment() {
        predictionExecutor.submit(() -> {
            try {
                for (long wellId = 1; wellId <= 3; wellId++) {
                    final long currentWellId = wellId;
                    CompletableFuture<List<ECDData>> ecdFuture = dataProvider.fetchECDLast24h(currentWellId);
                    CompletableFuture<List<TimeSeriesPoint>> frictionFuture = dataProvider.fetchFrictionDragLast24h(currentWellId);
                    CompletableFuture<List<TimeSeriesPoint>> wobFuture = dataProvider.fetchWeightOnBitLast24h(currentWellId);

                    ecdFuture.thenCombine(frictionFuture, (ecdList, frictionList) ->
                                    new Object[]{ecdList, frictionList})
                            .thenCombine(wobFuture, (arr, wobList) -> {
                                @SuppressWarnings("unchecked")
                                List<ECDData> ecdList = (List<ECDData>) arr[0];
                                @SuppressWarnings("unchecked")
                                List<TimeSeriesPoint> frictionList = (List<TimeSeriesPoint>) arr[1];
                                return assessRisk(currentWellId, ecdList, frictionList, wobList);
                            })
                            .exceptionally(ex -> {
                                log.error("Error in risk assessment for wellId={}", currentWellId, ex);
                                return null;
                            });
                }
                lastPredictionAt.set(System.currentTimeMillis());
            } catch (Exception e) {
                log.error("Error in scheduled risk assessment", e);
            }
        });
    }

    public RiskPrediction assessRisk(long wellId, List<ECDData> ecdData,
                                      List<TimeSeriesPoint> friction, List<TimeSeriesPoint> wob) {
        try {
            if (ecdData == null || ecdData.size() < ARIMA_HISTORY_POINTS) {
                log.warn("Insufficient ECD data for wellId={}, size={}", wellId,
                        ecdData != null ? ecdData.size() : 0);
                return createEmptyPrediction(wellId);
            }

            double[] ecdValues = extractValues(ecdData);
            int totalPoints = ecdValues.length;
            double[] arimaInput = new double[ARIMA_HISTORY_POINTS];
            System.arraycopy(ecdValues, totalPoints - ARIMA_HISTORY_POINTS, arimaInput, 0, ARIMA_HISTORY_POINTS);

            double[] forecast = arimaPredictor.forecast(arimaInput, FORECAST_STEPS);

            double[] trendData = new double[TREND_CALCULATION_POINTS];
            System.arraycopy(ecdValues, totalPoints - TREND_CALCULATION_POINTS, trendData, 0, TREND_CALCULATION_POINTS);
            double actualTrend = calculateTrendSlope(trendData);

            double forecastPeak = Double.MIN_VALUE;
            for (double v : forecast) {
                if (v > forecastPeak) {
                    forecastPeak = v;
                }
            }

            double[] fullTrendData = new double[TREND_CALCULATION_POINTS];
            System.arraycopy(ecdValues, totalPoints - TREND_CALCULATION_POINTS, fullTrendData, 0, TREND_CALCULATION_POINTS);
            double[] firstDifferences = new double[fullTrendData.length - 1];
            for (int i = 1; i < fullTrendData.length; i++) {
                firstDifferences[i - 1] = fullTrendData[i] - fullTrendData[i - 1];
            }
            double nonlinearDeviation = calculateVariance(firstDifferences);

            double pressureRatio = (forecastPeak > fracturePressureThreshold) ? 1.0 : (forecastPeak / fracturePressureThreshold);
            double anomalyScore = pressureRatio * (1 + nonlinearDeviation * 5);

            RiskLevel riskLevel;
            if (anomalyScore >= anomalyThreshold && forecastPeak >= fracturePressureThreshold * 0.95) {
                riskLevel = RiskLevel.CRITICAL;
            } else if (anomalyScore >= anomalyThreshold * 0.7 && forecastPeak >= fracturePressureThreshold * 0.85) {
                riskLevel = RiskLevel.WARNING;
            } else {
                riskLevel = RiskLevel.NONE;
            }

            int historyStart = Math.max(0, ecdData.size() - HISTORY_DISPLAY_POINTS);
            List<TimeSeriesPoint> ecdHistory = new ArrayList<>(HISTORY_DISPLAY_POINTS);
            for (int i = historyStart; i < ecdData.size(); i++) {
                ECDData d = ecdData.get(i);
                ecdHistory.add(new TimeSeriesPoint(d.getTimestamp(), d.getEcd()));
            }

            long lastTimestamp = ecdData.get(ecdData.size() - 1).getTimestamp();
            List<TimeSeriesPoint> ecdForecast = buildForecastSeries(forecast, lastTimestamp + INTERVAL_SECONDS * 1000);

            RiskPrediction prediction = new RiskPrediction(wellId, riskLevel, buildMessage(riskLevel, wellId, forecastPeak, anomalyScore),
                    forecastPeak, fracturePressureThreshold, anomalyScore,
                    forecastPeak, actualTrend, ecdHistory, ecdForecast);

            boolean hasActiveCritical = activeAlerts.values().stream()
                    .anyMatch(p -> p.getRiskLevel() == RiskLevel.CRITICAL && !p.isAcknowledged());

            if (riskLevel == RiskLevel.CRITICAL) {
                activeAlerts.put(prediction.getAlertId(), prediction);
                addToHistory(prediction);
                broadcastAlert(prediction);
                log.warn("CRITICAL risk detected for wellId={}, anomalyScore={}, forecastPeak={}",
                        wellId, anomalyScore, forecastPeak);
            } else if (riskLevel == RiskLevel.WARNING && !hasActiveCritical) {
                broadcastAlert(prediction);
                addToHistory(prediction);
                log.info("WARNING risk detected for wellId={}, anomalyScore={}, forecastPeak={}",
                        wellId, anomalyScore, forecastPeak);
            }

            return prediction;
        } catch (Exception e) {
            log.error("Error assessing risk for wellId={}", wellId, e);
            return createEmptyPrediction(wellId);
        }
    }

    public RiskPrediction acknowledgeAlert(String alertId, String acknowledgedBy) {
        RiskPrediction prediction = activeAlerts.get(alertId);
        if (prediction == null) {
            log.warn("Alert not found for acknowledgement: {}", alertId);
            return null;
        }

        prediction.acknowledge(acknowledgedBy);
        broadcastAlert(prediction);
        log.info("Alert {} acknowledged by {}", alertId, acknowledgedBy);

        CompletableFuture.delayedExecutor(10, java.util.concurrent.TimeUnit.SECONDS, predictionExecutor)
                .execute(() -> {
                    activeAlerts.remove(alertId);
                    log.debug("Removed acknowledged alert {} from active alerts", alertId);
                });

        return prediction;
    }

    public List<RiskPrediction> getActiveAlerts() {
        return new ArrayList<>(activeAlerts.values());
    }

    public List<RiskPrediction> getAlertHistory() {
        return new ArrayList<>(alertHistory);
    }

    private double calculateTrendSlope(double[] data) {
        if (data == null || data.length < 2) {
            return 0.0;
        }

        int n = data.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;

        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += data[i];
            sumXY += (double) i * data[i];
            sumXX += (double) i * i;
        }

        double denominator = (n * sumXX - sumX * sumX);
        if (Math.abs(denominator) < 1e-10) {
            return 0.0;
        }

        return (n * sumXY - sumX * sumY) / denominator;
    }

    private double calculateVariance(double[] data) {
        if (data == null || data.length < 2) {
            return 0.0;
        }

        double mean = 0;
        for (double v : data) {
            mean += v;
        }
        mean /= data.length;

        double sumSq = 0;
        for (double v : data) {
            double diff = v - mean;
            sumSq += diff * diff;
        }

        return sumSq / data.length;
    }

    private double[] extractValues(List<ECDData> list) {
        if (list == null) {
            return new double[0];
        }
        double[] values = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            values[i] = list.get(i).getEcd();
        }
        return values;
    }

    private List<TimeSeriesPoint> buildForecastSeries(double[] forecast, long startTime) {
        List<TimeSeriesPoint> series = new ArrayList<>(forecast.length);
        for (int i = 0; i < forecast.length; i++) {
            long timestamp = startTime + (long) i * INTERVAL_SECONDS * 1000;
            series.add(new TimeSeriesPoint(timestamp, forecast[i]));
        }
        return series;
    }

    private RiskPrediction createEmptyPrediction(long wellId) {
        return new RiskPrediction(wellId, RiskLevel.NONE, "",
                0.0, fracturePressureThreshold, 0.0, 0.0, 0.0,
                new ArrayList<>(), new ArrayList<>());
    }

    private String buildMessage(RiskLevel level, long wellId, double peak, double score) {
        switch (level) {
            case CRITICAL:
                return String.format("井口 #%d ECD 非线性异常上扬，ARIMA 预测峰值 %.3f g/cm³ 即将触碰破裂压力 %.2f g/cm³",
                        wellId, peak, fracturePressureThreshold);
            case WARNING:
                return String.format("井口 #%d ECD 呈现上扬趋势，预测峰值 %.3f g/cm³，异常得分 %.2f",
                        wellId, peak, score);
            default:
                return String.format("井口 #%d 正常", wellId);
        }
    }

    private void broadcastAlert(RiskPrediction prediction) {
        try {
            messagingTemplate.convertAndSend("/topic/alerts", prediction);
        } catch (Exception e) {
            log.error("Error broadcasting alert for wellId={}", prediction.getWellId(), e);
        }
    }

    private void addToHistory(RiskPrediction prediction) {
        alertHistory.addFirst(prediction);
        while (alertHistory.size() > MAX_HISTORY_SIZE) {
            alertHistory.removeLast();
        }
    }
}
