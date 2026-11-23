package com.almaraama.identity.adapters.out.email;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * The SMTP client of the account e-mails (ADR-031), configured from {@code almaraama.mail.smtp.*} (variables {@code
 * ALMARAAMA_SMTP_*}). The relay password is read from a secret file, never from the image or the repository. Short
 * timeouts, so that a stuck relay cannot hold a request for long; the outbox retries later.
 */
@Configuration(proxyBeanMethods = false)
class SmtpConfiguration {

    private static final String TIMEOUT_MS = "10000";

    /**
     * @param host              relay host ({@code mailpit} in development)
     * @param port              relay port
     * @param username          relay account, empty for none
     * @param passwordFile      secret file holding the relay password, empty for none
     * @param starttlsRequired  refuse to send without TLS to the relay ({@code true} outside development)
     */
    @Bean
    JavaMailSender accountMailSender(
            @Value("${almaraama.mail.smtp.host}") String host,
            @Value("${almaraama.mail.smtp.port}") int port,
            @Value("${almaraama.mail.smtp.username:}") String username,
            @Value("${almaraama.mail.smtp.password-file:}") String passwordFile,
            @Value("${almaraama.mail.smtp.starttls-required:true}") boolean starttlsRequired) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.connectiontimeout", TIMEOUT_MS);
        properties.put("mail.smtp.timeout", TIMEOUT_MS);
        properties.put("mail.smtp.writetimeout", TIMEOUT_MS);
        properties.put("mail.smtp.starttls.enable", String.valueOf(starttlsRequired));
        properties.put("mail.smtp.starttls.required", String.valueOf(starttlsRequired));
        if (!username.isBlank()) {
            sender.setUsername(username);
            sender.setPassword(readSecret(passwordFile));
            properties.put("mail.smtp.auth", "true");
        }
        return sender;
    }

    /** The relay password from its secret file; empty when no file is given. */
    private static String readSecret(String file) {
        if (file == null || file.isBlank()) {
            return "";
        }
        try {
            return Files.readString(Path.of(file), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException("SMTP password file not readable", e);
        }
    }
}
