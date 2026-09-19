package com.ktb4.team16.mulo.auth.repository;

import com.ktb4.team16.mulo.auth.entity.RefreshToken;
import com.ktb4.team16.mulo.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByUser(User user);

}
