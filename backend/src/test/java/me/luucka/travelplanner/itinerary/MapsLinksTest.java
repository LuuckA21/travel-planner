package me.luucka.travelplanner.itinerary;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MapsLinksTest {

    @Test
    void customUrlWins() {
        assertThat(MapsLinks.of("x", "https://maps.app.goo.gl/abc")).isEqualTo("https://maps.app.goo.gl/abc");
    }

    @Test
    void encodesLocation() {
        assertThat(MapsLinks.of("Café & Bar #1, Antigua", null)).isEqualTo(
                "https://www.google.com/maps/search/?api=1&query=Caf%C3%A9%20%26%20Bar%20%231%2C%20Antigua");
    }

    @Test
    void nullWhenBothBlank() {
        assertThat(MapsLinks.of(" ", "")).isNull();
        assertThat(MapsLinks.of(null, null)).isNull();
    }
}
