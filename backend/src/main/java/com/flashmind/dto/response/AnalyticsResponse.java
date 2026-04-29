package com.flashmind.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class AnalyticsResponse {
    private Integer currentStreak;
    private Long totalCardsReviewed;
    private Long masteredCards;
    private List<DailyStat> last30Days;

    @Data
    @Builder
    @AllArgsConstructor
    public static class DailyStat {
        private LocalDate date;
        private Integer cardsReviewed;
        private Integer correctCount;
    }
}
