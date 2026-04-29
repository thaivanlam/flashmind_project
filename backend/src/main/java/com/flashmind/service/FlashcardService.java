package com.flashmind.service;

import com.flashmind.dto.request.FlashcardRequest;
import com.flashmind.dto.response.FlashcardResponse;
import com.flashmind.entity.CardReview;
import com.flashmind.entity.Flashcard;
import com.flashmind.exception.BusinessException;
import com.flashmind.exception.ResourceNotFoundException;
import com.flashmind.repository.CardReviewRepository;
import com.flashmind.repository.FlashcardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FlashcardService {

    private final FlashcardRepository flashcardRepository;
    private final CardReviewRepository cardReviewRepository;
    private final DeckService deckService;

    public List<FlashcardResponse> listByDeck(Long deckId, Long userId) {
        deckService.findDeckOwnedBy(deckId, userId);
        return flashcardRepository.findByDeckIdOrderByCreatedAtAsc(deckId)
            .stream()
            .map(FlashcardResponse::from)
            .toList();
    }

    @Transactional
    public FlashcardResponse createCard(Long deckId, FlashcardRequest req, Long userId) {
        deckService.findDeckOwnedBy(deckId, userId);

        Flashcard card = Flashcard.builder()
            .deckId(deckId)
            .front(req.getFront())
            .back(req.getBack())
            .hint(req.getHint())
            .isAiGenerated(false)
            .build();
        card = flashcardRepository.save(card);

        // Tạo review record với nextReviewDate = hôm nay
        cardReviewRepository.save(
            CardReview.builder()
                .cardId(card.getId())
                .userId(userId)
                .nextReviewDate(LocalDate.now())
                .build()
        );

        deckService.updateCardCount(deckId);
        return FlashcardResponse.from(card);
    }

    @Transactional
    public FlashcardResponse updateCard(Long cardId, FlashcardRequest req, Long userId) {
        Flashcard card = findCardOwnedBy(cardId, userId);
        card.setFront(req.getFront());
        card.setBack(req.getBack());
        card.setHint(req.getHint());
        return FlashcardResponse.from(flashcardRepository.save(card));
    }

    @Transactional
    public void deleteCard(Long cardId, Long userId) {
        Flashcard card = findCardOwnedBy(cardId, userId);
        flashcardRepository.delete(card);
        deckService.updateCardCount(card.getDeckId());
    }

    private Flashcard findCardOwnedBy(Long cardId, Long userId) {
        Flashcard card = flashcardRepository.findById(cardId)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thẻ"));
        // Verify ownership through deck
        deckService.findDeckOwnedBy(card.getDeckId(), userId);
        return card;
    }
}
