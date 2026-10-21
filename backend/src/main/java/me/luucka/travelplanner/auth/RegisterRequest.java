package me.luucka.travelplanner.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull @Size(min = AuthService.PASSWORD_MIN, max = AuthService.PASSWORD_MAX) String password,
        @NotBlank @Size(max = 80) String displayName) {}
