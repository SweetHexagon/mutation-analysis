package com.example.util;

import com.example.EditOperation;
import com.example.MappedNode;
import com.github.javaparser.Position;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.metamodel.NodeMetaModel;
import com.github.javaparser.metamodel.PropertyMetaModel;
import eu.mihosoft.ext.apted.node.StringNodeData;
import lombok.Getter;
import lombok.Setter;
import spoon.reflect.declaration.CtElement;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

    public static Optional<Node> findJavaParserNode(File file, CtElement spoonNode) throws IOException {
        if (spoonNode == null || !spoonNode.getPosition().isValidPosition()) {
            return Optional.empty();
        }

        // 1) read & parse
        String code = Files.readString(file.toPath());
        CompilationUnit cu = StaticJavaParser.parse(code);

        // 2) normalize the snippet
        String targetSrc = spoonNode.toString()
                .replaceAll("\\s+", " ")
                .trim();

        // 3) spoon position
        int spoonLine = spoonNode.getPosition().getLine();
        int spoonCol  = spoonNode.getPosition().getColumn();

        // 4) find *all* text‐matching nodes, but also check their JavaParser Range sits on the same line
        List<Node> candidates = cu.findAll(Node.class).stream()
                .filter(n -> {
                    // text match
                    String norm = n.toString().replaceAll("\\s+", " ").trim();
                    if (!norm.equals(targetSrc)) return false;

                    // has a range?
                    Optional<Range> rOpt = n.getRange();
                    if (rOpt.isEmpty()) return false;

                    // does this node begin on the same line/column (or contain it)?
                    Range r = rOpt.get();
                    boolean sameLine = (r.begin.line == spoonLine);
                    boolean contains  = (r.begin.isBefore(new Position(spoonLine, spoonCol)) ||
                            r.begin.equals(new Position(spoonLine, spoonCol)))
                            && (r.end.isAfter(new Position(spoonLine, spoonCol)) ||
                            r.end.equals(new Position(spoonLine, spoonCol)));
                    return sameLine || contains;
                })
                .toList();

        // 5) pick the best candidate: smallest span (tightest match)
        return candidates.stream()
                .min(Comparator.comparingInt(n -> {
                    Range r = n.getRange().get();
                    // compute span in characters (approx)
                    int lineSpan = r.end.line   - r.begin.line;
                    int colSpan  = r.end.column - r.begin.column;
                    return lineSpan * 1000 + colSpan;
                }));
    }

    public static Optional<Node> findJavaParserNode(File file, CtElement srcNode, CtElement dstNode , boolean useSrc) throws IOException {
        CtElement element = useSrc ? srcNode : dstNode;
        return findJavaParserNode(file, element);
    }

    public static List<String> extractCtElementContext(
            File file,
            CtElement element,
            int radius
    ) throws IOException {
        if (radius == 0){
            return Arrays.asList( element.toString().replaceAll("\\b([a-zA-Z_][\\w$]*\\.)+([A-Z][\\w$]*)", "$2").split("\\R") );
        }
        List<String> all = Files.readAllLines(file.toPath());
        if (!element.getPosition().isValidPosition()) {
            return List.of();
        }
        int startLine = element.getPosition().getLine();
        int endLine   = element.getPosition().getEndLine();
        // expand by radius
        int begin = Math.max(1, startLine - radius);
        int finish = Math.min(all.size(), endLine + radius);
        // subList is 0-based, end-exclusive
        return all.subList(begin - 1, finish);
    }

}
