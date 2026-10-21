package me.luucka.travelplanner.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import me.luucka.travelplanner.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.resource.CachingResourceResolver;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

class SpaServingTest extends IntegrationTest {

    @Autowired ApplicationContext context;

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

    @Test
    void fileLikeMissesAreNotFoundButClientRoutesFallBack() throws Exception {
        mvc.perform(get("/missing.png")).andExpect(status().isNotFound());
        mvc.perform(get("/a/b/foo.map")).andExpect(status().isNotFound());
        for (String path : new String[] {"/trips/123", "/trips/456", "/settings", "/trips/123/edit"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"root\"")));
        }
    }

    @Test
    void backendNamespacesNeverFallBack() throws Exception {
        RequestPostProcessor me = testUsers.as(testUsers.create("luca@example.it", "supersecret1"));
        mvc.perform(get("/api").with(me)).andExpect(status().isNotFound());
        // The actuator discovery document, not index.html
        mvc.perform(get("/actuator").with(me))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("id=\"root\""))));
    }

    @Test
    void noCachingResolverIsConfigured() {
        // CachingResourceResolver's cache is unbounded and would be keyed by every requested path
        ResourceHttpRequestHandler handler = (ResourceHttpRequestHandler) ((SimpleUrlHandlerMapping) context
                .getBean("resourceHandlerMapping")).getUrlMap().get("/**");
        assertThat(handler.getResourceResolvers()).noneMatch(r -> r instanceof CachingResourceResolver);
    }
}
