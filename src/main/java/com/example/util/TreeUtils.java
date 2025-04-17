package com.example.util;

import com.example.MappedNode;
import com.example.TreeNode;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

public class TreeUtils {
    public static TreeNode convert(ParseTree parseTree, CommonTokenStream tokens) {
        return convertHelperOriginal(parseTree, 0, tokens);
    }
    private static TreeNode convertHelper(ParseTree parseTree, int depth, CommonTokenStream tokens) {
        if (parseTree instanceof TerminalNode) {
            String text = parseTree.getText();
            if (text.matches("[;(){}\\[\\]]")) {  // trivial tokens skipped
                return null;
            }

            TreeNode terminalNode = new TreeNode(text, parseTree, depth);
            terminalNode.setTokens(tokens); // <-- set token stream
            return terminalNode;
        }

        int levelsSkipped = 0;
        while (parseTree.getChildCount() == 1 && !(parseTree.getChild(0) instanceof TerminalNode)) {
            parseTree = parseTree.getChild(0);
            levelsSkipped++;
        }

        int currentDepth = depth + levelsSkipped;

        if (parseTree.getChildCount() == 1 && parseTree.getChild(0) instanceof TerminalNode) {
            String text = parseTree.getChild(0).getText();
            if (text.matches("[;(){}\\[\\]]")) {  // trivial tokens skipped
                return null;
            }
            TreeNode singleTerminal = new TreeNode(text, parseTree, currentDepth + 1);
            singleTerminal.setTokens(tokens); // <-- set token stream
            return singleTerminal;
        }

        String label = parseTree.getClass().getSimpleName().replace("Context", "");
        TreeNode node = new TreeNode(label, parseTree, currentDepth);
        node.setTokens(tokens); // <-- set token stream

        for (int i = 0; i < parseTree.getChildCount(); i++) {
            TreeNode child = convertHelper(parseTree.getChild(i), currentDepth + 1, tokens);
            if (child != null) { // Check if child is not trivial/skipped
                node.addChild(child);
            }
        }

        return node;
    }

    private static TreeNode convertHelperOriginal(ParseTree parseTree, int depth, CommonTokenStream tokens) {
        if (parseTree instanceof TerminalNode) {

            TreeNode terminalNode = new TreeNode(parseTree.getText(), parseTree, depth);
            terminalNode.setTokens(tokens); // <-- set token stream
            return terminalNode;
        }

        int levelsSkipped = 0;
        while (parseTree.getChildCount() == 1 && !(parseTree.getChild(0) instanceof TerminalNode)) {
            parseTree = parseTree.getChild(0);
            levelsSkipped++;
        }

        int currentDepth = depth + levelsSkipped;

        if (parseTree.getChildCount() == 1 && parseTree.getChild(0) instanceof TerminalNode) {
            String text = parseTree.getChild(0).getText();
            TreeNode singleTerminal = new TreeNode(text, parseTree, currentDepth + 1);
            singleTerminal.setTokens(tokens); // <-- set token stream
            return singleTerminal;
        }

        String label = parseTree.getClass().getSimpleName().replace("Context", "");
        TreeNode node = new TreeNode(label, parseTree, currentDepth);
        node.setTokens(tokens); // <-- set token stream

        for (int i = 0; i < parseTree.getChildCount(); i++) {
            TreeNode child = convertHelper(parseTree.getChild(i), currentDepth + 1, tokens);
            if (child != null) {
                node.addChild(child);
            }        }

        return node;
    }



    public static MappedNode convertToApted(TreeNode treeNode, MappedNode parentNode) {
        MappedNode node = new MappedNode(treeNode.getLabel(), treeNode, parentNode);
        for (TreeNode child : treeNode.getChildren()) {
            node.addChild(convertToApted(child, node));
        }
        return node;
    }

    public static MappedNode emptyMappedPlaceholder() {
        // Create a TreeNode with no real parseTree and no children
        TreeNode empty = new TreeNode("EMPTY", null, 0);
        return convertToApted(empty, null);
    }
}
