package com.drilling.timeseries;

public class ECDData {

    private final long timestamp;
    private final double ecd;

    public ECDData(long timestamp, double ecd) {
        this.timestamp = timestamp;
        this.ecd = ecd;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getEcd() {
        return ecd;
    }
}
