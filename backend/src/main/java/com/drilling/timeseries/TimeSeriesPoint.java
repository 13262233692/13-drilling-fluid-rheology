package com.drilling.timeseries;

public class TimeSeriesPoint {

    private final long timestamp;
    private final double value;
    private final String metric;

    public TimeSeriesPoint(long timestamp, double value) {
        this(timestamp, value, null);
    }

    public TimeSeriesPoint(long timestamp, double value, String metric) {
        this.timestamp = timestamp;
        this.value = value;
        this.metric = metric;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getValue() {
        return value;
    }

    public String getMetric() {
        return metric;
    }

    @Override
    public String toString() {
        return "TimeSeriesPoint{timestamp=" + timestamp + ", value=" + value + ", metric='" + metric + "'}";
    }
}
