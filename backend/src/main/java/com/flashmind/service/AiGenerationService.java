package com.flashmind.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.flashmind.dto.response.FlashcardResponse;
import com.flashmind.entity.CardReview;
import com.flashmind.entity.Flashcard;
import com.flashmind.exception.BusinessException;
import com.flashmind.repository.CardReviewRepository;
import com.flashmind.repository.FlashcardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiGenerationService {

    /**
     * Static instructions for the model. The per-request parts (card count, file
     * content) go in the user message so the system prompt stays identical between calls.
     */
    private static final String SYSTEM_PROMPT = """
            You are an expert tutor creating flashcards for spaced repetition learning.

            Rules:
            - Front: a clear question or term
            - Back: a concise but complete answer (max 2-3 sentences)
            - Hint: an optional keyword or category; use an empty string when no useful hint exists
            - Focus on testable, factual content
            - Avoid duplicates and overly broad questions
            """;

    private final FileParsingService fileParsingService;
    private final FlashcardRepository flashcardRepository;
    private final CardReviewRepository cardReviewRepository;
    private final DeckService deckService;
    private final AnthropicClient anthropicClient;

    @Value("${anthropic.model}")
    private String model;

    @Value("${anthropic.max-tokens}")
    private long maxTokens;

    /**
     * The JSON structure Claude is required to return. The schema is derived from these
     * records and enforced server-side (structured outputs), so the response shape never
     * has to be guessed at.
     */
    public record GeneratedCard(String front, String back, String hint) {
    }

    public record GeneratedCards(List<GeneratedCard> flashcards) {
    }

    @Transactional
    public List<FlashcardResponse> generateFromFile(Long deckId, Long userId, MultipartFile file, int count) {
        deckService.findDeckOwnedBy(deckId, userId);

        String content = fileParsingService.extractText(file);
        GeneratedCards generated = callClaude(content, count);
        List<Flashcard> savedCards = saveCards(deckId, userId, generated);

        deckService.updateCardCount(deckId);

        return savedCards.stream().map(FlashcardResponse::from).toList();
    }

    private GeneratedCards callClaude(String content, int count) {
        StructuredMessageCreateParams<GeneratedCards> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .system(SYSTEM_PROMPT)
                .outputConfig(GeneratedCards.class)
                .addUserMessage("""
                        Extract %d key concepts from the text below and create flashcards.

                        Text:
                        %s
                        """.formatted(count, content))
                .build();

        StructuredMessage<GeneratedCards> response;
        try {
            response = anthropicClient.messages().create(params);
        } catch (NotFoundException e) {
            log.error("Claude model not found: {}", model, e);
            throw new BusinessException("Invalid AI configuration, please contact an administrator");
        } catch (RateLimitException e) {
            log.warn("Claude API rate limit reached", e);
            throw new BusinessException("The AI is overloaded, please try again in a few minutes");
        } catch (AnthropicServiceException e) {
            log.error("Claude API returned an error, errorType={}", e.errorType().orElse(null), e);
            throw new BusinessException("Could not generate flashcards from the AI: " + e.getMessage());
        } catch (Exception e) {
            log.error("Claude API call failed", e);
            throw new BusinessException("Could not generate flashcards from the AI: " + e.getMessage());
        }

        // stopReason must be checked before reading the content: when the model declines
        // a request the response is still HTTP 200, but the content is empty.
        StopReason stopReason = response.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stopReason)) {
            log.warn("Claude declined to process the content: {}",
                    response.stopDetails().map(Object::toString).orElse("no reason given"));
            throw new BusinessException("The AI declined to process this file's content");
        }
        if (StopReason.MAX_TOKENS.equals(stopReason)) {
            log.warn("Claude response truncated at the max_tokens limit ({})", maxTokens);
            throw new BusinessException("The AI response was too long, please request fewer cards");
        }

        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(StructuredTextBlock::text)
                .findFirst()
                .orElseThrow(() -> new BusinessException("The AI returned an invalid format"));
    }

    private List<Flashcard> saveCards(Long deckId, Long userId, GeneratedCards generated) {
        List<GeneratedCard> cards = generated.flashcards();
        if (cards == null || cards.isEmpty()) {
            throw new BusinessException("The AI did not generate any flashcards");
        }

        List<Flashcard> savedCards = new ArrayList<>();
        for (GeneratedCard generatedCard : cards) {
            String front = generatedCard.front();
            String back = generatedCard.back();
            if (front == null || front.isBlank() || back == null || back.isBlank()) {
                continue;
            }
            // Existing convention preserved: a blank hint is stored as null
            String hint = (generatedCard.hint() == null || generatedCard.hint().isBlank())
                    ? null
                    : generatedCard.hint();

            Flashcard card = flashcardRepository.save(
                    Flashcard.builder()
                            .deckId(deckId)
                            .front(front)
                            .back(back)
                            .hint(hint)
                            .isAiGenerated(true)
                            .build());

            cardReviewRepository.save(
                    CardReview.builder()
                            .cardId(card.getId())
                            .userId(userId)
                            .nextReviewDate(LocalDate.now())
                            .build());

            savedCards.add(card);
        }

        if (savedCards.isEmpty()) {
            throw new BusinessException("The AI did not generate any flashcards");
        }
        return savedCards;
    }
}
