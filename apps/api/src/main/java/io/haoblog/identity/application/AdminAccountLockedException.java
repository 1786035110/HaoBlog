package io.haoblog.identity.application;

import org.springframework.security.core.AuthenticationException;

public class AdminAccountLockedException extends AuthenticationException {
    public AdminAccountLockedException() {
        super("Admin account is temporarily locked");
    }
}
