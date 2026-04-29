package com.flashmind.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashmind.dto.response.FlashcardResponse;
import com.flashmind.entity.CardReview;
import com.flashmind.entity.Flashcard;
import com.flashmind.exception.BusinessException;
import com.flashmind.repository.CardReviewRepository;
import com.flashmind.repository.FlashcardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiGenerationService {

    private final FileParsingService fileParsingService;
    private final FlashcardRepository flashcardRepository;
    private final CardReviewRepository cardReviewRepository;
    private final DeckService deckService;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.api-url}")
    private String apiUrl;

    @Value("${openai.model}")
    private String model;

    @Transactional
    public List<FlashcardResponse> generateFromFile(Long deckId, Long userId,
            org.springframework.web.multipart.MultipartFile file,
            int count) {
        deckService.findDeckOwnedBy(deckId, userId);

        String content = fileParsingService.extractText(file);
        String prompt = buildPrompt(content, count);
        String aiResponse = callOpenAi(prompt);
        List<Flashcard> generated = parseAndSaveCards(deckId, userId, aiResponse);

        deckService.updateCardCount(deckId);

        return generated.stream().map(FlashcardResponse::from).toList();
    }

    private String buildPrompt(String content, int count) {
        return """
                You are an expert tutor creating flashcards for spaced repetition learning.
                Extract %d key concepts from the text below and create flashcards.

                Rules:
                - Front: a clear question or term
                - Back: a concise but complete answer (max 2-3 sentences)
                - Hint: an optional keyword or category (can be null)
                - Focus on testable, factual content
                - Avoid duplicates and overly broad questions

                IMPORTANT: Return a JSON object with EXACTLY this structure:
                {
                  "flashcards": [
                    {"front": "...", "back": "...", "hint": "..."}
                  ]
                }

                Text:
                %s
                """.formatted(count, content);
    }

    private String callOpenAi(String prompt) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", 0.3,
                    "response_format", Map.of("type", "json_object"));

            String response = webClientBuilder.build()
                    .post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("OpenAI call failed", e);
            throw new BusinessException("Không thể tạo flashcard từ AI: " + e.getMessage());
        }
    }

    private List<Flashcard> parseAndSaveCards(Long deckId, Long userId, String aiResponse) {
        List<Flashcard> savedCards = new ArrayList<>();
        try {
            log.info("AI response: {}", aiResponse); // Thêm log để debug
            JsonNode root = objectMapper.readTree(aiResponse);

            // Tìm array flashcards trong response
            JsonNode cardsArray = null;
            if (root.isArray()) {
                cardsArray = root;
            } else if (root.has("flashcards") && root.get("flashcards").isArray()) {
                cardsArray = root.get("flashcards");
            } else if (root.has("cards") && root.get("cards").isArray()) {
                cardsArray = root.get("cards");
            } else {
                // Tìm field array đầu tiên trong object
                var fields = root.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    if (entry.getValue().isArray()) {
                        cardsArray = entry.getValue();
                        break;
                    }
                }
            }

            if (cardsArray == null || !cardsArray.isArray() || cardsArray.size() == 0) {
                log.error("Cannot find flashcards array in response: {}", aiResponse);
                throw new BusinessException("AI trả về định dạng không hợp lệ");
            }

            for (JsonNode node : cardsArray) {
                String front = node.path("front").asText("");
                String back = node.path("back").asText("");
                String hint = node.path("hint").isNull() ? null : node.path("hint").asText(null);

                if (front.isBlank() || back.isBlank())
                    continue;

                Flashcard card = Flashcard.builder()
                        .deckId(deckId)
                        .front(front)
                        .back(back)
                        .hint(hint)
                        .isAiGenerated(true)
                        .build();
                card = flashcardRepository.save(card);

                cardReviewRepository.save(
                        CardReview.builder()
                                .cardId(card.getId())
                                .userId(userId)
                                .nextReviewDate(LocalDate.now())
                                .build());

                savedCards.add(card);
            }

            if (savedCards.isEmpty()) {
                throw new BusinessException("AI không sinh được flashcard nào");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse AI response", e);
            throw new BusinessException("Không thể parse phản hồi từ AI: " + e.getMessage());
        }
        return savedCards;
    }
}
