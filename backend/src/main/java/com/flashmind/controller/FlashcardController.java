package com.flashmind.controller;

import com.flashmind.dto.request.FlashcardRequest;
import com.flashmind.dto.response.FlashcardResponse;
import com.flashmind.security.AuthHelper;
import com.flashmind.service.AiGenerationService;
import com.flashmind.service.FlashcardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class FlashcardController {

    private final FlashcardService flashcardService;
    private final AiGenerationService aiGenerationService;

    @GetMapping("/api/decks/{deckId}/cards")
    public ResponseEntity<List<FlashcardResponse>> listByDeck(@PathVariable Long deckId) {
        return ResponseEntity.ok(
            flashcardService.listByDeck(deckId, AuthHelper.getCurrentUserId())
        );
    }

    @PostMapping("/api/decks/{deckId}/cards")
    public ResponseEntity<FlashcardResponse> createCard(@PathVariable Long deckId,
                                                        @Valid @RequestBody FlashcardRequest req) {
        return ResponseEntity.ok(
            flashcardService.createCard(deckId, req, AuthHelper.getCurrentUserId())
        );
    }

    @PutMapping("/api/cards/{cardId}")
    public ResponseEntity<FlashcardResponse> update(@PathVariable Long cardId,
                                                    @Valid @RequestBody FlashcardRequest req) {
        return ResponseEntity.ok(
            flashcardService.updateCard(cardId, req, AuthHelper.getCurrentUserId())
        );
    }

    @DeleteMapping("/api/cards/{cardId}")
    public ResponseEntity<Void> delete(@PathVariable Long cardId) {
        flashcardService.deleteCard(cardId, AuthHelper.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/api/decks/{deckId}/generate-ai", consumes = "multipart/form-data")
    public ResponseEntity<List<FlashcardResponse>> generateFromAi(
            @PathVariable Long deckId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "count", defaultValue = "10") int count) {
        return ResponseEntity.ok(
            aiGenerationService.generateFromFile(deckId, AuthHelper.getCurrentUserId(), file, count)
        );
    }
}
