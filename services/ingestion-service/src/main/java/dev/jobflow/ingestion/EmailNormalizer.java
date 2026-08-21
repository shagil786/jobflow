package dev.jobflow.ingestion;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class EmailNormalizer {
    static final int MAX_DECODED_BYTES = 262_144;
    static final String MAX_CONTENT_MESSAGE = "Gmail decoded content exceeds 256 KiB";

    private static final Pattern SCRIPT_OR_STYLE = Pattern.compile("(?is)<(script|style).*?>.*?</\\1>");
    private static final Pattern LINE_BREAK_TAGS =
            Pattern.compile("(?i)<br\\s*/?>|</(p|div|li|tr|td|th|h1|h2|h3|h4|h5|h6|blockquote)>");
    private static final Pattern HTML_TAGS = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern QUOTED_BOUNDARY = Pattern.compile(
            "(?im)^On .+ wrote:\\s*$|^>.*$|^-+\\s*(Forwarded message|Original Message)\\s*-+$|^From:\\s.*$");

    public NormalizedEmail normalize(String htmlOrText) {
        if (htmlOrText == null || htmlOrText.isBlank()) {
            return empty();
        }
        if (htmlOrText.getBytes(StandardCharsets.UTF_8).length > MAX_DECODED_BYTES) {
            throw new IllegalStateException(MAX_CONTENT_MESSAGE);
        }

        String visibleText = normalizeVisibleText(toVisibleText(htmlOrText));
        if (visibleText.isBlank()) {
            return empty();
        }

        Matcher quotedMatcher = QUOTED_BOUNDARY.matcher(visibleText);
        if (!quotedMatcher.find()) {
            return new NormalizedEmail(visibleText, "", null);
        }

        String plainText = visibleText.substring(0, quotedMatcher.start()).trim();
        String quotedText = stripQuotedPrefixes(visibleText.substring(quotedMatcher.start()).trim());
        return new NormalizedEmail(plainText, quotedText, null);
    }

    static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte part : hash) {
                builder.append(String.format("%02x", part));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static NormalizedEmail empty() {
        return new NormalizedEmail("", "", sha256(""));
    }

    private static String toVisibleText(String value) {
        String withoutScripts = SCRIPT_OR_STYLE.matcher(value).replaceAll(" ");
        String withLineBreaks = LINE_BREAK_TAGS.matcher(withoutScripts).replaceAll("\n");
        String withoutTags = HTML_TAGS.matcher(withLineBreaks).replaceAll(" ");
        return HtmlUtils.htmlUnescape(withoutTags).replace('\r', '\n').replace('\u00A0', ' ');
    }

    private static String normalizeVisibleText(String value) {
        String[] rawLines = value.split("\\n");
        List<String> normalizedLines = new ArrayList<>();
        for (String rawLine : rawLines) {
            String normalizedLine = rawLine == null ? "" : rawLine.replaceAll("[\\t\\x0B\\f ]+", " ").trim();
            if (normalizedLine.isEmpty()) {
                continue;
            }
            normalizedLines.add(normalizedLine);
        }
        return String.join("\n", normalizedLines).trim();
    }

    private static String stripQuotedPrefixes(String quotedText) {
        if (quotedText.isBlank()) {
            return "";
        }
        String[] lines = quotedText.split("\\n");
        List<String> normalizedLines = new ArrayList<>(lines.length);
        for (String line : lines) {
            normalizedLines.add(line.replaceFirst("^>+\\s?", "").trim());
        }
        return String.join("\n", normalizedLines).trim();
    }

    public record NormalizedEmail(String plainText, String quotedText, String contentHash) {
        public NormalizedEmail {
            plainText = plainText == null ? "" : plainText;
            quotedText = quotedText == null ? "" : quotedText;
            contentHash = contentHash == null ? sha256(extractionText(plainText, quotedText)) : contentHash;
        }

        public String extractionText() {
            return extractionText(plainText, quotedText);
        }

        private static String extractionText(String plainText, String quotedText) {
            if (plainText.isBlank()) {
                return quotedText;
            }
            if (quotedText.isBlank()) {
                return plainText;
            }
            return plainText + "\n---------- Quoted content ---------\n" + quotedText;
        }
    }
}
