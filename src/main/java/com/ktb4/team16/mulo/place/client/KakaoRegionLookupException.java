package com.ktb4.team16.mulo.place.client;

public class KakaoRegionLookupException extends RuntimeException {

    public enum Reason {
        API_ERROR,
        INVALID_RESPONSE,
        LEGAL_REGION_NOT_FOUND
    }

    private final Reason reason;

    public KakaoRegionLookupException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
