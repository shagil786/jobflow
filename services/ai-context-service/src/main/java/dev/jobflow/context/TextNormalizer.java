package dev.jobflow.context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

final class TextNormalizer {
    private TextNormalizer() {}

    static String normalize(String input) {
        if (input == null) return "";
        String text = input.replaceAll("(?is)<script[^>]*>.*?</script>|<style[^>]*>.*?</style>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replaceAll("(?m)^\\s*(>+|on .* wrote:|from:|sent:|subject:).*$", " ")
                .replaceAll("\\r", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\\n\\n")
                .trim();
        return text.length() > 100_000 ? text.substring(0, 100_000) : text;
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("content hashing unavailable", exception);
        }
    }
}
