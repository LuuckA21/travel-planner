package me.luucka.travelplanner.trip;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TripSummary(UUID id, String name, List<String> destinations, LocalDate startDate, LocalDate endDate,
                          String coverUrl) {}
