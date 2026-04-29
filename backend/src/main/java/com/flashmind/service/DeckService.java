package com.flashmind.service;

import com.flashmind.dto.request.DeckRequest;
import com.flashmind.dto.response.DeckResponse;
import com.flashmind.entity.Deck;
import com.flashmind.exception.BusinessException;
import com.flashmind.exception.ResourceNotFoundException;
import com.flashmind.repository.DeckRepository;
import com.flashmind.repository.FlashcardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DeckService {

    private final DeckRepository deckRepository;
    private final FlashcardRepository flashcardRepository;

    public List<DeckResponse> listUserDecks(Long userId) {
        return deckRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(DeckResponse::from)
            .toList();
    }

    public DeckResponse getDeck(Long deckId, Long userId) {
        Deck deck = findDeckOwnedBy(deckId, userId);
        return DeckResponse.from(deck);
    }

    @Transactional
    public DeckResponse createDeck(DeckRequest req, Long userId) {
        Deck deck = Deck.builder()
            .userId(userId)
            .title(req.getTitle())
            .description(req.getDescription())
            .language(req.getLanguage())
            .build();
        return DeckResponse.from(deckRepository.save(deck));
    }

    @Transactional
    public DeckResponse updateDeck(Long deckId, DeckRequest req, Long userId) {
        Deck deck = findDeckOwnedBy(deckId, userId);
        deck.setTitle(req.getTitle());
        deck.setDescription(req.getDescription());
        deck.setLanguage(req.getLanguage());
        return DeckResponse.from(deckRepository.save(deck));
    }

    @Transactional
    public void deleteDeck(Long deckId, Long userId) {
        Deck deck = findDeckOwnedBy(deckId, userId);
        flashcardRepository.deleteByDeckId(deck.getId());
        deckRepository.delete(deck);
    }

    public Deck findDeckOwnedBy(Long deckId, Long userId) {
        Deck deck = deckRepository.findById(deckId)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy deck"));
        if (!deck.getUserId().equals(userId)) {
            throw new BusinessException("Không có quyền truy cập deck này");
        }
        return deck;
    }

    @Transactional
    public void updateCardCount(Long deckId) {
        Deck deck = deckRepository.findById(deckId)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy deck"));
        deck.setCardCount((int) flashcardRepository.countByDeckId(deckId));
        deckRepository.save(deck);
    }
}
