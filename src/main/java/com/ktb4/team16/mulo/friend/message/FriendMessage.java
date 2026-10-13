package com.ktb4.team16.mulo.friend.message;

/** FRIEND API의 성공 응답 문구를 한 곳에서 관리한다. */
public final class FriendMessage {

    public static final String FRIENDS_RETRIEVED = "친구 목록 조회 성공";
    public static final String RECEIVED_REQUESTS_RETRIEVED = "받은 친구 요청 목록 조회 성공";
    public static final String SENT_REQUESTS_RETRIEVED = "보낸 친구 요청 목록 조회 성공";
    public static final String REQUEST_SENT = "친구 요청을 보냈습니다.";
    public static final String REQUEST_AUTO_ACCEPTED = "서로의 친구 요청이 확인되어 친구가 되었습니다.";
    public static final String REQUEST_ACCEPTED = "친구 요청을 수락했습니다.";
    public static final String REQUEST_DELETED = "친구 요청이 삭제되었습니다.";
    public static final String FRIENDSHIP_DELETED = "친구가 삭제되었습니다.";

    /** 상수 모음의 인스턴스 생성을 막는다. */
    private FriendMessage() {
    }
}
