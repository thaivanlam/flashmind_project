package com.flashmind.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "flashcards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Flashcard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deck_id", nullable = false)
    private Long deckId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String front;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String back;

    @Column(columnDefinition = "TEXT")
    private String hint;

    @Column(name = "is_ai_generated")
    @Builder.Default
    private Boolean isAiGenerated = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
