package com.example;

import com.github.javaparser.Range;
import com.github.javaparser.ast.Node;
import spoon.reflect.declaration.CtElement;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
            // trim whitespace/newlines
            return element.toString().replaceAll("\\s+", " ").trim();
        } catch (Exception e) {
            return "<error rendering node>";
        }
    }




}
