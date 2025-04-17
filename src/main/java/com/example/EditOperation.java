package com.example;

import org.antlr.v4.runtime.ParserRuleContext;

public record EditOperation(EditOperation.Type type, TreeNode fromNode, TreeNode toNode) {

    public enum Type {INSERT, DELETE, RELABEL}

    @Override
    public String toString() {
        String fromText = safeNodeText(fromNode);
        String toText = safeNodeText(toNode);

        return switch (type) {
            case RELABEL -> "Relabel: '" + fromText + "' -> '" + toText + "'";
            case INSERT -> "Insert: '" + fromText + "' -> '" + toText + "'";
            case DELETE -> "Delete: '" + fromText + "' -> '" + toText + "'";
            default -> "Unknown";
        };
    }

    private static String safeNodeText(TreeNode node) {
        if (node == null) return "null (node)";
        if (node.parseTreeOriginalNode == null) return "null (no tree)";
        if (node.getTokens() != null && node.parseTreeOriginalNode instanceof ParserRuleContext ctx) {
            try {
                return node.getTokens().getText(ctx).trim();
            } catch (Exception e) {
                return "error extracting text";
            }
        }
        return node.getLabel() != null ? node.getLabel() : "null (no label)";
    }
}
