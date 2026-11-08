package com.ktb4.team16.mulo.global.exception;

import java.net.SocketException;
import java.util.Locale;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

public final class AsyncRequestFailureClassifier {

    private AsyncRequestFailureClassifier() {
    }

    public static boolean containsAsyncRequestNotUsable(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof AsyncRequestNotUsableException) {
                return true;
            }
        }
        return false;
    }

    public static boolean isExpectedConnectionTermination(Throwable exception) {
        if (exception instanceof AsyncRequestNotUsableException asyncException
                && asyncException.getCause() == null) {
            return true;
        }
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (isKnownClientDisconnect(cause)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKnownClientDisconnect(Throwable exception) {
        if (exception instanceof ClientAbortException) {
            return true;
        }
        if (!(exception instanceof SocketException)) {
            return false;
        }
        String message = exception.getMessage();
        if (message == null) {
            return false;
        }
        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        return normalizedMessage.contains("broken pipe")
                || normalizedMessage.contains("connection reset by peer");
    }
}
