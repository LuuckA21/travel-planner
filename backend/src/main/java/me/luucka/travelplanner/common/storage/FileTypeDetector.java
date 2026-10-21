package me.luucka.travelplanner.common.storage;

import java.util.Optional;

public final class FileTypeDetector {

    private FileTypeDetector() {}

    /** Detects the content type from the first 12 bytes; empty when not an allowed type. */
    public static Optional<String> detect(byte[] head) {
        if (startsWith(head, 0, 0x25, 0x50, 0x44, 0x46, 0x2D)) { // %PDF-
            return Optional.of("application/pdf");
        }
        if (startsWith(head, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(head, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of("image/png");
        }
        if (startsWith(head, 0, 0x52, 0x49, 0x46, 0x46) && startsWith(head, 8, 0x57, 0x45, 0x42, 0x50)) { // RIFF....WEBP
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] b, int offset, int... expected) {
        if (b.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((b[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
