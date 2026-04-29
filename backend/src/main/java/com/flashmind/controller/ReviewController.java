package com.flashmind.controller;

import com.flashmind.dto.request.ReviewSubmitRequest;
import com.flashmind.dto.response.CardReviewResponse;
import com.flashmind.dto.response.ReviewSubmitResponse;
import com.flashmind.security.AuthHelper;
import com.flashmind.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/today")
    public ResponseEntity<List<CardReviewResponse>> getTodayReviews() {
        return ResponseEntity.ok(reviewService.getTodayReviews(AuthHelper.getCurrentUserId()));
    }

    @PostMapping("/{cardId}")
    public ResponseEntity<ReviewSubmitResponse> submit(@PathVariable Long cardId,
                                                       @Valid @RequestBody ReviewSubmitRequest req) {
        return ResponseEntity.ok(
            reviewService.submitReview(cardId, AuthHelper.getCurrentUserId(), req.getQuality())
        );
    }
}
