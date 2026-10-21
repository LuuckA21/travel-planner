package me.luucka.travelplanner.itinerary;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.luucka.travelplanner.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

class ActivityApiTest extends IntegrationTest {

    @Autowired JsonMapper json;

    RequestPostProcessor me;
    String trip;

    @BeforeEach
    void fixture() throws Exception {
        me = testUsers.as(testUsers.create("luca@example.it", "supersecret1"));
        trip = json.readTree(mvc.perform(post("/api/trips").with(me).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"T\",\"destinations\":[\"Roma\"],"
                                + "\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
    }

    String body(String date, String title, String location, String mapsUrl) {
        Map<String, Object> m = new HashMap<>();
        m.put("date", date);
        m.put("title", title);
        m.put("location", location);
        m.put("mapsUrl", mapsUrl);
        return json.writeValueAsString(m);
    }

    org.springframework.test.web.servlet.ResultActions add(String date, String title, String location, String mapsUrl)
            throws Exception {
        return mvc.perform(post("/api/trips/" + trip + "/activities").with(me).with(csrf())
                .contentType(APPLICATION_JSON).content(body(date, title, location, mapsUrl)));
    }

    String addId(String date, String title) throws Exception {
        String res = add(date, title, null, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asString();
    }

    @Test
    void createAppendsAtEndOfDay() throws Exception {
        add("2027-03-12", "First", null, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(0));
        add("2027-03-12", "Second", null, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(1));
    }

    @Test
    void dateOutsideTripIs400() throws Exception {
        add("2027-03-15", "Late", null, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.date").exists());
        add("2027-03-09", "Early", null, null).andExpect(status().isBadRequest());
        add("2027-03-14", "Last day", null, null).andExpect(status().isCreated());
    }

    @Test
    void nonHttpsMapsUrlIs400() throws Exception {
        add("2027-03-12", "X", null, "javascript:alert(1)").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.mapsUrl").exists());
        add("2027-03-12", "X", null, "http://x").andExpect(status().isBadRequest());
        add("2027-03-12", "X", null, "https://maps.app.goo.gl/abc").andExpect(status().isCreated());
    }

    @Test
    void reorderSetsPositions() throws Exception {
        String a = addId("2027-03-12", "A");
        String b = addId("2027-03-12", "B");
        mvc.perform(put("/api/trips/" + trip + "/activities/order").with(me).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("date", "2027-03-12", "activityIds", List.of(b, a)))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/trips/" + trip).with(me))
                .andExpect(jsonPath("$.activities[0].id").value(b))
                .andExpect(jsonPath("$.activities[0].position").value(0))
                .andExpect(jsonPath("$.activities[1].id").value(a))
                .andExpect(jsonPath("$.activities[1].position").value(1));
    }

    @Test
    void reorderWithIncompleteListIs400() throws Exception {
        String a = addId("2027-03-12", "A");
        String b = addId("2027-03-12", "B");
        String other = addId("2027-03-13", "Other day");

        for (List<String> ids : List.of(List.of(a), List.of(a, b, other), List.of(a, other), List.of(a, a))) {
            mvc.perform(put("/api/trips/" + trip + "/activities/order").with(me).with(csrf())
                            .contentType(APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("date", "2027-03-12", "activityIds", ids))))
                    .andExpect(status().isBadRequest());
        }
        // an activity of another user's trip is rejected the same way
        RequestPostProcessor other2 = testUsers.as(testUsers.create("o@example.it", "supersecret1"));
        String otherTrip = json.readTree(mvc.perform(post("/api/trips").with(other2).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"T\",\"destinations\":[\"Roma\"],"
                                + "\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asString();
        String foreign = json.readTree(mvc.perform(post("/api/trips/" + otherTrip + "/activities").with(other2)
                        .with(csrf()).contentType(APPLICATION_JSON).content(body("2027-03-12", "F", null, null)))
                .andReturn().getResponse().getContentAsString()).get("id").asString();
        mvc.perform(put("/api/trips/" + trip + "/activities/order").with(me).with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("date", "2027-03-12", "activityIds", List.of(a, foreign)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void movingToAnotherDayAppendsAtEnd() throws Exception {
        addId("2027-03-13", "Existing 1");
        addId("2027-03-13", "Existing 2");
        String moved = addId("2027-03-12", "Moved");
        mvc.perform(put("/api/activities/" + moved).with(me).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("2027-03-13", "Moved", null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2027-03-13"))
                .andExpect(jsonPath("$.position").value(2));
    }

    @Test
    void updateKeepsPositionOnSameDayAndRejectsDateOutsideTrip() throws Exception {
        addId("2027-03-12", "A");
        String b = addId("2027-03-12", "B");
        mvc.perform(put("/api/activities/" + b).with(me).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("2027-03-12", "B2", "Somewhere", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("B2"))
                .andExpect(jsonPath("$.position").value(1));
        mvc.perform(put("/api/activities/" + b).with(me).with(csrf()).contentType(APPLICATION_JSON)
                        .content(body("2027-04-01", "B2", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.date").exists());
    }

    @Test
    void deleteRemovesActivity() throws Exception {
        String a = addId("2027-03-12", "A");
        mvc.perform(delete("/api/activities/" + a).with(me).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/trips/" + trip).with(me)).andExpect(jsonPath("$.activities.length()").value(0));
    }

    @Test
    void shrinkingTripDatesKeepsActivities() throws Exception {
        addId("2027-03-11", "Early activity");
        mvc.perform(put("/api/trips/" + trip).with(me).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"T\",\"destinations\":[\"Roma\"],"
                                + "\"startDate\":\"2027-03-12\",\"endDate\":\"2027-03-14\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities.length()").value(1));
        mvc.perform(get("/api/trips/" + trip).with(me))
                .andExpect(jsonPath("$.activities[0].title").value("Early activity"));
    }

    @Test
    void detailIncludesMapsLink() throws Exception {
        add("2027-03-12", "Custom", "Somewhere", "https://maps.app.goo.gl/abc").andExpect(status().isCreated())
                .andExpect(jsonPath("$.mapsLink").value("https://maps.app.goo.gl/abc"));
        add("2027-03-12", "Derived", "Antigua", null).andExpect(status().isCreated());
        add("2027-03-12", "None", null, null).andExpect(status().isCreated());
        mvc.perform(get("/api/trips/" + trip).with(me))
                .andExpect(jsonPath("$.activities[0].mapsLink").value("https://maps.app.goo.gl/abc"))
                .andExpect(jsonPath("$.activities[1].mapsLink")
                        .value("https://www.google.com/maps/search/?api=1&query=Antigua"))
                .andExpect(jsonPath("$.activities[2].mapsLink").doesNotExist());
    }
}
