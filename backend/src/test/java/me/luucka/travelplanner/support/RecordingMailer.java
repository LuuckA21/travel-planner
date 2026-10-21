package me.luucka.travelplanner.support;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.luucka.travelplanner.auth.Mailer;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class RecordingMailer implements Mailer {

    public record SentMail(String to, String subject, String body) {}

    private static final Pattern LINK = Pattern.compile("https?://\\S+");

    private final List<SentMail> sent = new ArrayList<>();

    @Override
    public synchronized void send(String to, String subject, String body) {
        sent.add(new SentMail(to, subject, body));
    }

    public synchronized List<SentMail> sent() {
        return List.copyOf(sent);
    }

    public synchronized void clear() {
        sent.clear();
    }

    /** The URL in the latest mail to {@code email}. */
    public synchronized String lastLinkTo(String email) {
        for (int i = sent.size() - 1; i >= 0; i--) {
            if (sent.get(i).to().equals(email)) {
                Matcher m = LINK.matcher(sent.get(i).body());
                if (m.find()) {
                    return m.group();
                }
            }
        }
        throw new AssertionError("No mail with a link sent to " + email);
    }
}
