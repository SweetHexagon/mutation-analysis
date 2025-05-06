package com.example.util;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.metamodel.NodeMetaModel;
import com.github.javaparser.metamodel.PropertyMetaModel;

import java.util.List;
import java.util.stream.Collectors;

public class SingleNodeYamlPrinter {

    private static final int NUM_SPACES_FOR_INDENT = 4;
    private final boolean outputNodeType;

    public SingleNodeYamlPrinter(boolean outputNodeType) {
        this.outputNodeType = outputNodeType;
    }

    /**
     * Prints only the given node’s attributes (no children),
     * in exactly the same YAML-ish format that YamlPrinter would use.
     */
    public String output(Node node) {
        StringBuilder b = new StringBuilder();
        NodeMetaModel meta = node.getMetaModel();

        // header line (with or without the “Type=” annotation)
        if (outputNodeType) {
            b.append(meta.getTypeName());
        } else {
            b.append(node.getClass().getSimpleName());
        }
        b.append(System.lineSeparator());

        // collect only the singular “attribute” properties
        List<PropertyMetaModel> attrs = meta.getAllPropertyMetaModels().stream()
                .filter(PropertyMetaModel::isAttribute)
                .filter(PropertyMetaModel::isSingular)
                .collect(Collectors.toList());

        // indent + print each attribute
        int level = 1;
        String indent = indent(level);
        for (PropertyMetaModel a : attrs) {
            Object val = a.getValue(node);
            if (val != null) {
                b.append(indent)
                        .append(a.getName())
                        .append(": ")
                        .append(escapeValue(val.toString()))
                        .append(System.lineSeparator());
            }
        }

        return b.toString().trim();
    }

    private String indent(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level * NUM_SPACES_FOR_INDENT; i++) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private String escapeValue(String value) {
        return "\""
                + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\f", "\\f")
                .replace("\b", "\\b")
                .replace("\t", "\\t")
                + "\"";
    }
}

