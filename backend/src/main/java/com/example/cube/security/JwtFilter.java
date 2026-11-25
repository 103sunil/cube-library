package com.example.cube.security;

import com.example.cube.auth.entity.User;
import com.example.cube.auth.repository.UserRepository;
import com.example.cube.auth.service.PermissionService;
import com.example.cube.auth.service.TokenRevocationService;
import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates "Authorization: Bearer <jwt>" requests. The token only proves identity;
 * the user's status and permissions are re-read from the database on every request, so
 * deactivation, logout and permission changes take effect immediately.
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final TokenRevocationService revocationService;

    public JwtFilter(JwtUtil jwtUtil, UserRepository userRepository, PermissionService permissionService,
                     TokenRevocationService revocationService) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.revocationService = revocationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            authenticate(header.substring(BEARER.length()).trim());
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        Optional<Claims> parsed = jwtUtil.parse(token);
        if (!parsed.isPresent()) {
            return;
        }
        Claims claims = parsed.get();
        if (claims.getId() == null || revocationService.isRevoked(claims.getId())) {
            return;
        }
        Optional<User> user = userRepository.findByUsername(claims.getSubject());
        if (!user.isPresent() || !user.get().isActive()) {
            return;
        }

        CurrentUser principal = permissionService.buildCurrentUser(user.get(), claims.getId(), claims.getExpiration());
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String permission : principal.getPermissions()) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
