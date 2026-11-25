package com.example.cube.security;

import com.example.cube.common.exception.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** The authenticated caller, or 401 if there is none. */
    public static CurrentUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CurrentUser)) {
            throw ApiException.unauthorized("Authentication required");
        }
        return (CurrentUser) auth.getPrincipal();
    }

    /** Throws 403 unless the caller holds the permission. */
    public static CurrentUser require(String permission) {
        CurrentUser user = currentUser();
        if (!user.has(permission)) {
            throw ApiException.forbidden("You do not have permission to perform this action (" + permission + ")");
        }
        return user;
    }

    /** Throws 403 unless the caller holds at least one of the permissions. */
    public static CurrentUser requireAny(String... permissions) {
        CurrentUser user = currentUser();
        for (String permission : permissions) {
            if (user.has(permission)) {
                return user;
            }
        }
        throw ApiException.forbidden("You do not have permission to perform this action");
    }
}
