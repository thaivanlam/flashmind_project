package com.flashmind.controller;

import com.flashmind.dto.request.DeckRequest;
import com.flashmind.dto.response.DeckResponse;
import com.flashmind.security.AuthHelper;
import com.flashmind.service.DeckService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/decks")
@RequiredArgsConstructor
public class DeckController {

    private final DeckService deckService;

    @GetMapping
    public ResponseEntity<List<DeckResponse>> list() {
        return ResponseEntity.ok(deckService.listUserDecks(AuthHelper.getCurrentUserId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeckResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(deckService.getDeck(id, AuthHelper.getCurrentUserId()));
    }

    @PostMapping
    public ResponseEntity<DeckResponse> create(@Valid @RequestBody DeckRequest req) {
        return ResponseEntity.ok(deckService.createDeck(req, AuthHelper.getCurrentUserId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DeckResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody DeckRequest req) {
        return ResponseEntity.ok(deckService.updateDeck(id, req, AuthHelper.getCurrentUserId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deckService.deleteDeck(id, AuthHelper.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
