package com.example;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

public class TreeUtils {
    public static TreeNode convert(ParseTree parseTree) {
        return convertHelper(parseTree, 0);
    }

    private static TreeNode convertHelper(ParseTree parseTree, int depth) {
        if (parseTree instanceof TerminalNode) {
            return new TreeNode(parseTree.getText(), parseTree, depth);
        }

        int levelsSkipped = 0;
        while (parseTree.getChildCount() == 1 && !(parseTree.getChild(0) instanceof TerminalNode)) {
            parseTree = parseTree.getChild(0);
            levelsSkipped++;
        }
        int currentDepth = depth + levelsSkipped;

        if (parseTree.getChildCount() == 1 && parseTree.getChild(0) instanceof TerminalNode) {
            String text = parseTree.getChild(0).getText();
            return new TreeNode(text, parseTree, currentDepth + 1);
        }

        String label = parseTree.getClass().getSimpleName().replace("Context", "");
        TreeNode node = new TreeNode(label, parseTree, currentDepth);

        for (int i = 0; i < parseTree.getChildCount(); i++) {
            TreeNode child = convertHelper(parseTree.getChild(i), currentDepth + 1);
            node.addChild(child);
        }

        return node;
    }



}
