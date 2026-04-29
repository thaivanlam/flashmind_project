package com.flashmind.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeckRequest {
    @NotBlank
    private String title;
    private String description;
    private String language;
}
