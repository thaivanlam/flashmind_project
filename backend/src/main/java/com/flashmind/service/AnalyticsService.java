package com.flashmind.service;

import com.flashmind.dto.response.AnalyticsResponse;
import com.flashmind.entity.StudySession;
import com.flashmind.repository.CardReviewRepository;
import com.flashmind.repository.StudySessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final int MASTERY_THRESHOLD = 5;

    private final StudySessionRepository sessionRepository;
    private final CardReviewRepository cardReviewRepository;

    public AnalyticsResponse getUserAnalytics(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDate thirtyDaysAgo = today.minusDays(29);

        List<StudySession> sessions = sessionRepository
            .findByUserIdAndSessionDateBetweenOrderBySessionDateAsc(userId, thirtyDaysAgo, today);

        Map<LocalDate, StudySession> sessionMap = sessions.stream()
            .collect(Collectors.toMap(StudySession::getSessionDate, s -> s));

        // Build last 30 days stats (fill gaps with zero)
        List<AnalyticsResponse.DailyStat> last30Days = thirtyDaysAgo.datesUntil(today.plusDays(1))
            .map(date -> {
                StudySession s = sessionMap.get(date);
                return AnalyticsResponse.DailyStat.builder()
                    .date(date)
                    .cardsReviewed(s != null ? s.getCardsReviewed() : 0)
                    .correctCount(s != null ? s.getCorrectCount() : 0)
                    .build();
            })
            .toList();

        long totalReviewed = sessions.stream()
            .mapToLong(StudySession::getCardsReviewed)
            .sum();

        long mastered = cardReviewRepository
            .countByUserIdAndRepetitionCountGreaterThanEqual(userId, MASTERY_THRESHOLD);

        int streak = calculateStreak(sessionMap, today);

        return AnalyticsResponse.builder()
            .currentStreak(streak)
            .totalCardsReviewed(totalReviewed)
            .masteredCards(mastered)
            .last30Days(last30Days)
            .build();
    }

    private int calculateStreak(Map<LocalDate, StudySession> sessions, LocalDate today) {
        int streak = 0;
        LocalDate current = today;
        // Nếu hôm nay chưa học, vẫn cho phép streak tính từ hôm qua
        if (!sessions.containsKey(current)) {
            current = current.minusDays(1);
        }
        while (sessions.containsKey(current) && sessions.get(current).getCardsReviewed() > 0) {
            streak++;
            current = current.minusDays(1);
        }
        return streak;
    }
}
