package dev.jobflow.context;

import java.util.ArrayList;
import java.util.List;

final class SemanticChunker {
    private static final int MAX_CHARS = 1800;

    private SemanticChunker() {}

    static List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : text.split("\\n\\s*\\n")) {
            String value = paragraph.trim();
            if (value.isEmpty()) continue;
            if (current.length() > 0 && current.length() + value.length() + 2 > MAX_CHARS) {
                chunks.add(current.toString().trim());
                String overlap = current.substring(Math.max(0, current.length() - 220));
                current = new StringBuilder(overlap).append("\n\n");
            }
            current.append(value).append("\n\n");
        }
        if (current.length() > 0) chunks.add(current.toString().trim());
        return List.copyOf(chunks);
    }
}
