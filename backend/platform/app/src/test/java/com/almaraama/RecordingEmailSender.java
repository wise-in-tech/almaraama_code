package com.almaraama;

import com.almaraama.identity.domain.EmailMessage;
import com.almaraama.identity.domain.EmailSender;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test adapter of the {@link EmailSender} port (ADR-031): keeps the e-mails in memory instead of sending them, so the
 * tests can read the link a person would receive and check that nothing else leaves (criterion 7: no SMS at all).
 */
class RecordingEmailSender implements EmailSender {

    private static final Pattern LINK = Pattern.compile("(https?://\\S+?/(invitation|verify-email|reset-password))#(\\S+)");

    private static final Duration WAIT = Duration.ofSeconds(10);

    private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

    /** Registers the recorder in place of the SMTP adapter for every integration test. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        /** The recorder, preferred to the SMTP adapter. */
        @Bean
        @Primary
        RecordingEmailSender recordingEmailSender() {
            return new RecordingEmailSender();
        }
    }

    /** Records the message. */
    @Override
    public void send(EmailMessage message) {
        sent.add(message);
    }

    /** Every message sent to this address, oldest first. */
    List<EmailMessage> to(String email) {
        return sent.stream().filter(m -> m.to().value().equalsIgnoreCase(email)).toList();
    }

    /**
     * The secret of the last link of this kind ({@code invitation}, {@code verify-email}, {@code reset-password}),
     * waiting up to 10 seconds: e-mails leave asynchronously after the commit (ADR-031).
     */
    Optional<String> lastSecret(String email, String path) {
        Instant deadline = Instant.now().plus(WAIT);
        Optional<String> found = findSecret(email, path);
        while (found.isEmpty() && Instant.now().isBefore(deadline)) {
            pause();
            found = findSecret(email, path);
        }
        return found;
    }

    /** Waits until this address received at least {@code count} messages (or 10 seconds) and returns them. */
    List<EmailMessage> awaitAtLeast(String email, int count) {
        Instant deadline = Instant.now().plus(WAIT);
        while (to(email).size() < count && Instant.now().isBefore(deadline)) {
            pause();
        }
        return to(email);
    }

    /** Lets the asynchronous listeners run, then returns what this address received (to check nothing more came). */
    List<EmailMessage> settled(String email) {
        for (int i = 0; i < 10; i++) {
            pause();
        }
        return to(email);
    }

    /** A short pause while the asynchronous listeners work. */
    private static void pause() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The secret of the last link of this kind received so far. */
    private Optional<String> findSecret(String email, String path) {
        List<EmailMessage> mine = to(email);
        for (int i = mine.size() - 1; i >= 0; i--) {
            Matcher matcher = LINK.matcher(mine.get(i).text());
            while (matcher.find()) {
                if (matcher.group(2).equals(path)) {
                    return Optional.of(matcher.group(3));
                }
            }
        }
        return Optional.empty();
    }
}
