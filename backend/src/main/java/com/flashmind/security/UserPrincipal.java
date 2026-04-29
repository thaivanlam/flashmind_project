package com.flashmind.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserPrincipal {
    private final Long userId;
    private final String email;
}
