package com.flashmind.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FlashcardRequest {
    @NotBlank
    private String front;

    @NotBlank
    private String back;

    private String hint;
}
