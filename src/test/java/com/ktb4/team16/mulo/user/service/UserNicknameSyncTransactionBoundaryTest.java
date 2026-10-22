package com.ktb4.team16.mulo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ktb4.team16.mulo.match.client.ChatRoomClient;
import com.ktb4.team16.mulo.user.entity.User;
import com.ktb4.team16.mulo.user.event.ChatNicknameSyncAfterCommitListener;
import com.ktb4.team16.mulo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringJUnitConfig(UserNicknameSyncTransactionBoundaryTest.TestConfig.class)
class UserNicknameSyncTransactionBoundaryTest {
    @Autowired UserProfileService userProfileService;
    @Autowired UserRepository userRepository;
    @Autowired ChatRoomClient chatRoomClient;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void resetMocks() {
        reset(userRepository, chatRoomClient);
    }

    @Test
    void invokesChatServerOnlyAfterNicknameTransactionCommits() {
        User user = user("기존닉네임");
        when(userRepository.existsByNicknameAndUserIdNotAndDeletedAtIsNull("새닉네임", 17L))
                .thenReturn(false);
        when(userRepository.findByUserIdAndDeletedAtIsNull(17L)).thenReturn(java.util.Optional.of(user));

        new TransactionTemplate(transactionManager).execute(status -> {
            userProfileService.updateNickname(17L, "새닉네임");
            verifyNoInteractions(chatRoomClient);
            return null;
        });

        assertThat(user.getNickname()).isEqualTo("새닉네임");
        verify(chatRoomClient).updateParticipantNickname(17L, "새닉네임");
    }

    @Test
    void doesNotInvokeChatServerWhenNicknameTransactionRollsBack() {
        User user = user("기존닉네임");
        when(userRepository.findByUserIdAndDeletedAtIsNull(17L)).thenReturn(java.util.Optional.of(user));
        when(userRepository.existsByNicknameAndUserIdNotAndDeletedAtIsNull("새닉네임", 17L))
                .thenReturn(false);

        new TransactionTemplate(transactionManager).execute(status -> {
            userProfileService.updateNickname(17L, "새닉네임");
            status.setRollbackOnly();
            return null;
        });

        verifyNoInteractions(chatRoomClient);
    }

    private User user(String nickname) {
        User user = User.signup("user@example.com", "password-hash", nickname);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "userId", 17L);
        return user;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Import({UserProfileService.class, ChatNicknameSyncAfterCommitListener.class})
    static class TestConfig {
        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return mock(PasswordEncoder.class);
        }

        @Bean
        ChatRoomClient chatRoomClient() {
            return mock(ChatRoomClient.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new ThreadBoundTransactionManager();
        }
    }

    /** 실제 Spring의 기존 트랜잭션 참여 및 커밋 동기화 경계를 재현하는 DB 비사용 매니저. */
    private static class ThreadBoundTransactionManager extends AbstractPlatformTransactionManager {
        private final ThreadLocal<TransactionState> currentTransaction = new ThreadLocal<>();

        @Override
        protected Object doGetTransaction() {
            TransactionState transaction = currentTransaction.get();
            return transaction == null ? new TransactionState() : transaction;
        }

        @Override
        protected boolean isExistingTransaction(Object transaction) {
            return ((TransactionState) transaction).active;
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            TransactionState state = (TransactionState) transaction;
            state.active = true;
            currentTransaction.set(state);
        }

        @Override
        protected Object doSuspend(Object transaction) {
            TransactionState suspended = currentTransaction.get();
            currentTransaction.remove();
            return suspended;
        }

        @Override
        protected void doResume(Object transaction, Object suspendedResources) {
            if (suspendedResources instanceof TransactionState state) {
                currentTransaction.set(state);
            }
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            ((TransactionState) status.getTransaction()).active = false;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            ((TransactionState) status.getTransaction()).active = false;
        }

        private static class TransactionState {
            private boolean active;
        }
    }
}
