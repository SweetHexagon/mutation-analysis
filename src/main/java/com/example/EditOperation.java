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
            case RELABEL -> "Relabel: '" +
                    (fromNode != null && fromNode.parseTreeOriginalNode != null ? fromNode.parseTreeOriginalNode.getText() : "null") +
                    "' -> '" +
                    (toNode != null && toNode.parseTreeOriginalNode != null ? toNode.parseTreeOriginalNode.getText() : "null") +
                    "'";
            case INSERT -> "Insert: '" +
                    (fromNode != null && fromNode.parseTreeOriginalNode != null ? fromNode.parseTreeOriginalNode.getText() : "null") +
                    "' -> '" +
                    (toNode != null && toNode.parseTreeOriginalNode != null ? toNode.parseTreeOriginalNode.getText() : "null") +
                    "'";
            case DELETE -> "Delete: '" +
                    (fromNode != null && fromNode.parseTreeOriginalNode != null ? fromNode.parseTreeOriginalNode.getText() : "null") +
                    "' -> '" +
                    (toNode != null && toNode.parseTreeOriginalNode != null ? toNode.parseTreeOriginalNode.getText() : "null") +
                    "'";
            default -> "Unknown";
        };

    }
}
