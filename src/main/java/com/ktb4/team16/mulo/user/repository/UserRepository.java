package com.ktb4.team16.mulo.user.repository;

import com.ktb4.team16.mulo.user.entity.User;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByNicknameAndDeletedAtIsNull(String nickname);

    boolean existsByNicknameAndUserIdNotAndDeletedAtIsNull(String nickname, Long userId);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByUserIdAndDeletedAtIsNull(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :userId and u.deletedAt is null")
    Optional<User> findByUserIdAndDeletedAtIsNullForUpdate(Long userId);

    boolean existsByUserIdAndDeletedAtIsNull(Long userId);
}
