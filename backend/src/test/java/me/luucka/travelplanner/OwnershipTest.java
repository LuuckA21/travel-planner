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

    static final String TRIP_BODY =
            "{\"name\":\"X\",\"destinations\":[\"Roma\"],\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-12\"}";

    @Autowired TripRepository trips;

    UUID trip;
    RequestPostProcessor userB;

    @BeforeEach
    void fixture() {
        UUID a = testUsers.create("a@example.it", "supersecret1");
        userB = testUsers.as(testUsers.create("b@example.it", "supersecret1"));
        trip = trips.save(new Trip(a, "Trip A", List.of("Roma"),
                LocalDate.of(2027, 3, 10), LocalDate.of(2027, 3, 12))).getId();
    }

    static Stream<Arguments> endpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/trips/{trip}", null),
                Arguments.of(HttpMethod.PUT, "/api/trips/{trip}", TRIP_BODY),
                Arguments.of(HttpMethod.DELETE, "/api/trips/{trip}", null));
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void otherUsersGet404(HttpMethod method, String template, String body) throws Exception {
        String uri = template.replace("{trip}", trip.toString());
        var req = request(method, uri).with(userB).with(csrf());
        if (body != null) {
            req.contentType(APPLICATION_JSON).content(body);
        }
        mvc.perform(req).andExpect(status().isNotFound());
        assertThat(trips.findById(trip)).isPresent();
    }

    @Test
    void listDoesNotContainOthersTrips() throws Exception {
        String res = mvc.perform(get("/api/trips").with(userB)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(res).isEqualTo("[]");
    }
}
