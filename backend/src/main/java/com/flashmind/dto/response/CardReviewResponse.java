package com.flashmind.dto.response;

import com.flashmind.entity.CardReview;
import com.flashmind.entity.Flashcard;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
public class CardReviewResponse {
    private Long id;
    private Long cardId;
    private FlashcardResponse card;
    private Integer interval;
    private Double easinessFactor;
    private Integer repetitionCount;
    private LocalDate nextReviewDate;

    public static CardReviewResponse from(CardReview review, Flashcard card) {
        return CardReviewResponse.builder()
            .id(review.getId())
            .cardId(review.getCardId())
            .card(card != null ? FlashcardResponse.from(card) : null)
            .interval(review.getInterval())
            .easinessFactor(review.getEasinessFactor())
            .repetitionCount(review.getRepetitionCount())
            .nextReviewDate(review.getNextReviewDate())
            .build();
    }
}
