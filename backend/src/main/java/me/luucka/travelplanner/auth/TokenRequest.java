package me.luucka.travelplanner.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record TokenRequest(@NotBlank @Size(max = 100) String token) {}
