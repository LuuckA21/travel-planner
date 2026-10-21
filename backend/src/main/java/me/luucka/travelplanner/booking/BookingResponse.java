package me.luucka.travelplanner.booking;

import java.time.LocalDateTime;
import java.util.UUID;

public record BookingResponse(UUID id, BookingType type, String title, String provider, String reference,
                              LocalDateTime startAt, LocalDateTime endAt, String origin, String location,
                              String notes) {}
