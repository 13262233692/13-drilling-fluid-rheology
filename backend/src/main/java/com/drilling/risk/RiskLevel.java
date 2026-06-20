package com.drilling.risk;

public enum RiskLevel {
    NONE(0),
    WARNING(1),
    CRITICAL(2);

    private final int severity;

    RiskLevel(int severity) {
        this.severity = severity;
    }

    public int getSeverity() {
        return severity;
    }
}
