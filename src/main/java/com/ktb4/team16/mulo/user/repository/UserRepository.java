package com.ktb4.team16.mulo.user.repository;

import com.ktb4.team16.mulo.user.entity.User;
import java.util.Optional;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByNicknameAndDeletedAtIsNull(String nickname);

    boolean existsByNicknameAndUserIdNotAndDeletedAtIsNull(String nickname, Long userId);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByUserIdAndDeletedAtIsNull(Long userId);

    /** 탈퇴하지 않은 요청 대상을 닉네임으로 찾는다. */
    Optional<User> findByNicknameAndDeletedAtIsNull(String nickname);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :userId and u.deletedAt is null")
    Optional<User> findByUserIdAndDeletedAtIsNullForUpdate(Long userId);

    /** 친구 쌍 쓰기 전에 두 활성 사용자 행을 ID 오름차순으로 잠근다. */
    @Query(value = """
            SELECT *
            FROM users
            WHERE user_id IN (:userIds)
                AND deleted_at IS NULL
            ORDER BY user_id ASC
            FOR UPDATE
            """, nativeQuery = true)
    List<User> lockActiveUsersByIdAscending(@Param("userIds") List<Long> userIds);

    boolean existsByUserIdAndDeletedAtIsNull(Long userId);
}
