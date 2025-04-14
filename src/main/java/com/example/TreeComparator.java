package com.example;

import com.example.dto.FileResult;
import com.example.parser.ASTParser;
import com.example.util.GitUtils;
import com.example.util.TreeUtils;
import eu.mihosoft.ext.apted.costmodel.StringUnitCostModel;
import eu.mihosoft.ext.apted.distance.APTED;
import eu.mihosoft.ext.apted.node.Node;
import eu.mihosoft.ext.apted.node.StringNodeData;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.jgit.revwalk.RevCommit;

import java.util.*;

public class TreeComparator {

    public static FileResult compareFileInTwoCommits(String localPath, RevCommit oldCommit, RevCommit newCommit, String fileName) {
        return compareFileInTwoCommits(localPath, oldCommit, newCommit, fileName, false);
    }

    public static FileResult compareFileInTwoCommits(String localPath, RevCommit oldCommit, RevCommit newCommit,
                                                     String fileName, boolean debug) {
        String oldFilePath = GitUtils.extractFileAtCommit(localPath, oldCommit, fileName);
        String newFilePath = GitUtils.extractFileAtCommit(localPath, newCommit, fileName);

        if (oldFilePath == null || newFilePath == null) {
            System.out.println("Couldn't extract both versions for: " + fileName);
            return null;
        }

        return compareFiles(oldFilePath, newFilePath, fileName, debug);
    }

    public static FileResult compareTwoFilePaths(String oldFilePath, String newFilePath) {
        return compareTwoFilePaths(oldFilePath, newFilePath, false);
    }

    public static FileResult compareTwoFilePaths(String oldFilePath, String newFilePath, boolean debug) {
        if (oldFilePath == null || newFilePath == null) {
            System.out.println("Both file paths must be provided.");
            return null;
        }

        String fileName = extractFileName(newFilePath);
        return compareFiles(oldFilePath, newFilePath, fileName, debug);
    }

    private static FileResult compareFiles(String oldFilePath, String newFilePath, String fileName, boolean debug) {
        ParseTree oldTree = ASTParser.parseFile(oldFilePath);
        ParseTree newTree = ASTParser.parseFile(newFilePath);

        if (oldTree == null || newTree == null) {
            System.out.println("Skipping file due to parse failure.");
            return null;
        }

        TreeNode convertedOldTree = TreeUtils.convert(oldTree);
        TreeNode convertedNewTree = TreeUtils.convert(newTree);

        MappedNode aptedOldTree = TreeUtils.convertToApted(convertedOldTree, null);
        MappedNode aptedNewTree = TreeUtils.convertToApted(convertedNewTree, null);

        assignPostOrderNumbers(aptedOldTree, 0);
        assignPostOrderNumbers(aptedNewTree, 0);

        APTED<StringUnitCostModel, StringNodeData> apted = new APTED<>(new StringUnitCostModel());
        int cost = (int) apted.computeEditDistance(aptedOldTree, aptedNewTree);

        if (debug) {
            printDebugInfo(aptedOldTree, aptedNewTree, cost, apted);
        }

        List<EditOperation> operations = getOperations(aptedOldTree, aptedNewTree, apted);
        HashMap<Metrics, Integer> metrics = new HashMap<>();
        metrics.put(Metrics.TED, cost);

        return createFileResult(fileName, oldFilePath, newFilePath, operations, metrics);
    }

    private static FileResult createFileResult(String fileName, String original, String changed,
                                               List<EditOperation> ops, HashMap<Metrics, Integer> metrics) {
        return FileResult.builder()
                .name(fileName)
                .editOperations(ops)
                .metrics(metrics)
                .build();
    }

    private static String extractFileName(String path) {
        if (path == null || path.isEmpty()) return "unknown";
        return path.substring(path.lastIndexOf('/') + 1).replace("\\", "");
    }

    private static void printDebugInfo(MappedNode oldTree, MappedNode newTree, int cost,
                                       APTED<StringUnitCostModel, StringNodeData> apted) {
        System.out.println("APTed Old Tree: " + oldTree);
        System.out.println("APTed New Tree: " + newTree);
        System.out.println("Edit Distance Cost: " + cost);
    }

