package me.luucka.travelplanner.auth;

import java.util.UUID;

public record MeResponse(UUID id, String email, String displayName) {

    static MeResponse of(UserAccount user) {
        return new MeResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
