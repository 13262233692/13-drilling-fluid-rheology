package com.drilling.risk;

import com.drilling.timeseries.TimeSeriesPoint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class RiskPrediction {

    private final String alertId;
    private final long wellId;
    private final RiskLevel riskLevel;
    private final String message;
    private final double predictedECD;
    private final double fracturePressureThreshold;
    private final double anomalyScore;
    private final double forecastPeak;
    private final double actualTrend;
    private final long timestamp;
    private final List<TimeSeriesPoint> ecdHistory;
    private final List<TimeSeriesPoint> ecdForecast;
    private volatile String acknowledgedBy;
    private volatile long acknowledgedAt;

    public RiskPrediction(long wellId, RiskLevel riskLevel, String message,
                          double predictedECD, double fracturePressureThreshold,
                          double anomalyScore, double forecastPeak, double actualTrend,
                          List<TimeSeriesPoint> ecdHistory, List<TimeSeriesPoint> ecdForecast) {
        this.alertId = UUID.randomUUID().toString();
        this.wellId = wellId;
        this.riskLevel = riskLevel;
        this.message = message;
        this.predictedECD = predictedECD;
        this.fracturePressureThreshold = fracturePressureThreshold;
        this.anomalyScore = anomalyScore;
        this.forecastPeak = forecastPeak;
        this.actualTrend = actualTrend;
        this.timestamp = Instant.now().toEpochMilli();
        this.ecdHistory = ecdHistory;
        this.ecdForecast = ecdForecast;
    }

    public RiskPrediction(long wellId, RiskLevel riskLevel, double anomalyScore,
                          double forecastPeak, double actualTrend,
                          List<TimeSeriesPoint> ecdHistory, List<TimeSeriesPoint> ecdForecast) {
        this(wellId, riskLevel, buildDefaultMessage(riskLevel, wellId),
                forecastPeak, 1.85, anomalyScore, forecastPeak, actualTrend,
                ecdHistory, ecdForecast);
    }

    private static String buildDefaultMessage(RiskLevel level, long wellId) {
        switch (level) {
            case CRITICAL:
                return "井口 #" + wellId + " ECD 异常上扬，ARIMA 预测即将触碰破裂压力阈值";
            case WARNING:
                return "井口 #" + wellId + " ECD 呈现上扬趋势，建议关注";
            default:
                return "井口 #" + wellId + " 正常";
        }
    }

    public void acknowledge(String acknowledgedBy) {
        this.acknowledgedBy = acknowledgedBy;
        this.acknowledgedAt = Instant.now().toEpochMilli();
    }

    public String getAlertId() {
        return alertId;
    }

    public long getWellId() {
        return wellId;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public String getMessage() {
        return message;
    }

    public double getPredictedECD() {
        return predictedECD;
    }

    public double getFracturePressureThreshold() {
        return fracturePressureThreshold;
    }

    public double getAnomalyScore() {
        return anomalyScore;
    }

    public double getForecastPeak() {
        return forecastPeak;
    }

    public double getActualTrend() {
        return actualTrend;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<TimeSeriesPoint> getEcdHistory() {
        return ecdHistory;
    }

    public List<TimeSeriesPoint> getEcdForecast() {
        return ecdForecast;
    }

    public String getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public long getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public boolean isAcknowledged() {
        return acknowledgedBy != null;
    }

    public boolean isActive() {
        return !isAcknowledged() && riskLevel != RiskLevel.NONE;
    }
}
