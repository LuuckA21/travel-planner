package me.luucka.travelplanner.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(UUID id, UUID bookingId, String filename, String contentType, long sizeBytes,
                               Instant createdAt, String fileUrl) {}
