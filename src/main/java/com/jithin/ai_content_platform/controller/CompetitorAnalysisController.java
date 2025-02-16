package com.jithin.ai_content_platform.controller;

import com.jithin.ai_content_platform.service.CompetitorAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/competitor-analysis")
@Slf4j
public class CompetitorAnalysisController {

    @Autowired
    private CompetitorAnalysisService competitorAnalysisService;

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeCompetitors(
            @RequestParam String industry,
            @RequestBody List<String> competitors) {
        try {
            Map<String, Object> analysis = competitorAnalysisService.analyzeCompetitorContent(industry, competitors);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            log.error("Error analyzing competitors: ", e);
            return ResponseEntity.internalServerError().body("Error analyzing competitors: " + e.getMessage());
        }
    }

    @GetMapping("/competitive-advantage/{industry}")
    public ResponseEntity<?> getCompetitiveAdvantage(
            @PathVariable String industry) {
        try {
            Map<String, Object> report = competitorAnalysisService.generateCompetitiveAdvantageReport(industry);
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Error generating competitive advantage report: ", e);
            return ResponseEntity.internalServerError().body("Error generating report: " + e.getMessage());
        }
    }

    @GetMapping("/predict/{competitor}")
    public ResponseEntity<?> predictCompetitorMoves(
            @PathVariable String competitor,
            @RequestParam String industry) {
        try {
            Map<String, Object> prediction = competitorAnalysisService.predictCompetitorMoves(competitor, industry);
            return ResponseEntity.ok(prediction);
        } catch (Exception e) {
            log.error("Error predicting competitor moves: ", e);
            return ResponseEntity.internalServerError().body("Error predicting moves: " + e.getMessage());
        }
    }
}
