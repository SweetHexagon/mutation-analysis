package com.example.util;

import com.example.MappedNode;
import com.example.TreeNode;
import com.github.javaparser.ast.Node;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class TreeUtils {

    /**
     * Converts a JavaParser AST node into our TreeNode structure.
     * @param astNode the JavaParser AST node
     * @param depth   current tree depth (start with 0)
     * @return a TreeNode representing the AST subtree, or null if skipped
     */
    public static TreeNode convert(Node astNode, int depth) {
        return convertHelper(astNode, depth);
    }

    private static TreeNode convertHelper(Node astNode, int depth) {
        if (astNode == null) return null;

        // Determine start/end lines from node range
        int startLine = astNode.getRange()
                .map(r -> r.begin.line)
                .orElse(-1);
        int endLine = astNode.getRange()
                .map(r -> r.end.line)
                .orElse(startLine);

        // Visit children
        List<Node> children = astNode.getChildNodes();

        // Determine label: leaf or non-leaf
        String label;
        if (children.isEmpty()) {
            String text = astNode.toString().trim();
            if (text.isEmpty()) {
                return null; // skip empty leaves
            }
            label = text;
        } else {
            label = astNode.getClass().getSimpleName();
        }

        // Build TreeNode
        TreeNode node = new TreeNode(label, astNode, depth, startLine);
        node.setEndLine(endLine);

        // Recurse on children
        for (Node childAst : children) {
            TreeNode childNode = convertHelper(childAst, depth + 1);
            if (childNode != null) {
                node.addChild(childNode);
            }
        }

        return node;
    }

    /**
     * Wraps our TreeNode into a MappedNode for APTED.
     */
    public static MappedNode convertToApted(TreeNode treeNode, MappedNode parentNode) {
        MappedNode node = new MappedNode(treeNode.getLabel(), treeNode, parentNode);
        for (TreeNode child : treeNode.getChildren()) {
            node.addChild(convertToApted(child, node));
        }
        return node;
    }

    /**
     * Placeholder for an empty AST.
     */
    public static MappedNode emptyMappedPlaceholder() {
        TreeNode empty = new TreeNode("EMPTY", null, 0, -1);
        return convertToApted(empty, null);
    }
}
