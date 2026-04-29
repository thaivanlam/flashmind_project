package com.flashmind.repository;

import com.flashmind.entity.StudySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudySessionRepository extends JpaRepository<StudySession, Long> {
    Optional<StudySession> findByUserIdAndSessionDate(Long userId, LocalDate date);
    List<StudySession> findByUserIdAndSessionDateBetweenOrderBySessionDateAsc(
        Long userId, LocalDate start, LocalDate end);
}
