package me.luucka.travelplanner.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import me.luucka.travelplanner.common.storage.FileStorage;
import me.luucka.travelplanner.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

class DocumentApiTest extends IntegrationTest {

    static final byte[] PDF = "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired JsonMapper json;
    @Autowired FileStorage storage;
    @MockitoSpyBean TripDocumentRepository documents;

    RequestPostProcessor me;
    String trip;

    @BeforeEach
    void fixture() throws Exception {
        me = testUsers.as(testUsers.create("luca@example.it", "supersecret1"));
        trip = newTrip();
    }

    String newTrip() throws Exception {
        return id(mvc.perform(post("/api/trips").with(me).with(csrf()).contentType(APPLICATION_JSON)
                .content("{\"name\":\"T\",\"destinations\":[\"Roma\"],"
                        + "\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\"}"))
                .andExpect(status().isCreated()));
    }

    String newBooking(String tripId) throws Exception {
        return id(mvc.perform(post("/api/trips/" + tripId + "/bookings").with(me).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"type\":\"FLIGHT\",\"title\":\"F\",\"startAt\":\"2027-03-10T10:00:00\"}"))
                .andExpect(status().isCreated()));
    }

    String id(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asString();
    }

    ResultActions upload(String name, String declaredType, byte[] bytes, String bookingId) throws Exception {
        var req = multipart("/api/trips/" + trip + "/documents")
                .file(new MockMultipartFile("file", name, declaredType, bytes)).with(me).with(csrf());
        if (bookingId != null) {
            req.param("bookingId", bookingId);
        }
        return mvc.perform(req);
    }

    String uploadPdf() throws Exception {
        return id(upload("ticket.pdf", "application/pdf", PDF, null).andExpect(status().isCreated()));
    }

    Path fileOf(String docId) {
        return storage.path(jdbc.queryForObject("SELECT storage_key FROM trip_document WHERE id = ?::uuid",
                String.class, docId));
    }

    long filesInRoot() throws IOException {
        try (Stream<Path> s = Files.list(storage.path(UUID.randomUUID().toString()).getParent())) {
            return s.count();
        }
    }

    @Test
    void uploadAndDownloadPdf() throws Exception {
        String doc = id(upload("ticket.pdf", "application/pdf", PDF, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("ticket.pdf"))
                .andExpect(jsonPath("$.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(PDF.length))
                .andExpect(jsonPath("$.bookingId").doesNotExist()));
        mvc.perform(get("/api/documents/" + doc + "/file").with(me)).andExpect(status().isOk())
                .andExpect(content().bytes(PDF))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "private, no-cache"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")));
        mvc.perform(get("/api/trips/" + trip).with(me)).andExpect(jsonPath("$.documents[0].id").value(doc))
                .andExpect(jsonPath("$.documents[0].fileUrl").value("/api/documents/" + doc + "/file"));
    }

    @Test
    void downloadParamSetsAttachment() throws Exception {
        String doc = uploadPdf();
        mvc.perform(get("/api/documents/" + doc + "/file?download=true").with(me)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment")));
    }

    @Test
    void unicodeFilenameIsPreserved() throws Exception {
        String name = "Biglietto ✈ Città \"A\".pdf";
        String doc = id(upload(name, "application/pdf", PDF, null).andExpect(status().isCreated()));
        mvc.perform(get("/api/documents/" + doc + "/file").with(me)).andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.containsString(
                        "filename*=UTF-8''Biglietto%20%E2%9C%88%20Citt%C3%A0%20%22A%22.pdf")));
        mvc.perform(get("/api/trips/" + trip).with(me)).andExpect(jsonPath("$.documents[0].filename").value(name));
    }

    @Test
    void declaredTypeIsIgnored() throws Exception {
        upload("x.png", "image/png", PDF, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("application/pdf"));
    }

    @Test
    void exeRenamedToPdfIs415() throws Exception {
        upload("a.pdf", "application/pdf", new byte[] {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0, 0, 0},
                null).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void heicIs415() throws Exception {
        byte[] heic = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0};
        upload("a.heic", "image/heic", heic, null).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void fileOver25MbIs413() throws Exception {
        byte[] big = new byte[25 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        upload("big.pdf", "application/pdf", big, null).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void bookingFromAnotherTripIs404() throws Exception {
        String other = newBooking(newTrip());
        long before = filesInRoot();
        upload("a.pdf", "application/pdf", PDF, other).andExpect(status().isNotFound());
        assertThat(filesInRoot()).isEqualTo(before);
    }

    @Test
    void deletingBookingKeepsDocument() throws Exception {
        String booking = newBooking(trip);
        String doc = id(upload("a.pdf", "application/pdf", PDF, booking).andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value(booking)));
        mvc.perform(delete("/api/bookings/" + booking).with(me).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/trips/" + trip).with(me)).andExpect(jsonPath("$.documents[0].id").value(doc))
                .andExpect(jsonPath("$.documents[0].bookingId").doesNotExist());
        mvc.perform(get("/api/documents/" + doc + "/file").with(me)).andExpect(status().isOk())
                .andExpect(content().bytes(PDF));
    }

    @Test
    void deletingDocumentRemovesFile() throws Exception {
        String doc = uploadPdf();
        Path file = fileOf(doc);
        assertThat(file).exists();
        mvc.perform(delete("/api/documents/" + doc).with(me).with(csrf())).andExpect(status().isNoContent());
        assertThat(file).doesNotExist();
        mvc.perform(get("/api/documents/" + doc + "/file").with(me)).andExpect(status().isNotFound());
    }

    @Test
    void deletingTripRemovesFiles() throws Exception {
        Path first = fileOf(uploadPdf());
        Path second = fileOf(uploadPdf());
        mvc.perform(delete("/api/trips/" + trip).with(me).with(csrf())).andExpect(status().isNoContent());
        assertThat(first).doesNotExist();
        assertThat(second).doesNotExist();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_document", Integer.class)).isZero();
    }

    @Test
    void rolledBackDeleteKeepsFile() throws Exception {
        String doc = uploadPdf();
        Path file = fileOf(doc);
        doThrow(new IllegalStateException("db down")).when(documents).flush();
        assertThatThrownBy(() -> mvc.perform(delete("/api/documents/" + doc).with(me).with(csrf())))
                .hasRootCauseMessage("db down");
        assertThat(file).exists();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_document", Integer.class)).isEqualTo(1);
    }

    @Test
    void missingFileIs404() throws Exception {
        String doc = uploadPdf();
        Files.delete(fileOf(doc));
        mvc.perform(get("/api/documents/" + doc + "/file").with(me)).andExpect(status().isNotFound());
    }

    @Test
    void failedInsertRemovesStoredFile() throws Exception {
        long before = filesInRoot();
        doThrow(new IllegalStateException("db down")).when(documents).save(any());
        assertThatThrownBy(() -> upload("a.pdf", "application/pdf", PDF, null)).hasRootCauseMessage("db down");
        assertThat(filesInRoot()).isEqualTo(before);
    }
}
