package me.luucka.travelplanner.itinerary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

record ActivityRequest(
        @NotNull LocalDate date,
        @NotBlank @Size(max = 200) String title,
        LocalTime startTime,
        @Size(max = 300) String location,
        // Rendered as a link: https only blocks javascript: URLs
        @Size(max = 2000) @Pattern(regexp = "(https://\\S+)?") String mapsUrl,
        @Size(max = 5000) String notes) {}
