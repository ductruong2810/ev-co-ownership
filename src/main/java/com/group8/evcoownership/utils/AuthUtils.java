package com.group8.evcoownership.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthUtils {

    private AuthUtils() {
    }

    public static String getCurrentUserEmail(Authentication authentication) {
        if (authentication != null) {
            return authentication.getName();
        }
        Authentication ctxAuth = SecurityContextHolder.getContext().getAuthentication();
        return ctxAuth != null ? ctxAuth.getName() : null;
    }
}