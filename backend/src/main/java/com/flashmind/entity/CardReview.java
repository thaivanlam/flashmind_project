package com.flashmind.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "card_reviews",
       uniqueConstraints = @UniqueConstraint(columnNames = {"card_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false)
    private Long cardId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "interval_days")
    @Builder.Default
    private Integer interval = 0;

    @Column(name = "easiness_factor")
    @Builder.Default
    private Double easinessFactor = 2.5;

    @Column(name = "repetition_count")
    @Builder.Default
    private Integer repetitionCount = 0;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;

    public CardReview(Long cardId, Long userId) {
        this.cardId = cardId;
        this.userId = userId;
        this.interval = 0;
        this.easinessFactor = 2.5;
        this.repetitionCount = 0;
        this.nextReviewDate = LocalDate.now();
    }
}
