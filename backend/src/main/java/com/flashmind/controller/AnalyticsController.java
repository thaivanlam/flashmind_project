package com.flashmind.controller;

import com.flashmind.dto.response.AnalyticsResponse;
import com.flashmind.security.AuthHelper;
import com.flashmind.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping
    public ResponseEntity<AnalyticsResponse> getAnalytics() {
        return ResponseEntity.ok(
            analyticsService.getUserAnalytics(AuthHelper.getCurrentUserId())
        );
    }
}
