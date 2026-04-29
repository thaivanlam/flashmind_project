package com.flashmind.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "study_sessions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "session_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "cards_reviewed")
    @Builder.Default
    private Integer cardsReviewed = 0;

    @Column(name = "correct_count")
    @Builder.Default
    private Integer correctCount = 0;
}
