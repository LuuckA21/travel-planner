package me.luucka.travelplanner.document;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
class DocumentController {

    private final DocumentService service;

    DocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping("/api/trips/{tripId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    DocumentResponse upload(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID tripId,
                            @RequestParam("file") MultipartFile file,
                            @RequestParam(required = false) UUID bookingId) {
        return service.upload(tripId, user.id(), file, bookingId);
    }

    @GetMapping("/api/documents/{id}/file")
    ResponseEntity<Resource> file(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id,
                                  @RequestParam(defaultValue = "false") boolean download) {
        var f = service.open(id, user.id());
        return StoredFiles.response(f.path(), f.contentType(), f.filename(), download);
    }

    @DeleteMapping("/api/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
        service.delete(id, user.id());
    }
}
