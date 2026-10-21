package me.luucka.travelplanner.checklist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ItemRequest(@NotBlank @Size(max = 300) String text) {}
