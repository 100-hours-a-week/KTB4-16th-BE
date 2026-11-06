package com.ktb4.team16.mulo.notification.sse;

import java.util.Set;

public interface NotificationCreatedNotifier {
    void notificationCreated(Set<Long> recipientUserIds);
}
