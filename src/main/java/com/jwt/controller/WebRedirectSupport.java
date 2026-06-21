package com.jwt.controller;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

final class WebRedirectSupport {
    private WebRedirectSupport() {
    }

    static String redirectAfter(RedirectAttributes redirectAttributes,
                                String redirect,
                                String successMessage,
                                Runnable action) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("message", successMessage);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return redirect;
    }

    static String redirectWithError(RedirectAttributes redirectAttributes,
                                    String fallbackRedirect,
                                    String errorPrefix,
                                    RedirectAction action) {
        try {
            return action.run();
        } catch (IllegalArgumentException e) {
            String message = errorPrefix == null ? e.getMessage() : errorPrefix + e.getMessage();
            redirectAttributes.addFlashAttribute("error", message);
            return fallbackRedirect;
        }
    }

    @FunctionalInterface
    interface RedirectAction {
        String run();
    }
}
