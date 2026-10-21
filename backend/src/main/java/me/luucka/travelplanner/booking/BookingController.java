package me.luucka.travelplanner.booking;

import jakarta.validation.Valid;
import java.util.UUID;
import me.luucka.travelplanner.auth.AppUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class BookingController {

    private final BookingService service;

    BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping("/api/trips/{tripId}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    BookingResponse create(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID tripId,
                           @Valid @RequestBody BookingRequest body) {
        return service.create(tripId, user.id(), body);
    }

    @PutMapping("/api/bookings/{id}")
    BookingResponse update(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id,
                           @Valid @RequestBody BookingRequest body) {
        return service.update(id, user.id(), body);
    }

    @DeleteMapping("/api/bookings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
        service.delete(id, user.id());
    }
}
