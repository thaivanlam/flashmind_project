package com.flashmind.dto.response;

import com.flashmind.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private String fullName;
    private LocalDateTime createdAt;

    public static UserResponse from(User user) {
        return UserResponse.builder()
            .id(user.getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .createdAt(user.getCreatedAt())
            .build();
    }
}
