package com.drilling.web;

import com.drilling.risk.AlertAcknowledgeRequest;
import com.drilling.risk.RiskPrediction;
import com.drilling.risk.RiskPredictionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final RiskPredictionService riskPredictionService;

    public AlertController(RiskPredictionService riskPredictionService) {
        this.riskPredictionService = riskPredictionService;
    }

    @GetMapping("/active")
    public ResponseEntity<List<RiskPrediction>> getActiveAlerts() {
        return ResponseEntity.ok(riskPredictionService.getActiveAlerts());
    }

    @GetMapping("/history")
    public ResponseEntity<List<RiskPrediction>> getAlertHistory() {
        return ResponseEntity.ok(riskPredictionService.getAlertHistory());
    }

    @GetMapping("/{alertId}")
    public ResponseEntity<RiskPrediction> getAlert(@PathVariable String alertId) {
        for (RiskPrediction alert : riskPredictionService.getActiveAlerts()) {
            if (alert.getAlertId().equals(alertId)) {
                return ResponseEntity.ok(alert);
            }
        }
        for (RiskPrediction alert : riskPredictionService.getAlertHistory()) {
            if (alert.getAlertId().equals(alertId)) {
                return ResponseEntity.ok(alert);
            }
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{alertId}/acknowledge")
    public ResponseEntity<RiskPrediction> acknowledgeAlert(
            @PathVariable String alertId,
            @RequestBody AlertAcknowledgeRequest request) {
        String by = request.getAcknowledgedBy();
        if (by == null || by.trim().isEmpty()) {
            by = "操作员";
        }
        RiskPrediction acknowledged = riskPredictionService.acknowledgeAlert(alertId, by);
        if (acknowledged == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(acknowledged);
    }

    @PostMapping("/{alertId}/confirm")
    public ResponseEntity<RiskPrediction> confirmAndMitigate(
            @PathVariable String alertId,
            @RequestBody AlertAcknowledgeRequest request) {
        return acknowledgeAlert(alertId, request);
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        int activeCount = riskPredictionService.getActiveAlerts().size();
        int criticalCount = (int) riskPredictionService.getActiveAlerts().stream()
                .filter(a -> a.getRiskLevel().getSeverity() == 2)
                .count();
        return ResponseEntity.ok(Map.of(
                "activeAlerts", activeCount,
                "criticalAlerts", criticalCount,
                "historicalAlerts", riskPredictionService.getAlertHistory().size()
        ));
    }
}
