package com.ktb4.team16.mulo.user.service;

import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserSignupWriter {
    private final UserRepository userRepository;

    // 해시가 준비된 사용자만 짧은 쓰기 트랜잭션에서 저장한다.
    @Transactional
    public void save(User user) {
        userRepository.saveAndFlush(user);
    }
}
