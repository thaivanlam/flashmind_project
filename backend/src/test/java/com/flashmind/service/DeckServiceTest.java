package com.flashmind.service;

import com.flashmind.entity.Deck;
import com.flashmind.exception.ForbiddenException;
import com.flashmind.repository.CardReviewRepository;
import com.flashmind.repository.DeckRepository;
import com.flashmind.repository.FlashcardRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeckService - Cascade delete")
class DeckServiceTest {

    @Mock
    private DeckRepository deckRepository;

    @Mock
    private FlashcardRepository flashcardRepository;

    @Mock
    private CardReviewRepository cardReviewRepository;

    @InjectMocks
    private DeckService deckService;

    private static final Long DECK_ID = 10L;
    private static final Long USER_ID = 100L;

    private Deck ownedDeck() {
        return Deck.builder().id(DECK_ID).userId(USER_ID).title("Deck").build();
    }

    @Test
    @DisplayName("Xóa deck cũng xóa review của mọi thẻ trong deck (không để lại bản ghi mồ côi)")
    void deleteDeckAlsoDeletesCardReviews() {
        Deck deck = ownedDeck();
        when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(deck));
        when(flashcardRepository.findIdsByDeckId(DECK_ID)).thenReturn(List.of(1L, 2L));

        deckService.deleteDeck(DECK_ID, USER_ID);

        // Review phải bị xóa TRƯỚC khi thẻ biến mất, nếu không id thẻ không còn để dọn
        InOrder order = inOrder(cardReviewRepository, flashcardRepository, deckRepository);
        order.verify(cardReviewRepository).deleteByCardIdIn(List.of(1L, 2L));
        order.verify(flashcardRepository).deleteByDeckId(DECK_ID);
        order.verify(deckRepository).delete(deck);
    }

    @Test
    @DisplayName("Deck rỗng: không gọi xóa review thừa")
    void deleteEmptyDeckSkipsReviewCleanup() {
        when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(ownedDeck()));
        when(flashcardRepository.findIdsByDeckId(DECK_ID)).thenReturn(List.of());

        deckService.deleteDeck(DECK_ID, USER_ID);

        verify(cardReviewRepository, never()).deleteByCardIdIn(any());
        verify(flashcardRepository).deleteByDeckId(DECK_ID);
    }

    @Test
    @DisplayName("Xóa deck của user khác: ném ForbiddenException, không xóa gì")
    void cannotDeleteDeckOwnedByAnotherUser() {
        Deck deck = Deck.builder().id(DECK_ID).userId(999L).title("Deck").build();
        when(deckRepository.findById(DECK_ID)).thenReturn(Optional.of(deck));

        assertThatThrownBy(() -> deckService.deleteDeck(DECK_ID, USER_ID))
                .isInstanceOf(ForbiddenException.class);

        verify(cardReviewRepository, never()).deleteByCardIdIn(any());
        verify(flashcardRepository, never()).deleteByDeckId(anyLong());
        verify(deckRepository, never()).delete(any(Deck.class));
    }
}
