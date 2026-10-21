package me.luucka.travelplanner.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

record BookingRequest(
        @NotNull BookingType type,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 200) String provider,
        @Size(max = 200) String reference,
        @NotNull LocalDateTime startAt,
        LocalDateTime endAt,
        @Size(max = 200) String origin,
        @Size(max = 300) String location,
        @Size(max = 5000) String notes) {}
