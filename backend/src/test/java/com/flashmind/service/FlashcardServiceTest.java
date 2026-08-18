package com.flashmind.service;

import com.flashmind.entity.Flashcard;
import com.flashmind.exception.ForbiddenException;
import com.flashmind.repository.CardReviewRepository;
import com.flashmind.repository.FlashcardRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FlashcardService - Cascade delete")
class FlashcardServiceTest {

    @Mock
    private FlashcardRepository flashcardRepository;

    @Mock
    private CardReviewRepository cardReviewRepository;

    @Mock
    private DeckService deckService;

    @InjectMocks
    private FlashcardService flashcardService;

    private static final Long CARD_ID = 1L;
    private static final Long DECK_ID = 10L;
    private static final Long USER_ID = 100L;

    @Test
    @DisplayName("Deleting a card also deletes that card's review")
    void deleteCardAlsoDeletesItsReview() {
        Flashcard card = Flashcard.builder()
                .id(CARD_ID).deckId(DECK_ID).front("F").back("B").build();
        when(flashcardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));

        flashcardService.deleteCard(CARD_ID, USER_ID);

        InOrder order = inOrder(cardReviewRepository, flashcardRepository);
        order.verify(cardReviewRepository).deleteByCardId(CARD_ID);
        order.verify(flashcardRepository).delete(card);
        verify(deckService).updateCardCount(DECK_ID);
    }

    @Test
    @DisplayName("Deleting another user's card: throws ForbiddenException and deletes no review")
    void cannotDeleteCardOwnedByAnotherUser() {
        Flashcard card = Flashcard.builder()
                .id(CARD_ID).deckId(DECK_ID).front("F").back("B").build();
        when(flashcardRepository.findById(CARD_ID)).thenReturn(Optional.of(card));
        doThrow(new ForbiddenException("You do not have access to this deck"))
                .when(deckService).findDeckOwnedBy(DECK_ID, USER_ID);

        assertThatThrownBy(() -> flashcardService.deleteCard(CARD_ID, USER_ID))
                .isInstanceOf(ForbiddenException.class);

        verify(cardReviewRepository, never()).deleteByCardId(anyLong());
        verify(flashcardRepository, never()).delete(any(Flashcard.class));
    }
}
