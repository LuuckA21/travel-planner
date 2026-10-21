package me.luucka.travelplanner.itinerary;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

final class MapsLinks {

    private MapsLinks() {}

    /** The custom URL if given, else a Google Maps search for the location, else null. */
    static String of(String location, String mapsUrl) {
        if (mapsUrl != null && !mapsUrl.isBlank()) {
            return mapsUrl;
        }
        if (location != null && !location.isBlank()) {
            return "https://www.google.com/maps/search/?api=1&query="
                    + URLEncoder.encode(location, StandardCharsets.UTF_8).replace("+", "%20");
        }
        return null;
    }
}
