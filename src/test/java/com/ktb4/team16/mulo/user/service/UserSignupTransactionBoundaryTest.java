package com.ktb4.team16.mulo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringJUnitConfig(UserSignupTransactionBoundaryTest.TestConfig.class)
class UserSignupTransactionBoundaryTest {
    @Autowired UserSignupService signupService;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // 공유 테스트 빈의 호출 기록을 각 테스트 전에 초기화한다.
    @BeforeEach
    void resetMocks() {
        reset(userRepository, passwordEncoder);
    }

    // 중복 조회와 BCrypt는 트랜잭션 밖에서, 사용자 저장은 트랜잭션 안에서 수행되는지 검증한다.
    @Test
    void hashesOutsideTransactionAndSavesInsideTransaction() {
        when(userRepository.existsByEmailAndDeletedAtIsNull("user@example.com"))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return false;
                });
        when(userRepository.existsByNicknameAndDeletedAtIsNull("뮤로16"))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return false;
                });
        when(passwordEncoder.encode("Test1234!"))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    return "encoded-password";
                });
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    return invocation.getArgument(0);
                });

        signupService.signup(new SignupCommand("뮤로16", "user@example.com", "Test1234!"));

        verify(passwordEncoder).encode("Test1234!");
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(savedUser.capture());
        assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("encoded-password");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import({UserSignupService.class, UserSignupWriter.class})
    static class TestConfig {
        // 서비스의 DB 호출만 관찰하도록 저장소를 테스트 빈으로 제공한다.
        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        // 해시 계산 시점의 트랜잭션 상태를 관찰하도록 인코더를 테스트 빈으로 제공한다.
        @Bean
        PasswordEncoder passwordEncoder() {
            return mock(PasswordEncoder.class);
        }

        // 실제 DB 없이 Spring 트랜잭션 프록시 경계만 검증한다.
        @Bean
        PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                // 새 트랜잭션에 사용할 상태 객체를 생성한다.
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                // 트랜잭션 시작을 표시한다.
                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) { }

                // 성공한 트랜잭션의 종료를 허용한다.
                @Override
                protected void doCommit(DefaultTransactionStatus status) { }

                // 실패한 트랜잭션의 종료를 허용한다.
                @Override
                protected void doRollback(DefaultTransactionStatus status) { }
            };
        }
    }
}
