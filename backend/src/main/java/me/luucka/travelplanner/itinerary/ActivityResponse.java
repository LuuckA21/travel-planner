package me.luucka.travelplanner.itinerary;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record ActivityResponse(UUID id, LocalDate date, int position, String title, LocalTime startTime,
                               String location, String mapsUrl, String mapsLink, String notes) {}
