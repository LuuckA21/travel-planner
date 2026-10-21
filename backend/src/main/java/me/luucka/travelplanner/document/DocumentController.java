package me.luucka.travelplanner.document;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import me.luucka.travelplanner.auth.AppUserDetails;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
        var disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(f.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
                .body(new FileSystemResource(f.path()));
    }

    @DeleteMapping("/api/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AppUserDetails user, @PathVariable UUID id) {
        service.delete(id, user.id());
    }
}
