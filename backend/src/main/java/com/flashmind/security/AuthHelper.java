package com.flashmind.security;

import org.springframework.security.core.context.SecurityContextHolder;

public class AuthHelper {

    private AuthHelper() {}

    public static Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserPrincipal up) {
            return up.getUserId();
        }
        throw new IllegalStateException("No user found in the security context");
    }
}
