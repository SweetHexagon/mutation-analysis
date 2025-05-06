package com.example.util;

import com.example.MappedNode;
import com.github.javaparser.ast.Node;
import com.github.javaparser.metamodel.NodeMetaModel;
import com.github.javaparser.metamodel.PropertyMetaModel;
import eu.mihosoft.ext.apted.node.StringNodeData;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import static java.util.stream.Collectors.toList;

@Getter @Setter
public class TreeUtils {

    /**
     * Wraps our TreeNode into a MappedNode for APTED.
     */
    public static MappedNode convertToApted(Node jpNode, MappedNode parent) {
        if (jpNode == null) return null;

        String label = getNodeInfo(jpNode);

        MappedNode node = new MappedNode(label, jpNode, parent);
        for (Node child : jpNode.getChildNodes()) {
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

    /**
     * Returns a label for the AST node, including its type and any simple attributes,
     * with values escaped the same way as YamlPrinter.
     */
    public static String getNodeInfo(Node node) {
        NodeMetaModel metaModel = node.getMetaModel();
        List<PropertyMetaModel> allProps = metaModel.getAllPropertyMetaModels();
        List<PropertyMetaModel> attributes = allProps.stream()
                .filter(PropertyMetaModel::isAttribute)
                .filter(PropertyMetaModel::isSingular)
                .collect(toList());

        String typeName = metaModel.getTypeName();
        if (attributes.isEmpty()) {
            return typeName;
        }

        StringBuilder sb = new StringBuilder(typeName);
        sb.append("(");
        for (int i = 0; i < attributes.size(); i++) {
            PropertyMetaModel a = attributes.get(i);
            Object valueObj = a.getValue(node);
            String value = valueObj != null ? valueObj.toString() : "";
            sb.append(a.getName()).append("=").append(escapeValue(value));
            if (i < attributes.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Escape a string value for inclusion in a label, matching YamlPrinter.escapeValue logic.
     */
    private static String escapeValue(String value) {
        return "\""
                + value.replace("\\", "\\\\")
                .replaceAll("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\f", "\\f")
                .replace("\b", "\\b")
                .replace("\t", "\\t")
                + "\"";
    }

    /**
     * Prints a tree of MappedNode objects in an indented format.
     */
    public static void printMappedTree(MappedNode root) {
        printMappedTreeRecursive(root, 0);
    }

    private static void printMappedTreeRecursive(MappedNode node, int indentLevel) {
        if (node == null) return;

        String indent = " ".repeat(indentLevel * 2);
        String label = node.getNodeData().getLabel(); // Get label from StringNodeData
        System.out.println(indent + "- " + label);

        for (eu.mihosoft.ext.apted.node.Node<StringNodeData> child :  node.getChildren()) {
            printMappedTreeRecursive((MappedNode) child, indentLevel + 1);
        }
    }

}
