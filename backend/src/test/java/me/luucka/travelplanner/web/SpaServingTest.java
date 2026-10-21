package me.luucka.travelplanner.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import me.luucka.travelplanner.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class SpaServingTest extends IntegrationTest {

    static final String CSP = "default-src 'self'; img-src 'self' blob: data:; style-src 'self' 'unsafe-inline'; "
            + "object-src 'none'; frame-ancestors 'none'; base-uri 'self'";

    @Test
    void clientRouteFallsBackToIndex() throws Exception {
        mvc.perform(get("/trips/123")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"root\"")))
                .andExpect(header().string("Cache-Control", "no-cache"));
    }

    @Test
    void apiMissIsNot404Html() throws Exception {
        RequestPostProcessor me = testUsers.as(testUsers.create("luca@example.it", "supersecret1"));
        mvc.perform(get("/api/nope").with(me)).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void hashedAssetsAreImmutable() throws Exception {
        mvc.perform(get("/assets/app-abc123.js")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"));
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
    }

    @Test
    void serviceWorkerIsNoCache() throws Exception {
        mvc.perform(get("/sw.js")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"));
    }

    @Test
    void cspAndFrameHeadersPresent() throws Exception {
        mvc.perform(get("/")).andExpect(header().string("Content-Security-Policy", CSP))
                .andExpect(header().string("Referrer-Policy", "same-origin"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
        mvc.perform(get("/api/auth/config")).andExpect(header().string("Content-Security-Policy", CSP));
    }
}
