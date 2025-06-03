package com.example;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import spoon.reflect.declaration.CtElement;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single AST edit operation detected by the GumTree Spoon comparator.
 * Supports both source (before) and destination (after) nodes.
 */
public record EditOperation(
        Type type,
        CtElement srcNode,
        CtElement dstNode,
        Node srcJavaNode,
        Node dstJavaNode,
        String method,
        List<String> context
) {

    public enum Type { INSERT, DELETE, UPDATE, MOVE }
    public static final String BEFORE_MARKER = "--- before ---";
    public static final String AFTER_MARKER  = "--- after ---";


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.name());

        // show src -> dst
        String fromText = safeNodeText(srcNode);
        String toText   = safeNodeText(dstNode);
        sb.append(": '").append(fromText)
                .append("' -> '").append(toText).append("'");

        if (method != null) {
            sb.append("  (in ").append(method).append(')');
        }
        sb.append(System.lineSeparator());

        // any extra context lines
        if (context != null && !context.isEmpty()) {
            for (String line : context) {
                sb.append("    ").append(line).append(System.lineSeparator());
            }
        }
        return sb.toString();
    }

    private static String safeNodeText(CtElement element) {
        if (element == null) return "<null>";
        try {
            // Trim whitespace/newlines
            String raw = element.toString().replaceAll("\\s+", " ").trim();

            // Remove fully qualified class names (keep only the class name)
            return raw.replaceAll("\\b([a-zA-Z_][\\w$]*\\.)+([A-Z][\\w$]*)", "$2");
        } catch (Exception e) {
            return "<error rendering node>";
        }
    }

    public List<String> extractBeforeContext(){
        List<String> result = new ArrayList<>();
        boolean inBeforeSection = false;
        for (String line : context) {
            if (line.trim().equals(BEFORE_MARKER)) {
                inBeforeSection = true;
                continue;
            }
            if (line.trim().equals(AFTER_MARKER)) {
                break;
            }
            if (inBeforeSection) {
                result.add(line);
            }
        }
        return result;
    }
    public List<String> extractAfterContext() {
        List<String> result = new ArrayList<>();
        boolean inAfterSection = false;
        for (String line : context) {
            if (inAfterSection) {
                result.add(line);
            }
            if (line.trim().equals(AFTER_MARKER)) {
                inAfterSection = true;
            }
        }
        return result;
    }

}
