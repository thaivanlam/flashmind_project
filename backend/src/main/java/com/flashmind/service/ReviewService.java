package com.flashmind.service;

import com.flashmind.dto.response.CardReviewResponse;
import com.flashmind.dto.response.ReviewSubmitResponse;
import com.flashmind.entity.CardReview;
import com.flashmind.entity.Flashcard;
import com.flashmind.entity.StudySession;
import com.flashmind.repository.CardReviewRepository;
import com.flashmind.repository.FlashcardRepository;
import com.flashmind.repository.StudySessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private static final int MASTERY_THRESHOLD = 5;

    private final CardReviewRepository reviewRepository;
    private final FlashcardRepository flashcardRepository;
    private final StudySessionRepository sessionRepository;
    private final FlashcardService flashcardService;

    public List<CardReviewResponse> getTodayReviews(Long userId) {
        List<CardReview> dueReviews = reviewRepository.findDueReviews(userId, LocalDate.now());
        if (dueReviews.isEmpty()) return List.of();

        List<Long> cardIds = dueReviews.stream().map(CardReview::getCardId).toList();
        Map<Long, Flashcard> cards = flashcardRepository.findAllById(cardIds)
            .stream()
            .collect(Collectors.toMap(Flashcard::getId, c -> c));

        // The query already excludes orphaned reviews; filter again against the loaded
        // card map so the client can never receive `card: null`.
        return dueReviews.stream()
            .filter(r -> cards.containsKey(r.getCardId()))
            .map(r -> CardReviewResponse.from(r, cards.get(r.getCardId())))
            .toList();
    }

    @Transactional
    public ReviewSubmitResponse submitReview(Long cardId, Long userId, int quality) {
        // Check card ownership (via its deck) before recording the review:
        // card does not exist -> 404, card belongs to another user -> 403
        flashcardService.findCardOwnedBy(cardId, userId);

        CardReview review = reviewRepository.findByCardIdAndUserId(cardId, userId)
            .orElseGet(() -> new CardReview(cardId, userId));

        applySpacedRepetition(review, quality);
        review.setLastReviewedAt(LocalDateTime.now());
        reviewRepository.save(review);

        recordStudySession(userId, quality >= 3);

        return ReviewSubmitResponse.builder()
            .nextReviewDate(review.getNextReviewDate())
            .interval(review.getInterval())
            .isMastered(review.getRepetitionCount() >= MASTERY_THRESHOLD)
            .build();
    }

    /**
     * SM-2 (SuperMemo 2) algorithm.
     * quality: 0 (totally forgot) to 5 (perfect recall)
     * Reference: https://en.wikipedia.org/wiki/SuperMemo#Description_of_SM-2_algorithm
     */
    private void applySpacedRepetition(CardReview r, int quality) {
        if (quality >= 3) {
            // Correct answer → increase the interval
            int newInterval;
            if (r.getRepetitionCount() == 0) {
                newInterval = 1;
            } else if (r.getRepetitionCount() == 1) {
                newInterval = 6;
            } else {
                newInterval = (int) Math.round(r.getInterval() * r.getEasinessFactor());
            }
            r.setInterval(newInterval);
            r.setRepetitionCount(r.getRepetitionCount() + 1);
        } else {
            // Wrong answer → reset the interval to 1
            r.setRepetitionCount(0);
            r.setInterval(1);
        }

        // Update the easiness factor
        double ef = r.getEasinessFactor()
            + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
        r.setEasinessFactor(Math.max(1.3, ef));

        r.setNextReviewDate(LocalDate.now().plusDays(r.getInterval()));
    }

    private void recordStudySession(Long userId, boolean correct) {
        LocalDate today = LocalDate.now();
        StudySession session = sessionRepository
            .findByUserIdAndSessionDate(userId, today)
            .orElseGet(() -> StudySession.builder()
                .userId(userId)
                .sessionDate(today)
                .cardsReviewed(0)
                .correctCount(0)
                .build());

        session.setCardsReviewed(session.getCardsReviewed() + 1);
        if (correct) {
            session.setCorrectCount(session.getCorrectCount() + 1);
        }
        sessionRepository.save(session);
    }
}
