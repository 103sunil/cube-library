package com.example.cube.security;

import java.io.Serializable;
import java.security.Principal;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;

/** The authenticated caller, rebuilt from the database on every request. */
public class CurrentUser implements Principal, Serializable {

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final String groupCode;
    private final Set<String> permissions;
    private final String tokenId;
    private final Date tokenExpiry;

    public CurrentUser(Long userId, String username, String groupCode, Set<String> permissions,
                       String tokenId, Date tokenExpiry) {
        this.userId = userId;
        this.username = username;
        this.groupCode = groupCode;
        this.permissions = Collections.unmodifiableSet(new LinkedHashSet<>(permissions));
        this.tokenId = tokenId;
        this.tokenExpiry = tokenExpiry == null ? null : new Date(tokenExpiry.getTime());
    }

    public boolean has(String permission) {
        return permissions.contains(permission);
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public String getName() {
        return username;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public String getTokenId() {
        return tokenId;
    }

    public Date getTokenExpiry() {
        return tokenExpiry == null ? null : new Date(tokenExpiry.getTime());
    }

    @Override
    public String toString() {
        return username;
    }
}
