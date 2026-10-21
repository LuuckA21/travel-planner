package me.luucka.travelplanner.checklist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ChecklistRequest(@NotBlank @Size(max = 80) String name) {}
