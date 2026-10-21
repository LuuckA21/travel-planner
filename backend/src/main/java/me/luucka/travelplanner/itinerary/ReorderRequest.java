package me.luucka.travelplanner.itinerary;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

record ReorderRequest(@NotNull LocalDate date, @NotNull List<@NotNull UUID> activityIds) {}
