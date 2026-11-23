package com.ktb4.team16.mulo.notification.exception;

import org.springframework.dao.TransientDataAccessResourceException;

/** 친구 관계가 잠금 획득 도중 바뀌어 같은 이벤트를 다시 처리해야 할 때 사용한다. */
public class NicknameFriendshipSnapshotChangedException extends TransientDataAccessResourceException {

    public NicknameFriendshipSnapshotChangedException() {
        super("NICKNAME_FRIENDSHIP_SNAPSHOT_CHANGED");
    }
}
