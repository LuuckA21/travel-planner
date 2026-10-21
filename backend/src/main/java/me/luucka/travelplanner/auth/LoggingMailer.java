package me.luucka.travelplanner.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Dev fallback: no SMTP server, the mail (and its link) ends up in the log. */
@Component
@Profile("!prod")
class LoggingMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailer.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("Mail to {}: {}\n{}", to, subject, body);
    }
}
