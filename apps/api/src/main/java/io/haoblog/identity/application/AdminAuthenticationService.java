package io.haoblog.identity.application;

import io.haoblog.identity.domain.AdminUser;
import io.haoblog.identity.persistence.AdminUserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AdminAuthenticationService {
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$0V.Xs7CLOUYSekm7RKq3Z.iY76KUan/Xbeu5vjmLpX.sVd4pcFpIu";
    private final AdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AdminAuthenticationService(AdminUserRepository repository,
                                      PasswordEncoder passwordEncoder,
                                      Clock clock) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = BadCredentialsException.class)
    public Authentication authenticate(String username, String password) {
        Optional<AdminUser> user = repository.findForAuthentication(username);
        if (!passwordEncoder.matches(password, user.map(AdminUser::getPasswordHash).orElse(DUMMY_PASSWORD_HASH))) {
            throw new BadCredentialsException("Invalid admin credentials");
        }
        AdminUser authenticatedUser = user.orElseThrow(() -> new BadCredentialsException("Invalid admin credentials"));
        authenticatedUser.recordSuccess(Instant.now(clock));
        return UsernamePasswordAuthenticationToken.authenticated(
                authenticatedUser.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_" + authenticatedUser.getRole())));
    }
}