    private static List<EditOperation> getOperations(MappedNode oldTree, MappedNode newTree,
                                                     APTED<StringUnitCostModel, StringNodeData> apted) {
        List<EditOperation> operations = new ArrayList<>();
        List<int[]> mappingPairs = apted.computeEditMapping();
        List<MappedNode> oldNodes = getPostOrder(oldTree);
        List<MappedNode> newNodes = getPostOrder(newTree);
        Map<Integer, Integer> oldToNewMap = new HashMap<>();

        for (int[] pair : mappingPairs) {
            oldToNewMap.put(pair[0] - 1, pair[1] - 1);
        }

        List<MappedNode> deleteStreak = new ArrayList<>();

        for (int[] pair : mappingPairs) {
            int oldIdx = pair[0] - 1;
            int newIdx = pair[1] - 1;

            if (oldIdx >= 0 && newIdx >= 0) {
                MappedNode oldNode = oldNodes.get(oldIdx);
                MappedNode newNode = newNodes.get(newIdx);

                if (!Objects.equals(oldNode.getNodeData().getLabel(), newNode.getNodeData().getLabel())) {
                    TreeNode fromNode = toTreeNode(oldNode);
                    TreeNode toNode = toTreeNode(newNode);

                    operations.removeIf(op ->
                            op.type == EditOperation.Type.RELABEL &&
                                    isDescendant(op.fromNode, fromNode)
                    );

                    operations.add(new EditOperation(EditOperation.Type.RELABEL, fromNode, toNode));
                }

                if (!deleteStreak.isEmpty()) {
                    handleDeletedSubtree(deleteStreak, operations);
                    deleteStreak.clear();
                }

            } else if (oldIdx >= 0) {
                deleteStreak.add(oldNodes.get(oldIdx));
            } else if (newIdx >= 0 && !deleteStreak.isEmpty()) {
                handleDeletedSubtree(deleteStreak, operations);
                deleteStreak.clear();
            }
        }

        if (!deleteStreak.isEmpty()) {
            handleDeletedSubtree(deleteStreak, operations);
        }

        return operations;
    }

    private static void handleDeletedSubtree(List<MappedNode> deletedNodes, List<EditOperation> operations) {
        if (deletedNodes.isEmpty()) return;

        MappedNode highestNode = deletedNodes.stream()
                .min(Comparator.comparingInt(n -> n.getOriginal().getDepth()))
                .orElse(deletedNodes.get(0));

        Set<MappedNode> deletedSet = new HashSet<>(deletedNodes);
        MappedNode firstUncommonChild = highestNode.getChildren().stream()
                .map(child -> (MappedNode) child)
                .filter(child -> !deletedSet.contains(child))
                .findFirst()
                .orElse(null);

        if (firstUncommonChild != null) {
            operations.add(new EditOperation(
                    EditOperation.Type.DELETE,
                    highestNode.getOriginal(),
                    firstUncommonChild.getOriginal()
            ));
        }
    }

    private static boolean isDescendant(Object possibleDescendant, Object possibleAncestor) {
        if (!(possibleDescendant instanceof TreeNode descendant) ||
                !(possibleAncestor instanceof TreeNode ancestor)) return false;

        TreeNode current = descendant.getParent();
        while (current != null) {
            if (current == ancestor) return true;
            current = current.getParent();
        }
        return false;
    }

    private static TreeNode toTreeNode(MappedNode node) {
        return node.getOriginal();
    }

    private static int assignPostOrderNumbers(MappedNode node, int currentIndex) {
        for (Node<StringNodeData> child : node.getChildren()) {
            currentIndex = assignPostOrderNumbers((MappedNode) child, currentIndex);
        }
        node.getOriginal().setPostorderIndex(++currentIndex);
        return currentIndex;
    }

    public static List<MappedNode> getPostOrder(MappedNode root) {
        List<MappedNode> result = new ArrayList<>();
        fillPostOrder(root, result);
        return result;
    }

    private static void fillPostOrder(MappedNode node, List<MappedNode> list) {
        for (Node<StringNodeData> child : node.getChildren()) {
            fillPostOrder((MappedNode) child, list);
        }
        list.add(node);
    }
}
