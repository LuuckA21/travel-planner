package me.luucka.travelplanner.trip;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

@ValidTripDates
public record TripRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @Size(max = 20) List<@NotBlank @Size(max = 80) String> destinations,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate) {}
