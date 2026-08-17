package com.flashmind.service;

import com.flashmind.entity.CardReview;
import com.flashmind.entity.Flashcard;
import com.flashmind.entity.StudySession;
import com.flashmind.repository.CardReviewRepository;
import com.flashmind.repository.FlashcardRepository;
import com.flashmind.repository.StudySessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService - SM-2 Algorithm Tests")
class ReviewServiceTest {

    @Mock
    private CardReviewRepository reviewRepository;

    @Mock
    private FlashcardRepository flashcardRepository;

    @Mock
    private StudySessionRepository sessionRepository;

    @InjectMocks
    private ReviewService reviewService;

    private static final Long CARD_ID = 1L;
    private static final Long USER_ID = 100L;

    @BeforeEach
    void setUp() {
        lenient().when(flashcardRepository.existsById(CARD_ID)).thenReturn(true);
        lenient().when(sessionRepository.findByUserIdAndSessionDate(any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(sessionRepository.save(any(StudySession.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Lần ôn đầu tiên với quality=4: interval=1, EF không đổi")
    void firstReviewWithGoodQuality() {
        when(reviewRepository.findByCardIdAndUserId(CARD_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(reviewRepository.save(any(CardReview.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = reviewService.submitReview(CARD_ID, USER_ID, 4);

        assertThat(result.getInterval()).isEqualTo(1);
        assertThat(result.getNextReviewDate()).isEqualTo(LocalDate.now().plusDays(1));
        assertThat(result.getIsMastered()).isFalse();
    }

    @Test
    @DisplayName("Lần ôn thứ 2 với quality=4: interval=6")
    void secondReviewSetsIntervalToSix() {
        CardReview existing = CardReview.builder()
                .cardId(CARD_ID)
                .userId(USER_ID)
                .interval(1)
                .easinessFactor(2.5)
                .repetitionCount(1)
                .nextReviewDate(LocalDate.now())
                .build();

        when(reviewRepository.findByCardIdAndUserId(CARD_ID, USER_ID))
                .thenReturn(Optional.of(existing));
        when(reviewRepository.save(any(CardReview.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = reviewService.submitReview(CARD_ID, USER_ID, 4);

        assertThat(result.getInterval()).isEqualTo(6);
        assertThat(result.getNextReviewDate()).isEqualTo(LocalDate.now().plusDays(6));
    }

    @Test
    @DisplayName("Trả lời sai (quality<3): reset interval về 1")
    void incorrectAnswerResetsInterval() {
        CardReview existing = CardReview.builder()
                .cardId(CARD_ID)
                .userId(USER_ID)
                .interval(15)
                .easinessFactor(2.5)
                .repetitionCount(3)
                .nextReviewDate(LocalDate.now())
                .build();

        when(reviewRepository.findByCardIdAndUserId(CARD_ID, USER_ID))
                .thenReturn(Optional.of(existing));
        when(reviewRepository.save(any(CardReview.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = reviewService.submitReview(CARD_ID, USER_ID, 1);

        assertThat(result.getInterval()).isEqualTo(1);
        assertThat(result.getNextReviewDate()).isEqualTo(LocalDate.now().plusDays(1));
    }

    @Test
    @DisplayName("EF không bao giờ xuống dưới 1.3 (kể cả khi quality=0)")
    void easinessFactorNeverDropsBelowMin() {
        CardReview existing = CardReview.builder()
                .cardId(CARD_ID)
                .userId(USER_ID)
                .interval(1)
                .easinessFactor(1.3)
                .repetitionCount(0)
                .nextReviewDate(LocalDate.now())
                .build();

        when(reviewRepository.findByCardIdAndUserId(CARD_ID, USER_ID))
                .thenReturn(Optional.of(existing));
        when(reviewRepository.save(any(CardReview.class)))
                .thenAnswer(inv -> {
                    CardReview saved = inv.getArgument(0);
                    assertThat(saved.getEasinessFactor()).isGreaterThanOrEqualTo(1.3);
                    return saved;
                });

        reviewService.submitReview(CARD_ID, USER_ID, 0);

        verify(reviewRepository).save(any(CardReview.class));
    }

    @Test
    @DisplayName("Lần ôn thứ 3+: interval = prevInterval * EF")
    void thirdReviewMultipliesByEf() {
        CardReview existing = CardReview.builder()
                .cardId(CARD_ID)
                .userId(USER_ID)
                .interval(6)
                .easinessFactor(2.5)
                .repetitionCount(2)
                .nextReviewDate(LocalDate.now())
                .build();

        when(reviewRepository.findByCardIdAndUserId(CARD_ID, USER_ID))
                .thenReturn(Optional.of(existing));
        when(reviewRepository.save(any(CardReview.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = reviewService.submitReview(CARD_ID, USER_ID, 4);

        // 6 * 2.5 = 15
        assertThat(result.getInterval()).isEqualTo(15);
    }

    @Test
    @DisplayName("Card được đánh dấu mastered sau 5+ lần đúng liên tiếp")
    void cardMarkedMasteredAfterFiveCorrect() {
        CardReview existing = CardReview.builder()
                .cardId(CARD_ID)
                .userId(USER_ID)
                .interval(38)
                .easinessFactor(2.5)
                .repetitionCount(4)
                .nextReviewDate(LocalDate.now())
                .build();

        when(reviewRepository.findByCardIdAndUserId(CARD_ID, USER_ID))
                .thenReturn(Optional.of(existing));
        when(reviewRepository.save(any(CardReview.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = reviewService.submitReview(CARD_ID, USER_ID, 5);

        assertThat(result.getIsMastered()).isTrue();
    }

    @Test
    @DisplayName("Submit review cũng update study session counter")
    void submitReviewUpdatesStudySession() {
        when(reviewRepository.findByCardIdAndUserId(CARD_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(reviewRepository.save(any(CardReview.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        reviewService.submitReview(CARD_ID, USER_ID, 5);

        verify(sessionRepository).save(any(StudySession.class));
    }
}
