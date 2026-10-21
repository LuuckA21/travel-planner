package me.luucka.travelplanner.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record ResetPasswordRequest(
        @NotBlank @Size(max = 100) String token,
        @NotNull @Size(min = AuthService.PASSWORD_MIN, max = AuthService.PASSWORD_MAX) String password) {}
