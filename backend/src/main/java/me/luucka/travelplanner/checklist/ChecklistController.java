package me.luucka.travelplanner.checklist;

import jakarta.validation.Valid;
import java.util.UUID;
import me.luucka.travelplanner.auth.AppUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChecklistController {

    private final ChecklistService service;

    ChecklistController(ChecklistService service) {
        this.service = service;
    }

    @PostMapping("/api/trips/{tripId}/checklists")
    @ResponseStatus(HttpStatus.CREATED)
    ChecklistResponse create(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID tripId,
                             @Valid @RequestBody ChecklistRequest body) {
        return service.create(tripId, user.id(), body);
    }

    @PutMapping("/api/checklists/{id}")
    ChecklistResponse rename(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id,
                             @Valid @RequestBody ChecklistRequest body) {
        return service.rename(id, user.id(), body);
    }

    @DeleteMapping("/api/checklists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
        service.delete(id, user.id());
    }

    @PostMapping("/api/checklists/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    ItemResponse addItem(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id,
                         @Valid @RequestBody ItemRequest body) {
        return service.addItem(id, user.id(), body);
    }

    @PatchMapping("/api/checklist-items/{id}")
    ItemResponse patchItem(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id,
                           @Valid @RequestBody ItemPatch body) {
        return service.patchItem(id, user.id(), body);
    }

    @DeleteMapping("/api/checklist-items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteItem(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
        service.deleteItem(id, user.id());
    }
}
