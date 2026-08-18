package com.flashmind.repository;

import com.flashmind.entity.CardReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CardReviewRepository extends JpaRepository<CardReview, Long> {

    Optional<CardReview> findByCardIdAndUserId(Long cardId, Long userId);

    /**
     * Returns only reviews whose card still exists — skips orphaned reviews left in
     * the DB by older data (from before the cascade delete was fixed).
     */
    @Query("SELECT cr FROM CardReview cr WHERE cr.userId = :userId " +
           "AND cr.nextReviewDate <= :date " +
           "AND EXISTS (SELECT 1 FROM Flashcard f WHERE f.id = cr.cardId)")
    List<CardReview> findDueReviews(@Param("userId") Long userId,
                                    @Param("date") LocalDate date);

    @Query("SELECT cr.cardId FROM CardReview cr WHERE cr.userId = :userId " +
           "AND cr.nextReviewDate <= :date " +
           "AND EXISTS (SELECT 1 FROM Flashcard f WHERE f.id = cr.cardId)")
    List<Long> findDueCardIds(@Param("userId") Long userId,
                              @Param("date") LocalDate date);

    @Query("SELECT DISTINCT cr.userId FROM CardReview cr")
    List<Long> findAllDistinctUserIds();

    long countByUserIdAndRepetitionCountGreaterThanEqual(Long userId, Integer count);

    /** Deletes a single card's review — called when deleting a card so no orphan is left behind. */
    @Modifying
    @Query("DELETE FROM CardReview cr WHERE cr.cardId = :cardId")
    int deleteByCardId(@Param("cardId") Long cardId);

    /** Deletes the reviews of many cards in one statement — called when deleting a whole deck. */
    @Modifying
    @Query("DELETE FROM CardReview cr WHERE cr.cardId IN :cardIds")
    int deleteByCardIdIn(@Param("cardIds") Collection<Long> cardIds);

    /** Purges orphaned reviews (whose card was deleted) still left in the DB. */
    @Modifying
    @Query("DELETE FROM CardReview cr " +
           "WHERE NOT EXISTS (SELECT 1 FROM Flashcard f WHERE f.id = cr.cardId)")
    int deleteOrphanedReviews();
}
