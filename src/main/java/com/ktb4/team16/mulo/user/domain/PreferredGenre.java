package com.ktb4.team16.mulo.user.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public enum PreferredGenre {
    BALLAD("발라드"),
    DANCE("댄스"),
    RAP_HIPHOP("랩/힙합"),
    R_AND_B_SOUL("R&B/Soul"),
    INDIE("인디음악"),
    ROCK_METAL("록/메탈"),
    FOLK_BLUES("포크/블루스"),
    TROT("트로트"),
    POP("POP"),
    ELECTRONICA("일렉트로니카"),
    OST("OST"),
    JAZZ("재즈"),
    J_POP("J-POP");

    private static final int MAX_SELECTION_COUNT = 3;

    private final String value;

    PreferredGenre(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static boolean isValidSelection(List<String> values) {
        if (values == null || values.size() > MAX_SELECTION_COUNT) {
            return false;
        }

        Set<String> selected = new HashSet<>();
        for (String value : values) {
            if (!isAllowed(value) || !selected.add(value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllowed(String value) {
        if (value == null) {
            return false;
        }
        for (PreferredGenre genre : values()) {
            if (genre.value.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
