package com.example.util;

import com.example.MappedNode;
import com.example.TreeNode;
import lombok.Getter;
import lombok.Setter;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

@Getter @Setter
public class TreeUtils {
    public static TreeNode convert(ParseTree parseTree, CommonTokenStream tokens) {
        return convertHelper(parseTree, 0, tokens);
    }

    private static TreeNode convertHelper(ParseTree parseTree,
                                          int depth,
                                          CommonTokenStream tokens) {

        /* ────────────────────────── TERMINAL ─────────────────────────── */
        if (parseTree instanceof TerminalNode term) {
            String text = term.getText();
            if (text.matches("[;(){}\\[\\]]")) {        // trivial punctuation
                return null;
            }
            int line = term.getSymbol().getLine();
            TreeNode leaf = new TreeNode(text, parseTree, depth, line);
            leaf.setEndLine(line);                      // same line for terminals
            leaf.setTokens(tokens);
            return leaf;
        }

        /* ───── COLLAPSE CHAINS OF SINGLE NON‑TERMINAL CHILDREN ──────── */
        int skipped = 0;
        while (parseTree.getChildCount() == 1 &&
                !(parseTree.getChild(0) instanceof TerminalNode)) {
            parseTree = parseTree.getChild(0);
            skipped++;
        }
        int curDepth = depth + skipped;

        /* ───── CASE: SINGLE CHILD THAT *IS* TERMINAL ─────────────────── */
        if (parseTree.getChildCount() == 1 &&
                parseTree.getChild(0) instanceof TerminalNode t) {
            String text = t.getText();
            if (text.matches("[;(){}\\[\\]]")) {
                return null;
            }
            int line = t.getSymbol().getLine();
            TreeNode leaf = new TreeNode(text, parseTree, curDepth + 1, line);
            leaf.setEndLine(line);
            leaf.setTokens(tokens);
            return leaf;
        }

        /* ─────────────────────── NON‑TERMINAL NODE ───────────────────── */
        ParserRuleContext ctx = (ParserRuleContext) parseTree;
        String label   = parseTree.getClass().getSimpleName().replace("Context", "");
        int startLine  = ctx.getStart().getLine();        // first token line

        TreeNode node  = new TreeNode(label, parseTree, curDepth, startLine);
        node.setTokens(tokens);

        for (int i = 0; i < parseTree.getChildCount(); i++) {
            TreeNode child = convertHelper(parseTree.getChild(i), curDepth + 1, tokens);
            if (child != null) {
                node.addChild(child);
            }
        }

    /* set end‑line = last (non‑null) child’s end line,
       fallback to ctx.getStop() when there are no children               */
        if (!node.getChildren().isEmpty()) {
            int lastLine = node.getChildren()
                    .get(node.getChildren().size() - 1)
                    .getEndLine();
            node.setEndLine(lastLine);
        } else {
            node.setEndLine(ctx.getStop().getLine());
        }
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
