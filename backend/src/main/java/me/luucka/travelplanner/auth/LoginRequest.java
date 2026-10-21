package me.luucka.travelplanner.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = AuthService.PASSWORD_MAX) String password) {}
