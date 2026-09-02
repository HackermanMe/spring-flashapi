package io.github.hackermanme.flashapi.security;

import org.springframework.security.core.Authentication;

@FunctionalInterface
public interface FlashPrincipalResolver {
    Object resolve(Authentication auth);
}
