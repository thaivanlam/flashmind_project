package com.flashmind.dto.response;

import com.flashmind.entity.Deck;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class DeckResponse {
    private Long id;
    private Long userId;
    private String title;
    private String description;
    private String language;
    private Integer cardCount;
    private LocalDateTime createdAt;

    public static DeckResponse from(Deck deck) {
        return DeckResponse.builder()
            .id(deck.getId())
            .userId(deck.getUserId())
            .title(deck.getTitle())
            .description(deck.getDescription())
            .language(deck.getLanguage())
            .cardCount(deck.getCardCount())
            .createdAt(deck.getCreatedAt())
            .build();
    }
}
