package com.example;

import com.github.javaparser.ast.Node;

import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
public class TreeNode {
    private final String label;
    private final Node astNode;
    private final List<TreeNode> children = new ArrayList<>();
    private TreeNode parent;
    private int postorderIndex;
    private int depth;
    private int startLine;
    private int endLine;

    public TreeNode(String label, Node astNode, int depth, int startLine) {
        this.label = label;
        this.astNode = astNode;
        this.depth = depth;
        this.startLine = startLine;
        this.endLine = startLine;
    }

    public void addChild(TreeNode child) {
        child.parent = this;
        children.add(child);
        // keep endLine up to date
        //this.endLine = Math.max(this.endLine, child.endLine);
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    @Override
    public String toString() {
        if (children.isEmpty()) {
            return "(\"" + label + "\")";
        }
        var sb = new StringBuilder("(\"" + label + "\"");
        for (var c : children) {
            sb.append(" ").append(c.toString());
        }
        sb.append(")");
        return sb.toString();
    }
}