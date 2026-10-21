package me.luucka.travelplanner.auth;

import jakarta.validation.constraints.NotBlank;

record EmailRequest(@NotBlank String email) {}
