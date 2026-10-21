package me.luucka.travelplanner.auth;

import jakarta.validation.constraints.NotBlank;

record TokenRequest(@NotBlank String token) {}
