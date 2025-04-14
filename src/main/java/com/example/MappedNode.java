package com.example;

import eu.mihosoft.ext.apted.node.Node;
import eu.mihosoft.ext.apted.node.StringNodeData;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MappedNode extends Node<StringNodeData> {
    private final TreeNode original;
    private final MappedNode parent;
    public MappedNode(String label, TreeNode original, MappedNode parent) {
        super(new StringNodeData(label));
        this.original = original;
        this.parent = parent;
    }

}
