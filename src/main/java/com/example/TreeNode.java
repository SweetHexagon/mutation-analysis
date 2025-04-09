package com.example;

import lombok.Getter;
import lombok.Setter;
import org.antlr.v4.runtime.tree.ParseTree;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class TreeNode {
    public String label;
    public List<TreeNode> children = new ArrayList<>();
    public ParseTree parseTreeOriginalNode;
    public TreeNode parent;
    public int postorderIndex;
    public int leftBoundaryIndex;
    public int depth;


    public TreeNode(String label, ParseTree parseTreeOriginalNode, int depth) {
        this.label = label;
        this.parseTreeOriginalNode = parseTreeOriginalNode;
        this.depth = depth;
    }

    public void addChild(TreeNode child) {
        child.parent = this;
        children.add(child);
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public int size() {
        int total = 1;
        for (TreeNode child : children) {
            total += child.size();
        }
        return total;
    }

    @Override
    public String toString() {
        return toStringHelper(this);
    }

    private String toStringHelper(TreeNode node) {
        if (node.getChildren().isEmpty()) {
            return "( \"" + node.getLabel() + "\" )";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("( \"").append(node.getLabel()).append("\" ");
        for (TreeNode child : node.getChildren()) {
            sb.append(toStringHelper(child)).append(" ");
        }
        sb.setLength(sb.length() - 1);
        sb.append(")");
        return sb.toString();
    }
}