package com.example.cube.auth.service;

import com.example.cube.auth.entity.Group;
import com.example.cube.auth.entity.User;
import com.example.cube.security.CurrentUser;

import java.util.Date;
import java.util.Optional;
import java.util.Set;

public interface PermissionService {

    /**
     * Effective permission codes ("MODULE:ACCESS") for a user:
     * active group defaults with ALLOWED=Y, then active, unexpired user overrides
     * (ALLOWED=Y adds, ALLOWED=N removes). Inactive modules/access codes are ignored.
     */
    Set<String> effectivePermissions(Long userId);

    /** The user's group, if the membership and the group are both active. */
    Optional<Group> findActiveGroup(Long userId);

    CurrentUser buildCurrentUser(User user, String tokenId, Date tokenExpiry);

    /**
     * IDs of ACTIVE users who currently hold the permission ("MODULE:ACCESS"), using the same rules as
     * {@link #effectivePermissions}: active group grants, then unexpired overrides.
     */
    Set<Long> usersWithPermission(String permission);
}
