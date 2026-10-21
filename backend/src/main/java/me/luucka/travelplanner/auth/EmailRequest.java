package me.luucka.travelplanner.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record EmailRequest(@NotBlank @Size(max = 254) String email) {}
