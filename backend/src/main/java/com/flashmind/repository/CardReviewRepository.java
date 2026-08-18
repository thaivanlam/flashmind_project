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
     * Chỉ trả về review có thẻ tương ứng còn tồn tại — bỏ qua review mồ côi
     * còn sót lại trong DB từ dữ liệu cũ (trước khi cascade delete được sửa).
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

    /** Xóa review của một thẻ — gọi khi xóa thẻ để không để lại bản ghi mồ côi. */
    @Modifying
    @Query("DELETE FROM CardReview cr WHERE cr.cardId = :cardId")
    int deleteByCardId(@Param("cardId") Long cardId);

    /** Xóa review của nhiều thẻ trong một câu lệnh — gọi khi xóa cả deck. */
    @Modifying
    @Query("DELETE FROM CardReview cr WHERE cr.cardId IN :cardIds")
    int deleteByCardIdIn(@Param("cardIds") Collection<Long> cardIds);
}
