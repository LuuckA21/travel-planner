package me.luucka.travelplanner.booking;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.TimeZone;
import me.luucka.travelplanner.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;

class BookingApiTest extends IntegrationTest {

    @Autowired JsonMapper json;

    RequestPostProcessor me;
    String trip;
    TimeZone originalZone;

    @BeforeEach
    void fixture() throws Exception {
        originalZone = TimeZone.getDefault();
        me = testUsers.as(testUsers.create("luca@example.it", "supersecret1"));
        trip = json.readTree(mvc.perform(post("/api/trips").with(me).with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"T\",\"destinations\":[\"Roma\"],"
                                + "\"startDate\":\"2027-03-10\",\"endDate\":\"2027-03-14\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
    }

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    ResultActions add(String type, String title, String startAt, String endAt) throws Exception {
        String end = endAt == null ? "null" : "\"" + endAt + "\"";
        return mvc.perform(post("/api/trips/" + trip + "/bookings").with(me).with(csrf())
                .contentType(APPLICATION_JSON)
                .content("{\"type\":\"" + type + "\",\"title\":\"" + title + "\",\"startAt\":\"" + startAt
                        + "\",\"endAt\":" + end + "}"));
    }

    @Test
    void localTimesRoundTripUnchanged() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
        add("FLIGHT", "FCO-JFK", "2027-03-10T10:00:00", "2027-03-10T15:40:00").andExpect(status().isCreated())
                .andExpect(jsonPath("$.startAt").value("2027-03-10T10:00:00"))
                .andExpect(jsonPath("$.endAt").value("2027-03-10T15:40:00"));
        mvc.perform(get("/api/trips/" + trip).with(me)).andExpect(status().isOk())
                .andExpect(jsonPath("$.bookings[0].startAt").value("2027-03-10T10:00:00"))
                .andExpect(jsonPath("$.bookings[0].endAt").value("2027-03-10T15:40:00"));
    }

    @Test
    void endBeforeStartIs400() throws Exception {
        add("FLIGHT", "X", "2027-03-10T10:00:00", "2027-03-10T09:59:00").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.endAt").exists());
    }

    @Test
    void unknownTypeIs400() throws Exception {
        add("HELICOPTER", "X", "2027-03-10T10:00:00", null).andExpect(status().isBadRequest());
    }

    @Test
    void listOrderedByStart() throws Exception {
        add("TRANSPORT", "Late", "2027-03-12T08:00:00", null).andExpect(status().isCreated());
        add("ACCOMMODATION", "Early", "2027-03-10T14:00:00", null).andExpect(status().isCreated());
        mvc.perform(get("/api/trips/" + trip).with(me)).andExpect(status().isOk())
                .andExpect(jsonPath("$.bookings[0].title").value("Early"))
                .andExpect(jsonPath("$.bookings[1].title").value("Late"));
    }

    @Test
    void deleteBooking() throws Exception {
        String id = json.readTree(add("FLIGHT", "X", "2027-03-10T10:00:00", null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asString();
        mvc.perform(delete("/api/bookings/" + id).with(me).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/trips/" + trip).with(me)).andExpect(jsonPath("$.bookings").isEmpty());
    }
}
