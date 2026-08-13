package io.haoblog.identity.web;

import io.haoblog.identity.application.AdminAccountLockedException;
import io.haoblog.shared.web.ProblemResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminSessionController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
    private final ProblemResponseWriter problemResponseWriter;

    public AdminSessionController(AuthenticationManager authenticationManager,
                                  SecurityContextRepository securityContextRepository,
                                  ProblemResponseWriter problemResponseWriter) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.problemResponseWriter = problemResponseWriter;
    }

    @PostMapping("/session")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
            httpRequest.getSession(true);
            httpRequest.changeSessionId();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);
            return ResponseEntity.ok(new AdminSessionResponse(authentication.getName(), "ADMIN", true));
        } catch (AdminAccountLockedException exception) {
            return problemResponseWriter.response(HttpStatus.LOCKED, "ACCOUNT_LOCKED", "Account locked", "The administrator account is temporarily locked");
        } catch (BadCredentialsException exception) {
            return problemResponseWriter.response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Authentication failed", "The username or password is incorrect");
        }
    }

    @GetMapping("/session")
    public AdminSessionResponse session(Authentication authentication) {
        return new AdminSessionResponse(authentication.getName(), "ADMIN", true);
    }

    @DeleteMapping("/session")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        logoutHandler.logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken token) {
        return new CsrfTokenResponse(token.getToken());
    }

    public record LoginRequest(@NotBlank @Size(max = 64) String username,
                               @NotBlank @Size(max = 200) String password) {}

    public record AdminSessionResponse(String username, String role, boolean authenticated) {}

    public record CsrfTokenResponse(String token) {}
}
