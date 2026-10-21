package me.luucka.travelplanner.web;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // No caching resolver: its cache is unbounded and the fallback would key it by every requested path.
        // Vite fingerprints these; a missing one is a real 404, never index.html cached as immutable
        registry.addResourceHandler("/assets/**").addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
        registry.addResourceHandler("/**").addResourceLocations("classpath:/static/")
                .setCacheControl(CacheControl.noCache())
                .resourceChain(false).addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource found = super.getResource(resourcePath, location);
                        if (found == null && isClientRoute(resourcePath)) {
                            return location.createRelative("index.html");
                        }
                        return found;
                    }
                });
    }

    // Backend namespaces and file-like paths (favicon.ico, foo.map) are real 404s, not client routes
    private static boolean isClientRoute(String path) {
        String first = path.split("/", 2)[0];
        String last = path.substring(path.lastIndexOf('/') + 1);
        return !first.equals("api") && !first.equals("actuator") && !last.contains(".");
    }
}
