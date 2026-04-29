package com.flashmind.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewSubmitRequest {
    @NotNull
    @Min(0)
    @Max(5)
    private Integer quality;
}
