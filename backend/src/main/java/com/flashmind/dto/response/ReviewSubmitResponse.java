package com.flashmind.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
public class ReviewSubmitResponse {
    private LocalDate nextReviewDate;
    private Integer interval;
    private Boolean isMastered;
}
