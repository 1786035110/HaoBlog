package io.haoblog.identity.application;

import io.haoblog.identity.domain.AdminUser;
import io.haoblog.identity.persistence.AdminUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class AdminAuthenticationService {
    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final int maxFailures;
    private final Duration lockDuration;

    public AdminAuthenticationService(AdminUserRepository repository,
                                      PasswordEncoder passwordEncoder,
                                      Clock clock,
                                      @Value("${haoblog.security.login-max-failures:5}") int maxFailures,
                                      @Value("${haoblog.security.login-lock-duration:15m}") Duration lockDuration) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.maxFailures = maxFailures;
        this.lockDuration = lockDuration;
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public Authentication authenticate(String username, String password) {
        AdminUser user = repository.findForAuthentication(username)
                .orElseThrow(() -> new BadCredentialsException("Invalid admin credentials"));
        Instant now = clock.instant();
        if (user.isLocked(now)) {
            throw new AdminAccountLockedException();
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.recordFailure(now, maxFailures, lockDuration);
            throw new BadCredentialsException("Invalid admin credentials");
        }
        user.recordSuccess(now);
        return UsernamePasswordAuthenticationToken.authenticated(
                user.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
    }
}
