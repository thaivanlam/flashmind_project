package com.flashmind.dto.response;

import com.flashmind.entity.Flashcard;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class FlashcardResponse {
    private Long id;
    private Long deckId;
    private String front;
    private String back;
    private String hint;
    private Boolean isAiGenerated;

    public static FlashcardResponse from(Flashcard card) {
        return FlashcardResponse.builder()
            .id(card.getId())
            .deckId(card.getDeckId())
            .front(card.getFront())
            .back(card.getBack())
            .hint(card.getHint())
            .isAiGenerated(card.getIsAiGenerated())
            .build();
    }
}
