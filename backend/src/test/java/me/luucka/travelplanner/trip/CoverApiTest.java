package me.luucka.travelplanner.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import me.luucka.travelplanner.common.storage.FileStorage;
import me.luucka.travelplanner.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

class CoverApiTest extends IntegrationTest {

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1, 2};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    static final byte[] PDF = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired JsonMapper json;
    @Autowired FileStorage storage;

    RequestPostProcessor me;
    String trip;

    @BeforeEach
    void fixture() throws Exception {
        me = testUsers.as(testUsers.create("luca@example.it", "supersecret1"));
        trip = json.readTree(mvc.perform(post("/api/trips").with(me).with(csrf()).contentType(APPLICATION_JSON)
                .content("{\"name\":\"T\",\"destinations\":[\"Roma\"],"
                        + "\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asString();
    }

    ResultActions putCover(String name, byte[] bytes) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, "/api/trips/" + trip + "/cover")
                .file(new MockMultipartFile("file", name, "image/jpeg", bytes)).with(me).with(csrf()));
    }

    String coverKey() {
        return jdbc.queryForObject("SELECT cover_storage_key FROM trip WHERE id = ?::uuid", String.class, trip);
    }

    @Test
    void uploadCoverSetsVersionedUrl() throws Exception {
        putCover("c.jpg", JPEG).andExpect(status().isOk())
                .andExpect(jsonPath("$.coverUrl").value("/api/trips/" + trip + "/cover?v=" + coverKey()));
        mvc.perform(get("/api/trips/" + trip + "/cover?v=" + coverKey()).with(me)).andExpect(status().isOk())
                .andExpect(content().bytes(JPEG))
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "private, no-cache"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")));
        mvc.perform(get("/api/trips/" + trip).with(me))
                .andExpect(jsonPath("$.coverUrl").value("/api/trips/" + trip + "/cover?v=" + coverKey()));
        mvc.perform(get("/api/trips").with(me))
                .andExpect(jsonPath("$[0].coverUrl").value("/api/trips/" + trip + "/cover?v=" + coverKey()));
    }

    @Test
    void pdfCoverIs415() throws Exception {
        putCover("c.pdf", PDF).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        assertThat(coverKey()).isNull();
    }

    @Test
    void coverOver25MbIs413() throws Exception {
        byte[] big = new byte[25 * 1024 * 1024 + 1];
        System.arraycopy(JPEG, 0, big, 0, JPEG.length);
        putCover("big.jpg", big).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void replacingCoverDeletesOldFile() throws Exception {
        putCover("a.jpg", JPEG).andExpect(status().isOk());
        String oldKey = coverKey();
        Path oldFile = storage.path(oldKey);
        assertThat(oldFile).exists();
        putCover("b.png", PNG).andExpect(status().isOk());
        assertThat(coverKey()).isNotEqualTo(oldKey);
        assertThat(oldFile).doesNotExist();
        assertThat(storage.path(coverKey())).exists();
        mvc.perform(get("/api/trips/" + trip + "/cover").with(me)).andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    void deleteCover() throws Exception {
        putCover("a.jpg", JPEG).andExpect(status().isOk());
        Path file = storage.path(coverKey());
        mvc.perform(delete("/api/trips/" + trip + "/cover").with(me).with(csrf())).andExpect(status().isNoContent());
        assertThat(file).doesNotExist();
        assertThat(coverKey()).isNull();
        mvc.perform(get("/api/trips/" + trip).with(me)).andExpect(jsonPath("$.coverUrl").doesNotExist());
        mvc.perform(get("/api/trips/" + trip + "/cover").with(me)).andExpect(status().isNotFound());
    }

    @Test
    void deletingTripRemovesCoverFile() throws Exception {
        putCover("a.jpg", JPEG).andExpect(status().isOk());
        Path file = storage.path(coverKey());
        mvc.perform(delete("/api/trips/" + trip).with(me).with(csrf())).andExpect(status().isNoContent());
        assertThat(file).doesNotExist();
    }

    @Test
    void missingCoverFileIs404() throws Exception {
        putCover("a.jpg", JPEG).andExpect(status().isOk());
        Files.delete(storage.path(coverKey()));
        mvc.perform(get("/api/trips/" + trip + "/cover").with(me)).andExpect(status().isNotFound());
    }
}
