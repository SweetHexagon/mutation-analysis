package com.example;

import eu.mihosoft.ext.apted.node.Node;
import eu.mihosoft.ext.apted.node.StringNodeData;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MappedNode extends Node<StringNodeData> {
    private final com.github.javaparser.ast.Node astNode;
    private final MappedNode parent;
    private int postorderIndex;
    private int startLine;
    private int endLine;

    public MappedNode(String label, com.github.javaparser.ast.Node astNode, MappedNode parent) {
        super(new StringNodeData(label));
        this.astNode = astNode;
        this.parent = parent;

        if (astNode != null && astNode.getRange().isPresent()) {
            this.startLine = astNode.getRange().get().begin.line;
            this.endLine = astNode.getRange().get().end.line;
        } else {
            this.startLine = -1;
            this.endLine = -1;
        }
    }
}

