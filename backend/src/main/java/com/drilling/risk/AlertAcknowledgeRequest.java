package com.drilling.risk;

public class AlertAcknowledgeRequest {

    private String acknowledgedBy;

    public AlertAcknowledgeRequest() {
    }

    public AlertAcknowledgeRequest(String acknowledgedBy) {
        this.acknowledgedBy = acknowledgedBy;
    }

    public String getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public void setAcknowledgedBy(String acknowledgedBy) {
        this.acknowledgedBy = acknowledgedBy;
    }
}
