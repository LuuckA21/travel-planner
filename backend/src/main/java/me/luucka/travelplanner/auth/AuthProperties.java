package me.luucka.travelplanner.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app")
public record AuthProperties(boolean registrationOpen, String baseUrl) {}
