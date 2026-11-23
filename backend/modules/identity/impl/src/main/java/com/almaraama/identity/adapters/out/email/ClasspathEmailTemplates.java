package com.almaraama.identity.adapters.out.email;

import com.almaraama.identity.api.events.AccountEmailKind;
import com.almaraama.identity.domain.EmailTemplates;
import com.almaraama.shared.i18n.Language;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Account e-mail templates in the jar, one pair per kind and language (ADR-031): {@code mail/<kind>_<language>.txt}
 * whose first line is {@code Subject: …}, and {@code mail/<kind>_<language>.html}. Variables are {@code {{name}}};
 * values are HTML-escaped in the HTML body. A small home-made filler, no template engine. Moves to the text dictionary
 * once ADR-029 is validated.
 */
@Component
class ClasspathEmailTemplates implements EmailTemplates {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{([a-zA-Z]+)}}");
    private static final String SUBJECT_PREFIX = "Subject: ";

    /** Reads both files of the template and fills them; fails on a missing template or variable. */
    @Override
    public Rendered render(AccountEmailKind kind, Language language, Map<String, String> variables) {
        String base = "/mail/" + kind.name().toLowerCase(Locale.ROOT) + "_" + language.name().toLowerCase(Locale.ROOT);
        String text = read(base + ".txt");
        int endOfSubject = text.indexOf('\n');
        if (!text.startsWith(SUBJECT_PREFIX) || endOfSubject < 0) {
            throw new IllegalStateException("Template without subject line: " + base);
        }
        String subject = fill(text.substring(SUBJECT_PREFIX.length(), endOfSubject).strip(), variables, false);
        String body = fill(text.substring(endOfSubject + 1).stripLeading(), variables, false);
        String html = fill(read(base + ".html"), variables, true);
        return new Rendered(subject, body, html);
    }

    /** Replaces each {@code {{name}}}; escapes the values for HTML when asked. */
    private static String fill(String template, Map<String, String> variables, boolean html) {
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = variables.get(matcher.group(1));
            if (value == null) {
                throw new IllegalStateException("Missing template variable " + matcher.group(1));
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(html ? escape(value) : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Escapes the five HTML special characters (output encoding, security baseline). */
    static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '&' -> out.append("&amp;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** Reads a template file of the jar, UTF-8. */
    private static String read(String resource) {
        try (InputStream in = ClasspathEmailTemplates.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing template " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
