package com.example.util;

import com.example.MappedNode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class TreeUtils {

    /**
     * Wraps our TreeNode into a MappedNode for APTED.
     */
    public static MappedNode convertToApted(com.github.javaparser.ast.Node jpNode, MappedNode parent) {
        if (jpNode == null) return null;

        String label = jpNode.getChildNodes().isEmpty()
                ? jpNode.toString().trim()
                : jpNode.getClass().getSimpleName();

        MappedNode node = new MappedNode(label, jpNode, parent);
        for (com.github.javaparser.ast.Node child : jpNode.getChildNodes()) {
            MappedNode childNode = convertToApted(child, node);
            if (childNode != null) {
                node.addChild(childNode);
            }
        }
        return node;
    }

    /**
     * Placeholder for an empty AST.
     */
    public static MappedNode emptyMappedPlaceholder() {
        return new MappedNode("EMPTY", null, null);
    }
}
