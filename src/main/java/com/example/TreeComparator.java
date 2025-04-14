package com.example;

import com.example.parser.ASTParser;
import com.example.dto.FileResult;
import com.example.util.GitUtils;
import com.example.util.TreeUtils;
import eu.mihosoft.ext.apted.node.StringNodeData;
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.jgit.revwalk.RevCommit;
import eu.mihosoft.ext.apted.node.Node;
import eu.mihosoft.ext.apted.distance.APTED;
import eu.mihosoft.ext.apted.costmodel.StringUnitCostModel;

import java.util.*;

public class TreeComparator {

    public static FileResult compareFileInTwoCommits(String localPath, RevCommit oldCommit, RevCommit newCommit, String fileName) {
        return compareFileInTwoCommits(localPath, oldCommit, newCommit, fileName, false);
    }

    public static FileResult compareFileInTwoCommits(String localPath, RevCommit oldCommit, RevCommit newCommit,
                                                     String fileName, boolean debug) {
        HashMap<Metrics, Integer> metrics = new HashMap<>();

        String oldPath = GitUtils.extractFileAtCommit(localPath, oldCommit, fileName);
        String newPath = GitUtils.extractFileAtCommit(localPath, newCommit, fileName);

        if (oldPath == null || newPath == null) {
            System.out.println("Couldn't extract both versions for: " + fileName);
            return null;
        }

        ParseTree oldTree = ASTParser.parseFile(oldPath);
        ParseTree newTree = ASTParser.parseFile(newPath);


        if (oldTree == null || newTree == null) {
            System.out.println("Skipping file due to parse failure.");
            return null;
        }


        TreeNode convertedOldTree = TreeUtils.convert(oldTree);
        TreeNode convertedNewTree = TreeUtils.convert(newTree);

        MappedNode aptedOldTree = TreeUtils.convertToApted(convertedOldTree, null);
        MappedNode aptedNewTree = TreeUtils.convertToApted(convertedNewTree, null);

        APTED<StringUnitCostModel, StringNodeData> apted = new APTED<>(new StringUnitCostModel());

        int cost = (int) apted.computeEditDistance(aptedOldTree, aptedNewTree);

        metrics.put(Metrics.TED, cost);

        if (debug) {
            printDebugInfo(aptedOldTree, aptedNewTree, cost, apted);
        }
        // Optional: Simulated operation list or placeholder
        List<EditOperation> operations = List.of(); // Replace if you implement actual operation extraction

        return createFileResult(fileName, oldPath, newPath, operations, metrics);
    }

    private static FileResult createFileResult(String fileName, String original, String changed,
                                               List<EditOperation> ops, HashMap<Metrics, Integer> metrics) {
        return FileResult.builder()
                .name(fileName)
                .editOperations(ops)
                .metrics(metrics)
                .build();
    }

    public static FileResult compareTwoFilePaths(String oldFilePath, String newFilePath) {
        return compareTwoFilePaths(oldFilePath, newFilePath, false);
    }

    public static FileResult compareTwoFilePaths(String oldFilePath, String newFilePath, boolean debug) {
        HashMap<Metrics, Integer> metrics = new HashMap<>();

        if (oldFilePath == null || newFilePath == null) {
            System.out.println("Both file paths must be provided.");
            return null;
        }

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

        metrics.put(Metrics.TED, cost);

        if (debug) {
            printDebugInfo(aptedOldTree, aptedNewTree, cost, apted);
        }
        var operations = getOperations(aptedOldTree, aptedNewTree, apted);

        String fileName = extractFileName(newFilePath);
        return createFileResult(fileName, oldFilePath, newFilePath, operations, metrics);
    }

    private static String extractFileName(String path) {
        if (path == null || path.isEmpty()) return "unknown";
        return path.substring(path.lastIndexOf('/') + 1);
    }


    private static void printDebugInfo(MappedNode oldTree, MappedNode newTree, int cost, APTED<StringUnitCostModel, StringNodeData> apted) {
        System.out.println("APTed Old Tree: " + oldTree.toString());
        System.out.println("APTed New Tree: " + newTree.toString());
        System.out.println("Edit Distance Cost: " + cost);


    }

