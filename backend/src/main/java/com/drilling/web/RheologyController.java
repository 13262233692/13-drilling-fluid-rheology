package com.drilling.web;

import com.drilling.buffer.RheologyData;
import com.drilling.service.RheologyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rheology")
public class RheologyController {

    private final RheologyService rheologyService;

    public RheologyController(RheologyService rheologyService) {
        this.rheologyService = rheologyService;
    }

    @GetMapping("/current")
    public ResponseEntity<RheologyData> getCurrent() {
        RheologyData data = rheologyService.getCurrentData();
        if (data == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(data);
    }

    @GetMapping("/raw")
    public ResponseEntity<RheologyData> getRaw() {
        RheologyData data = rheologyService.getRawData();
        if (data == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(data);
    }

    @GetMapping("/history")
    public ResponseEntity<List<RheologyData>> getHistory() {
        return ResponseEntity.ok(rheologyService.getHistory());
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "service", "drilling-fluid-rheology",
                "status", "running",
                "version", "1.0.0"
        ));
    }
}
