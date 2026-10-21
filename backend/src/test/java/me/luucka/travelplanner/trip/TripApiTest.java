package me.luucka.travelplanner.trip;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import me.luucka.travelplanner.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

class TripApiTest extends IntegrationTest {

    @Autowired JsonMapper json;

    RequestPostProcessor me;

    @BeforeEach
    void user() {
        me = testUsers.as(testUsers.create("luca@example.it", "supersecret1"));
    }

    String body(String name, List<String> destinations, String start, String end) {
        return json.writeValueAsString(Map.of("name", name, "destinations", destinations,
                "startDate", start, "endDate", end));
    }

    String create(String name, String start, String end) throws Exception {
        String res = mvc.perform(post("/api/trips").with(me).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body(name, List.of("Roma"), start, end)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asString();
    }

    @Test
    void createAndGetTrip() throws Exception {
        String res = mvc.perform(post("/api/trips").with(me).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("Guatemala e Belize", List.of("Guatemala", "Belize"), "2027-03-10", "2027-03-24")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(res).get("id").asString();

        mvc.perform(get("/api/trips/" + id).with(me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Guatemala e Belize"))
                .andExpect(jsonPath("$.destinations[0]").value("Guatemala"))
                .andExpect(jsonPath("$.destinations[1]").value("Belize"))
                .andExpect(jsonPath("$.startDate").value("2027-03-10"))
                .andExpect(jsonPath("$.endDate").value("2027-03-24"))
                .andExpect(jsonPath("$.coverUrl").doesNotExist());
    }

    @Test
    void listReturnsOnlyOwnTripsOrderedByStart() throws Exception {
        create("Later", "2027-06-01", "2027-06-05");
        create("Earlier", "2027-02-01", "2027-02-05");
        RequestPostProcessor other = testUsers.as(testUsers.create("other@example.it", "supersecret1"));
        mvc.perform(post("/api/trips").with(other).with(csrf()).contentType(APPLICATION_JSON)
                .content(body("Theirs", List.of("Parigi"), "2027-01-01", "2027-01-02"))).andExpect(status().isCreated());

        mvc.perform(get("/api/trips").with(me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Earlier"))
                .andExpect(jsonPath("$[1].name").value("Later"));
    }

    @Test
    void endBeforeStartIs400() throws Exception {
        mvc.perform(post("/api/trips").with(me).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("X", List.of("Roma"), "2027-03-10", "2027-03-09")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.endDate").exists());
    }

    @Test
    void tripLongerThan365DaysIs400() throws Exception {
        mvc.perform(post("/api/trips").with(me).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("X", List.of("Roma"), "2027-01-01", "2028-01-02")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.endDate").exists());
        mvc.perform(post("/api/trips").with(me).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("X", List.of("Roma"), "2027-01-01", "2028-01-01")))
                .andExpect(status().isCreated());
    }

    @Test
    void tooManyDestinationsIs400() throws Exception {
        List<String> many = IntStream.range(0, 21).mapToObj(i -> "D" + i).toList();
        mvc.perform(post("/api/trips").with(me).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("X", many, "2027-01-01", "2027-01-05")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.destinations").exists());
    }

    @Test
    void updateReplacesDestinations() throws Exception {
        String id = create("Old", "2027-03-10", "2027-03-12");
        mvc.perform(put("/api/trips/" + id).with(me).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("New", List.of("A", "B", "C"), "2027-04-01", "2027-04-03")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"));
        mvc.perform(put("/api/trips/" + id).with(me).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("New", List.of("Z"), "2027-04-01", "2027-04-03")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/trips/" + id).with(me))
                .andExpect(jsonPath("$.destinations.length()").value(1))
                .andExpect(jsonPath("$.destinations[0]").value("Z"))
                .andExpect(jsonPath("$.startDate").value("2027-04-01"));
    }

    @Test
    void deleteRemovesTrip() throws Exception {
        String id = create("Gone", "2027-03-10", "2027-03-12");
        mvc.perform(delete("/api/trips/" + id).with(me).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/trips/" + id).with(me)).andExpect(status().isNotFound());
        mvc.perform(get("/api/trips/" + UUID.randomUUID()).with(me)).andExpect(status().isNotFound());
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/trips")).andExpect(status().isUnauthorized());
    }
}
