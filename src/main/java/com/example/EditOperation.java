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
            case RELABEL -> "Relabel: '" + fromNode.parseTreeOriginalNode.getText() + "' -> '" + toNode.parseTreeOriginalNode.getText() + "'";
            case INSERT -> "Insert: '" + fromNode.parseTreeOriginalNode.getText() + "' -> '" + toNode.parseTreeOriginalNode.getText() + "'";
            case DELETE -> "Delete: '" + fromNode.parseTreeOriginalNode.getText() + "' -> '" + toNode.parseTreeOriginalNode.getText() + "'";
            default -> "Unknown";
        };
    }
}
