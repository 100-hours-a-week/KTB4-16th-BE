package com.ktb4.team16.mulo.global.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.api.StatefulRedisConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** 실제 Lettuce TCP 핸드셰이크를 지연시켜 연결 초기화와 명령 제한을 분리 검증한다. */
class RedisConnectionInitializationTest {

    /** 느린 최초 HELLO는 초기화 기한 내 허용하고 연결 후 ECHO에는 짧은 명령 기한을 적용한다. */
    @Test
    void delayedHandshakeDoesNotSpendCommandTimeout() throws Exception {
        try (var server = new DelayedRedisServer()) {
            new ApplicationContextRunner().withUserConfiguration(RedisConfiguration.class)
                    .withPropertyValues("mulo.redis.host=127.0.0.1", "mulo.redis.port=" + server.port(),
                            "mulo.redis.connect-timeout=500ms", "mulo.redis.initialization-timeout=2s",
                            "mulo.redis.command-timeout=80ms")
                    .run(context -> {
                        @SuppressWarnings("unchecked")
                        StatefulRedisConnection<String, String> connection =
                                context.getBean("redisConnection", StatefulRedisConnection.class);
                        assertThat(connection.sync().ping()).isEqualTo("PONG");
                        assertThat(connection.getTimeout()).isEqualTo(Duration.ofMillis(80));
                        assertThatThrownBy(() -> connection.sync().echo("slow"))
                                .isInstanceOf(RedisCommandTimeoutException.class);
                    });
        }
    }

    /** 테스트용 RESP 서버가 TCP 연결을 수락하고 초기 HELLO만 의도적으로 늦춘다. */
    private static final class DelayedRedisServer implements AutoCloseable {
        private final ServerSocket server;
        private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        private final List<Socket> sockets = java.util.Collections.synchronizedList(new ArrayList<>());

        /** 외부 Redis나 운영 포트 없이 루프백 임의 포트에서 접속을 받는다. */
        private DelayedRedisServer() throws IOException {
            server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
            workers.submit(() -> {
                while (!server.isClosed()) {
                    try {
                        Socket socket = server.accept();
                        sockets.add(socket);
                        workers.submit(() -> serve(socket));
                    } catch (IOException exception) {
                        if (!server.isClosed()) {
                            throw new IllegalStateException(exception);
                        }
                    }
                }
            });
        }

        /** 테스트가 사용할 동적 포트를 노출한다. */
        private int port() {
            return server.getLocalPort();
        }

        /** 실제 RESP 명령을 읽어 HELLO·PING·CLIENT·ECHO에 최소 계약으로 응답한다. */
        private void serve(Socket socket) {
            try (socket; var reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int count = Integer.parseInt(line.substring(1));
                    List<String> parts = new ArrayList<>();
                    for (int index = 0; index < count; index++) {
                        reader.readLine();
                        parts.add(reader.readLine());
                    }
                    String command = parts.getFirst().toUpperCase(java.util.Locale.ROOT);
                    String response;
                    if ("HELLO".equals(command)) {
                        Thread.sleep(200);
                        response = "%7\r\n+server\r\n+redis\r\n+version\r\n+8.2.10\r\n"
                                + "+proto\r\n:3\r\n+id\r\n:1\r\n+mode\r\n+standalone\r\n"
                                + "+role\r\n+master\r\n+modules\r\n*0\r\n";
                    } else if ("PING".equals(command)) {
                        response = "+PONG\r\n";
                    } else if ("ECHO".equals(command)) {
                        Thread.sleep(300);
                        response = "$4\r\nslow\r\n";
                    } else {
                        response = "+OK\r\n";
                    }
                    socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().flush();
                }
            } catch (IOException exception) {
                // 제한시간에 도달한 클라이언트가 연결을 닫는 것은 이 시험의 정상 경로다.
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }

        /** 시험 전용 소켓과 작업만 닫아 실제 Redis·다른 시험에 영향을 주지 않는다. */
        @Override
        public void close() throws IOException {
            server.close();
            synchronized (sockets) {
                for (Socket socket : sockets) {
                    socket.close();
                }
            }
            workers.shutdownNow();
        }
    }
}
