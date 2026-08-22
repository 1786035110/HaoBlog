package io.haoblog.identity.persistence;

import io.haoblog.identity.domain.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {
    @Query("select user from AdminUser user where user.username = :username")
    Optional<AdminUser> findForAuthentication(@Param("username") String username);
}
