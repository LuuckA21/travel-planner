package me.luucka.travelplanner.itinerary;

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
class ActivityController {

    private final ActivityService service;

    ActivityController(ActivityService service) {
        this.service = service;
    }

    @PostMapping("/api/trips/{tripId}/activities")
    @ResponseStatus(HttpStatus.CREATED)
    ActivityResponse create(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID tripId,
                            @Valid @RequestBody ActivityRequest body) {
        return service.create(tripId, user.id(), body);
    }

    @PutMapping("/api/trips/{tripId}/activities/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reorder(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID tripId,
                 @Valid @RequestBody ReorderRequest body) {
        service.reorder(tripId, user.id(), body);
    }

    @PutMapping("/api/activities/{id}")
    ActivityResponse update(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id,
                            @Valid @RequestBody ActivityRequest body) {
        return service.update(id, user.id(), body);
    }

    @DeleteMapping("/api/activities/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
        service.delete(id, user.id());
    }
}
