package com.example;

public class EditOperation {
    public enum Type { INSERT, DELETE, RELABEL }

    public final Type type;
    public final TreeNode fromNode;
    public final TreeNode toNode;

    public EditOperation(Type type, TreeNode fromNode, TreeNode toNode) {
        this.type = type;
        this.fromNode = fromNode;
        this.toNode = toNode;
    }

    @Override
    public String toString() {
        return switch (type) {
            case RELABEL -> "Relabel: '" + fromNode.label + "' -> '" + toNode.label + "'";
            case INSERT -> "Insert: '" + toNode.label + "'";
            case DELETE -> "Delete: '" + fromNode.label + "'";
            default -> "Unknown";
        };
    }
}
