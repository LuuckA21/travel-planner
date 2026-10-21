package me.luucka.travelplanner.trip;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import me.luucka.travelplanner.auth.AppUserDetails;
import me.luucka.travelplanner.common.storage.StoredFiles;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/trips")
class TripController {

    private final TripService service;

    TripController(TripService service) {
        this.service = service;
    }

    @GetMapping
    List<TripSummary> list(@AuthenticationPrincipal AppUserDetails user) {
        return service.list(user.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TripDetail create(@AuthenticationPrincipal AppUserDetails user, @Valid @RequestBody TripRequest body) {
        return service.create(user.id(), body);
    }

    @GetMapping("/{id}")
    TripDetail get(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
        return service.get(id, user.id());
    }

    @PutMapping("/{id}")
    TripDetail update(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id,
                      @Valid @RequestBody TripRequest body) {
        return service.update(id, user.id(), body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
        service.delete(id, user.id());
    }

    @PutMapping("/{id}/cover")
    TripSummary setCover(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id,
                         @RequestParam("file") MultipartFile file) {
        return service.setCover(id, user.id(), file);
    }

    @GetMapping("/{id}/cover")
    ResponseEntity<Resource> cover(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
        var c = service.cover(id, user.id());
        return StoredFiles.response(c.path(), c.contentType(), "cover", false);
    }

    @DeleteMapping("/{id}/cover")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCover(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
        service.deleteCover(id, user.id());
    }
}
