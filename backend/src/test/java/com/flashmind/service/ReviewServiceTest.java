package com.flashmind.service;

import com.flashmind.entity.CardReview;
import com.flashmind.entity.StudySession;
import com.flashmind.exception.ForbiddenException;
import com.flashmind.exception.ResourceNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Mock
    private FlashcardService flashcardService;

    @InjectMocks
    private ReviewService reviewService;

    private static final Long CARD_ID = 1L;
    private static final Long USER_ID = 100L;

    @BeforeEach
    void setUp() {
        lenient().when(sessionRepository.findByUserIdAndSessionDate(any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(sessionRepository.save(any(StudySession.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("First review with quality=4: interval=1, EF unchanged")
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
    @DisplayName("Second review with quality=4: interval=6")
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
    @DisplayName("Wrong answer (quality<3): interval resets to 1")
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
    @DisplayName("EF never drops below 1.3 (even at quality=0)")
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
    @DisplayName("Third and later reviews: interval = prevInterval * EF")
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
    @DisplayName("Card is marked mastered after 5+ consecutive correct answers")
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
    @DisplayName("Submitting a review also updates the study session counter")
    void submitReviewUpdatesStudySession() {
        when(reviewRepository.findByCardIdAndUserId(CARD_ID, USER_ID))
                .thenReturn(Optional.empty());
        when(reviewRepository.save(any(CardReview.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        reviewService.submitReview(CARD_ID, USER_ID, 5);

        verify(sessionRepository).save(any(StudySession.class));
    }

    @Test
    @DisplayName("Cannot review another user's card: throws ForbiddenException and writes nothing")
    void cannotReviewCardOwnedByAnotherUser() {
        when(flashcardService.findCardOwnedBy(CARD_ID, USER_ID))
                .thenThrow(new ForbiddenException("You do not have access to this deck"));

        assertThatThrownBy(() -> reviewService.submitReview(CARD_ID, USER_ID, 5))
                .isInstanceOf(ForbiddenException.class);

        verify(reviewRepository, never()).save(any(CardReview.class));
        verify(sessionRepository, never()).save(any(StudySession.class));
    }

    @Test
    @DisplayName("Reviewing a nonexistent card: throws ResourceNotFoundException")
    void cannotReviewMissingCard() {
        when(flashcardService.findCardOwnedBy(CARD_ID, USER_ID))
                .thenThrow(new ResourceNotFoundException("Card not found"));

        assertThatThrownBy(() -> reviewService.submitReview(CARD_ID, USER_ID, 5))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(reviewRepository, never()).save(any(CardReview.class));
    }
}
