package me.luucka.travelplanner.trip;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import me.luucka.travelplanner.booking.BookingResponse;
import me.luucka.travelplanner.itinerary.ActivityResponse;

public record TripDetail(UUID id, String name, List<String> destinations, LocalDate startDate, LocalDate endDate,
                         String coverUrl, List<ActivityResponse> activities,
                         List<BookingResponse> bookings) {}
