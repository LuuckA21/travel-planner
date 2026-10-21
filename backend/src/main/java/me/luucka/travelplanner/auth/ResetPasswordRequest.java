package me.luucka.travelplanner.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record ResetPasswordRequest(
        @NotBlank String token,
        @NotNull @Size(min = AuthService.PASSWORD_MIN, max = AuthService.PASSWORD_MAX) String password) {}
