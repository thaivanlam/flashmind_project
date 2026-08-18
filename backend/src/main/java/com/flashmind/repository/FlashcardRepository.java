package com.flashmind.repository;

import com.flashmind.entity.Flashcard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {
    List<Flashcard> findByDeckIdOrderByCreatedAtAsc(Long deckId);
    long countByDeckId(Long deckId);
    void deleteByDeckId(Long deckId);

    /** Lấy id các thẻ trong deck — dùng để dọn review trước khi xóa deck. */
    @Query("SELECT f.id FROM Flashcard f WHERE f.deckId = :deckId")
    List<Long> findIdsByDeckId(@Param("deckId") Long deckId);
}
