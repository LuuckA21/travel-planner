package me.luucka.travelplanner.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FileTypeDetectorTest {

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) values[i];
        }
        return b;
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void detectsAllowedTypes() {
        assertThat(FileTypeDetector.detect(ascii("%PDF-1.7"))).contains("application/pdf");
        assertThat(FileTypeDetector.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10))).contains("image/jpeg");
        assertThat(FileTypeDetector.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)))
                .contains("image/png");
        assertThat(FileTypeDetector.detect(ascii("RIFF\0\0\0\0WEBP"))).contains("image/webp");
    }

    @Test
    void rejectsExeRenamedPdf() {
        assertThat(FileTypeDetector.detect(ascii("MZ\u0090\0\u0003\0\0\0\u0004\0\0\0"))).isEmpty();
    }

    @Test
    void rejectsHeic() {
        assertThat(FileTypeDetector.detect(ascii("\0\0\0\u0018ftypheic"))).isEmpty();
    }

    @Test
    void rejectsRiffThatIsNotWebp() {
        assertThat(FileTypeDetector.detect(ascii("RIFF\0\0\0\0WAVE"))).isEqualTo(Optional.empty());
    }

    @Test
    void rejectsShortInput() {
        assertThat(FileTypeDetector.detect(bytes(0xFF, 0xD8))).isEmpty();
    }
}