    private static List<EditOperation> getOperations(MappedNode oldTree, MappedNode newTree, APTED<StringUnitCostModel, StringNodeData> apted){
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
                    //System.out.println("RELABELED: [" + oldNode.getNodeData().getLabel() + "] ↔ [" + newNode.getNodeData().getLabel() + "]");
                    operations.add(new EditOperation(EditOperation.Type.RELABEL,
                            toTreeNode(oldNode), toTreeNode(newNode)));
                } else {
                    //System.out.println("MATCHED: [" + oldNode.getNodeData().getLabel() + "] ↔ [" + newNode.getNodeData().getLabel() + "]");
                }
                // Flush deleteStreak if it exists
                if (!deleteStreak.isEmpty()) {
                    handleDeletedSubtree(deleteStreak, oldToNewMap, newNodes, operations);
                    deleteStreak.clear();
                }

            } else if (oldIdx >= 0) {
                MappedNode oldNode = oldNodes.get(oldIdx);
                //System.out.println("DELETED: [" + oldNode.getNodeData().getLabel() + "]");
                deleteStreak.add(oldNode);

            } else if (newIdx >= 0) {
                MappedNode newNode = newNodes.get(newIdx);
                //System.out.println("INSERTED: [" + newNode.getNodeData().getLabel() + "]");
                // Flush deleteStreak if it exists
                if (!deleteStreak.isEmpty()) {
                    handleDeletedSubtree(deleteStreak, oldToNewMap, newNodes, operations);
                    deleteStreak.clear();
                }
            }
        }

        // Handle remaining deletions
        if (!deleteStreak.isEmpty()) {
            handleDeletedSubtree(deleteStreak, oldToNewMap, newNodes, operations);
        }
        return operations;
    }

    private static TreeNode toTreeNode(MappedNode node) {
        return node.getOriginal();
    }

    private static void handleDeletedSubtree(
            List<MappedNode> deletedNodes,
            Map<Integer, Integer> oldToNewMap,
            List<MappedNode> newNodes,
            List<EditOperation> operations) {

        if (deletedNodes.isEmpty()) return;

        // Get the highest node by comparing depths
        MappedNode highestNode = deletedNodes.get(0);
        for (MappedNode node : deletedNodes) {
            if (node.getOriginal().getDepth() < highestNode.getOriginal().getDepth()) {
                highestNode = node;
            }
        }

        MappedNode parent = highestNode.getParent();
        if (parent == null) return;

        int parentIdxInOld = parent.getOriginal().getPostorderIndex() - 1;
        Integer mappedNewIdx = oldToNewMap.get(parentIdxInOld);
        if (mappedNewIdx == null || mappedNewIdx < 0) return;

        MappedNode mappedNewNode = newNodes.get(mappedNewIdx);

        List<Node<StringNodeData>> oldChildren = parent.getChildren();
        List<Node<StringNodeData>> newChildren = mappedNewNode.getChildren();

        int minSize = Math.min(oldChildren.size(), newChildren.size());
        for (int i = 0; i < minSize; i++) {
            String oldLabel = oldChildren.get(i).getNodeData().getLabel();
            String newLabel = newChildren.get(i).getNodeData().getLabel();

            MappedNode oldChild = (MappedNode) oldChildren.get(i);
            MappedNode newChild = (MappedNode) newChildren.get(i);

            if (!oldLabel.equals(newLabel)) {
                String oldText = oldChild.getOriginal().getParseTreeOriginalNode().getText();
                String newText = newChild.getOriginal().getParseTreeOriginalNode().getText();

                operations.add(new EditOperation(
                        EditOperation.Type.DELETE,
                        oldChild.getOriginal(),
                        newChild.getOriginal())
                );
                return;
            }
        }

        // If one list is longer, there's also an uncommon child
        if (oldChildren.size() != newChildren.size()) {
            if (oldChildren.size() > newChildren.size()) {
                MappedNode extra = (MappedNode) oldChildren.get(minSize);
                String text = extra.getOriginal().getParseTreeOriginalNode().getText();
                operations.add(new EditOperation(
                        EditOperation.Type.DELETE,
                        extra.getOriginal(),
                        null
                ));
            } else {
                MappedNode extra = (MappedNode) newChildren.get(minSize);
                String text = extra.getOriginal().getParseTreeOriginalNode().getText();
                operations.add(new EditOperation(
                        EditOperation.Type.INSERT,
                        null,
                        extra.getOriginal()
                ));
            }
        }
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
