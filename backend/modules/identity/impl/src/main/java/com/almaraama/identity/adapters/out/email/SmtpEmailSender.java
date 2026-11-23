package com.almaraama.identity.adapters.out.email;

import com.almaraama.identity.domain.EmailMessage;
import com.almaraama.identity.domain.EmailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends the account e-mails through the SMTP relay (ADR-031): Mailpit in development, the Algerian host's relay or
 * our Postfix in production. Plain text and HTML alternatives, UTF-8, no reply address. Host, port, credentials and
 * TLS come from {@code spring.mail.*}, fed by the {@code ALMARAAMA_SMTP_*} variables.
 */
@Component
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    /**
     * @param mailSender Spring's SMTP client
     * @param from       sender address ({@code almaraama.mail.from})
     */
    SmtpEmailSender(JavaMailSender mailSender, @Value("${almaraama.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    /** Builds a multipart message (text and HTML) and hands it to the relay; throws when the relay refuses it. */
    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(message.to().value());
            helper.setSubject(message.subject());
            helper.setText(message.text(), message.html());
            mailSender.send(mime);
        } catch (MessagingException | MailException e) {
            throw new IllegalStateException("E-mail not accepted by the relay", e);
        }
    }
}
