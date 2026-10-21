package me.luucka.travelplanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Every endpoint addressing a resource of user A must answer 404 to user B. Later tasks add rows and fixtures. */
class OwnershipTest extends IntegrationTest {

    static final String ACTIVITY_BODY = "{\"date\":\"2027-03-11\",\"title\":\"Y\"}";
    static final String ORDER_BODY = "{\"date\":\"2027-03-11\",\"activityIds\":[\"{activity}\"]}";
    static final String TRIP_BODY =
            "{\"name\":\"X\",\"destinations\":[\"Roma\"],\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-12\"}";

    @Autowired TripRepository trips;

    UUID trip;
    UUID activity;
    RequestPostProcessor userA;
    RequestPostProcessor userB;

    @BeforeEach
    void fixture() {
        UUID a = testUsers.create("a@example.it", "supersecret1");
        userA = testUsers.as(a);
        userB = testUsers.as(testUsers.create("b@example.it", "supersecret1"));
        trip = trips.save(new Trip(a, "Trip A", List.of("Roma"),
                LocalDate.of(2027, 3, 10), LocalDate.of(2027, 3, 12))).getId();
        activity = UUID.randomUUID();
        jdbc.update("INSERT INTO activity (id, trip_id, date, position, title) VALUES (?, ?, ?, 0, 'Walk')",
                activity, trip, LocalDate.of(2027, 3, 11));
    }

    static Stream<Arguments> endpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/trips/{trip}", null),
                Arguments.of(HttpMethod.PUT, "/api/trips/{trip}", TRIP_BODY),
                Arguments.of(HttpMethod.DELETE, "/api/trips/{trip}", null),
                Arguments.of(HttpMethod.POST, "/api/trips/{trip}/activities", ACTIVITY_BODY),
                Arguments.of(HttpMethod.PUT, "/api/activities/{activity}", ACTIVITY_BODY),
                Arguments.of(HttpMethod.DELETE, "/api/activities/{activity}", null),
                Arguments.of(HttpMethod.PUT, "/api/trips/{trip}/activities/order", ORDER_BODY));
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void otherUsersGet404(HttpMethod method, String template, String body) throws Exception {
        assertThat(send(userB, method, template, body)).isEqualTo(404);
        assertThat(trips.findById(trip)).isPresent();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity", Integer.class)).isEqualTo(1);
        // Positive control (last: DELETE rows destroy the fixture): the owner's identical request is not a 404
        assertThat(send(userA, method, template, body)).isNotEqualTo(404);
    }

    private int send(RequestPostProcessor user, HttpMethod method, String template, String body) throws Exception {
        String uri = template.replace("{trip}", trip.toString()).replace("{activity}", activity.toString());
        var req = request(method, uri).with(user).with(csrf());
        if (body != null) {
            req.contentType(APPLICATION_JSON).content(body.replace("{activity}", activity.toString()));
        }
        return mvc.perform(req).andReturn().getResponse().getStatus();
    }

    @Test
    void listDoesNotContainOthersTrips() throws Exception {
        String res = mvc.perform(get("/api/trips").with(userB)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(res).isEqualTo("[]");
    }
}
