package io.github.hackermanme.flashapi.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class SpringSecurityEvaluator extends SecurityEvaluator {

    @Override
    protected Object getCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        if (auth.getPrincipal() instanceof String s && "anonymousUser".equals(s)) {
            return null;
        }
        return auth.getPrincipal();
    }

    @Override
    protected Collection<String> getCurrentAuthorities() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return Set.of();
        }
        return auth.getAuthorities().stream()
                .map(ga -> ga.getAuthority())
                .collect(Collectors.toUnmodifiableSet());
    }
}
