package com.travelplanner;

import com.travelplanner.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthTest extends IntegrationTest {

    @Test
    void healthIsUpAndPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        // Authenticated, so a 404 proves the endpoint is not exposed (not merely protected)
        mvc.perform(get("/actuator/env").with(user("u"))).andExpect(status().isNotFound());
    }
}
