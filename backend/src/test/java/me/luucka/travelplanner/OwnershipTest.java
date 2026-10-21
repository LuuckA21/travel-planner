package me.luucka.travelplanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import me.luucka.travelplanner.common.storage.FileStorage;
import me.luucka.travelplanner.support.IntegrationTest;
import me.luucka.travelplanner.trip.Trip;
import me.luucka.travelplanner.trip.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Every endpoint addressing a resource of user A must answer 404 to user B. Later tasks add rows and fixtures. */
class OwnershipTest extends IntegrationTest {

    static final String ACTIVITY_BODY = "{\"date\":\"2027-03-11\",\"title\":\"Y\"}";
    static final String ORDER_BODY = "{\"date\":\"2027-03-11\",\"activityIds\":[\"{activity}\"]}";
    static final String BOOKING_BODY = "{\"type\":\"FLIGHT\",\"title\":\"Y\",\"startAt\":\"2027-03-11T10:00:00\"}";
    static final String TRIP_BODY =
            "{\"name\":\"X\",\"destinations\":[\"Roma\"],\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-12\"}";

    static final String MULTIPART = "multipart";
    static final String COVER_MULTIPART = "multipart-cover";
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    static final byte[] PDF = "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired TripRepository trips;
    @Autowired FileStorage storage;

    UUID trip;
    UUID activity;
    UUID booking;
    UUID document;
    String coverKey;
    RequestPostProcessor userA;
    RequestPostProcessor userB;

    @BeforeEach
    void fixture() throws Exception {
        UUID a = testUsers.create("a@example.it", "supersecret1");
        userA = testUsers.as(a);
        userB = testUsers.as(testUsers.create("b@example.it", "supersecret1"));
        trip = trips.save(new Trip(a, "Trip A", List.of("Roma"),
                LocalDate.of(2027, 3, 10), LocalDate.of(2027, 3, 12))).getId();
        activity = UUID.randomUUID();
        jdbc.update("INSERT INTO activity (id, trip_id, date, position, title) VALUES (?, ?, ?, 0, 'Walk')",
                activity, trip, LocalDate.of(2027, 3, 11));
        booking = UUID.randomUUID();
        jdbc.update("INSERT INTO booking (id, trip_id, type, title, start_at) VALUES (?, ?, 'FLIGHT', 'Flight', ?)",
                booking, trip, LocalDateTime.of(2027, 3, 11, 10, 0));
        document = UUID.randomUUID();
        jdbc.update("INSERT INTO trip_document (id, trip_id, original_filename, content_type, size_bytes, storage_key)"
                        + " VALUES (?, ?, 'a.pdf', 'application/pdf', ?, ?)", document, trip, PDF.length,
                storage.store(new ByteArrayInputStream(PDF)));
        coverKey = storage.store(new ByteArrayInputStream(PNG));
        jdbc.update("UPDATE trip SET cover_storage_key = ? WHERE id = ?", coverKey, trip);
    }

    static Stream<Arguments> endpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/trips/{trip}", null),
                Arguments.of(HttpMethod.PUT, "/api/trips/{trip}", TRIP_BODY),
                Arguments.of(HttpMethod.DELETE, "/api/trips/{trip}", null),
                Arguments.of(HttpMethod.POST, "/api/trips/{trip}/activities", ACTIVITY_BODY),
                Arguments.of(HttpMethod.PUT, "/api/activities/{activity}", ACTIVITY_BODY),
                Arguments.of(HttpMethod.DELETE, "/api/activities/{activity}", null),
                Arguments.of(HttpMethod.PUT, "/api/trips/{trip}/activities/order", ORDER_BODY),
                Arguments.of(HttpMethod.POST, "/api/trips/{trip}/bookings", BOOKING_BODY),
                Arguments.of(HttpMethod.PUT, "/api/bookings/{booking}", BOOKING_BODY),
                Arguments.of(HttpMethod.DELETE, "/api/bookings/{booking}", null),
                Arguments.of(HttpMethod.POST, "/api/trips/{trip}/documents", MULTIPART),
                Arguments.of(HttpMethod.GET, "/api/documents/{document}/file", null),
                Arguments.of(HttpMethod.DELETE, "/api/documents/{document}", null),
                Arguments.of(HttpMethod.PUT, "/api/trips/{trip}/cover", COVER_MULTIPART),
                Arguments.of(HttpMethod.GET, "/api/trips/{trip}/cover", null),
                Arguments.of(HttpMethod.DELETE, "/api/trips/{trip}/cover", null));
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void otherUsersGet404(HttpMethod method, String template, String body) throws Exception {
        assertThat(send(userB, method, template, body)).isEqualTo(404);
        assertThat(trips.findById(trip)).isPresent();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT title FROM activity WHERE id = ?", String.class, activity))
                .isEqualTo("Walk");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM booking", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT title FROM booking WHERE id = ?", String.class, booking))
                .isEqualTo("Flight");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_document", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cover_storage_key FROM trip WHERE id = ?", String.class, trip))
                .isEqualTo(coverKey);
        assertThat(storage.path(coverKey)).exists();
        // Positive control (last: DELETE rows destroy the fixture): the owner's identical request is not a 404
        assertThat(send(userA, method, template, body)).isNotEqualTo(404);
    }

    private int send(RequestPostProcessor user, HttpMethod method, String template, String body) throws Exception {
        String uri = template.replace("{trip}", trip.toString()).replace("{activity}", activity.toString())
                .replace("{booking}", booking.toString()).replace("{document}", document.toString());
        boolean multipart = MULTIPART.equals(body) || COVER_MULTIPART.equals(body);
        var req = !multipart ? request(method, uri)
                : MULTIPART.equals(body)
                ? multipart(method, uri).file(new MockMultipartFile("file", "b.pdf", "application/pdf", PDF))
                : multipart(method, uri).file(new MockMultipartFile("file", "b.png", "image/png", PNG));
        req.with(user).with(csrf());
        if (body != null && !multipart) {
            req.contentType(APPLICATION_JSON).content(body.replace("{activity}", activity.toString()));
        }
        return mvc.perform(req).andReturn().getResponse().getStatus();
    }

    @Test
    void otherUsersTripCannotReferenceForeignBooking() throws Exception {
        UUID ownerB = UUID.fromString(jdbc.queryForObject(
                "SELECT id::text FROM user_account WHERE email = 'b@example.it'", String.class));
        UUID tripB = trips.save(new Trip(ownerB, "Trip B", List.of("Roma"),
                LocalDate.of(2027, 3, 10), LocalDate.of(2027, 3, 12))).getId();
        var upload = multipart("/api/trips/" + tripB + "/documents")
                .file(new MockMultipartFile("file", "b.pdf", "application/pdf", PDF)).param("bookingId", booking.toString());
        assertThat(mvc.perform(upload.with(userB).with(csrf())).andReturn().getResponse().getStatus()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM trip_document", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cover_storage_key FROM trip WHERE id = ?", String.class, trip))
                .isEqualTo(coverKey);
        assertThat(storage.path(coverKey)).exists();
        // Positive control: A with the same booking on A's own trip is accepted
        var own = multipart("/api/trips/" + trip + "/documents")
                .file(new MockMultipartFile("file", "b.pdf", "application/pdf", PDF)).param("bookingId", booking.toString());
        assertThat(mvc.perform(own.with(userA).with(csrf())).andReturn().getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void listDoesNotContainOthersTrips() throws Exception {
        String res = mvc.perform(get("/api/trips").with(userB)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(res).isEqualTo("[]");
    }
}
