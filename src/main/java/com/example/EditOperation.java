package com.example;

import java.util.List;

public record EditOperation(
        EditOperation.Type type,
        TreeNode fromNode,
        TreeNode toNode,
        String method,
        List<String> context
) {

    public enum Type {INSERT, DELETE, RELABEL}

    public EditOperation(Type type, TreeNode fromNode, TreeNode toNode) {
        this(type, fromNode, toNode, null, List.of());
    }

    @Override
    public String toString() {
        String from = safeNodeText(fromNode);
        String to   = safeNodeText(toNode);

        StringBuilder sb = new StringBuilder();
        switch (type) {
            case RELABEL -> sb.append("Relabel: '").append(from).append("' -> '").append(to).append('\'');
            case INSERT  -> sb.append("Insert : '").append(from).append("' -> '").append(to).append('\'');
            case DELETE  -> sb.append("Delete : '").append(from).append("' -> '").append(to).append('\'');
            default      -> sb.append("Unknown");
        }
        sb.append("  (in ").append(method).append(')').append(System.lineSeparator());

        if (context != null && !context.isEmpty()) {
            for (String line : context) {
                sb.append("    ").append(line).append(System.lineSeparator());
            }
        }
        return sb.toString();
    }

    private static String safeNodeText(TreeNode node) {
        if (node == null) return "null (node)";
        if (node.getAstNode() != null) {
            try {
                return node.getAstNode().toString().trim();
            } catch (Exception e) {
                return "error extracting text";
            }
        }
        return node.getLabel() != null ? node.getLabel() : "null (no label)";
    }
}
