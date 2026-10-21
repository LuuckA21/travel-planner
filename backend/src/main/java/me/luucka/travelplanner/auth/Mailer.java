package me.luucka.travelplanner.auth;

public interface Mailer {
    void send(String to, String subject, String body);
}
