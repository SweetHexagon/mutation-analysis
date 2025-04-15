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

        if (debug) {
            System.out.println("File path: " + oldFilePath + " -> " + newFilePath);
        }

        if (oldFilePath == null || newFilePath == null) {
            System.out.println("Couldn't extract both versions for: " + fileName);
            return null;
        }

        return compareFiles(oldFilePath, newFilePath, fileName,oldCommit, newCommit, debug);
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
        return compareFiles(oldFilePath, newFilePath, fileName, null, null, debug);
    }

    private static FileResult compareFiles(String oldFilePath, String newFilePath, String fileName, RevCommit oldCommit, RevCommit newCommit, boolean debug) {
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

        List<EditOperation> operations = getOperations(aptedOldTree, aptedNewTree, apted, debug);
        HashMap<Metrics, Integer> metrics = new HashMap<>();
        metrics.put(Metrics.TED, cost);

        return createFileResult(fileName, oldFilePath, newFilePath, operations, metrics, oldCommit.getName(), newCommit.getName());
    }

    private static FileResult createFileResult(String fileName, String original, String changed,
                                               List<EditOperation> ops, HashMap<Metrics, Integer> metrics, String oldCommit, String newCommit) {
        return FileResult.builder()
                .name(fileName)
                .editOperations(ops)
                .metrics(metrics)
                .oldCommit(oldCommit)
                .newCommit(newCommit)
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
                                                     APTED<StringUnitCostModel, StringNodeData> apted,
                                                     boolean debug) {
        List<EditOperation> operations = new ArrayList<>();
        List<int[]> mappingPairs = apted.computeEditMapping();
        List<MappedNode> oldNodes = getPostOrder(oldTree);
        List<MappedNode> newNodes = getPostOrder(newTree);
        Map<Integer, Integer> oldToNewMap = new HashMap<>();

        for (int[] pair : mappingPairs) {
            oldToNewMap.put(pair[0] - 1, pair[1] - 1);
        }

        List<MappedNode> deleteStreak = new ArrayList<>();
        List<MappedNode> insertStreak = new ArrayList<>();
        Set<MappedNode> relabeledNodes = new HashSet<>();

        for (int[] pair : mappingPairs) {
            int oldIdx = pair[0] - 1;
            int newIdx = pair[1] - 1;

            if (oldIdx >= 0 && newIdx >= 0) {
                MappedNode oldNode = oldNodes.get(oldIdx);
                MappedNode newNode = newNodes.get(newIdx);

                if (!Objects.equals(oldNode.getNodeData().getLabel(), newNode.getNodeData().getLabel())) {
                    if (debug) {
                        System.out.println("RELABELED: [" + oldNode.getNodeData().getLabel() + "] ↔ [" + newNode.getNodeData().getLabel() + "]");
                    }

                    TreeNode fromNode = oldNode.toTreeNode();
                    TreeNode toNode = newNode.toTreeNode();

                    operations.removeIf(op ->
                            op.type == EditOperation.Type.RELABEL &&
                                    isDescendant(op.fromNode, fromNode)
                    );

                    relabeledNodes.add(oldNode);

                    operations.add(new EditOperation(EditOperation.Type.RELABEL, fromNode, toNode));


                } else if (debug) {
                    System.out.println("MATCHED: [" + oldNode.getNodeData().getLabel() + "] ↔ [" + newNode.getNodeData().getLabel() + "]");
                }

                if (!deleteStreak.isEmpty()) {
                    handleDeletedSubtree(deleteStreak, operations, relabeledNodes);
                    deleteStreak.clear();
                }

                if (!insertStreak.isEmpty()) {
                    handleInsertedSubtree(insertStreak, operations);
                    insertStreak.clear();
                }

            } else if (oldIdx >= 0) {
                MappedNode oldNode = oldNodes.get(oldIdx);
                if (debug) {
                    System.out.println("DELETED: [" + oldNode.getNodeData().getLabel() + "]");
                }

                deleteStreak.add(oldNode);

                if (!insertStreak.isEmpty()) {
                    handleInsertedSubtree(insertStreak, operations);
                    insertStreak.clear();
                }
            } else if (newIdx >= 0) {
                MappedNode newNode = newNodes.get(newIdx);
                if (debug) {
                    System.out.println("INSERTED: [" + newNode.getNodeData().getLabel() + "]");
                }

                insertStreak.add(newNode);

                if (!deleteStreak.isEmpty()) {
                    handleDeletedSubtree(deleteStreak, operations, relabeledNodes);
                    deleteStreak.clear();
                }

            }
        }

        if (!deleteStreak.isEmpty()) {
            handleDeletedSubtree(deleteStreak, operations, relabeledNodes);
            deleteStreak.clear();
        }

        if (!insertStreak.isEmpty()) {
            handleInsertedSubtree(insertStreak, operations);
            insertStreak.clear();
        }

        return operations;
    }

    private static void handleInsertedSubtree(List<MappedNode> insertedNodes, List<EditOperation> operations) {
        if (insertedNodes.isEmpty()) return;

        Set<MappedNode> insertedSet = new HashSet<>(insertedNodes);
        List<MappedNode> topLevelInsertions = filterInsertionRoots(insertedNodes, insertedSet);

        for (MappedNode insertedRoot : topLevelInsertions) {
            MappedNode maybeParent = insertedRoot.getParent();
            if (maybeParent != null && allChildrenInserted(maybeParent, insertedSet)) {
                // Promote relabeled parent as insertion root
                insertedRoot = maybeParent;
            }

            MappedNode firstSurvivingDescendant = findFirstNonInsertedDescendant(insertedRoot, insertedSet);
            TreeNode from = firstSurvivingDescendant != null ? firstSurvivingDescendant.getTreeNode() : null;
            TreeNode to = insertedRoot.getTreeNode();

            operations.add(new EditOperation(EditOperation.Type.INSERT, from, to));
        }
    }

    private static List<MappedNode> filterInsertionRoots(List<MappedNode> insertedNodes, Set<MappedNode> insertedSet) {
        List<MappedNode> roots = new ArrayList<>();

        for (MappedNode node : insertedNodes) {
            boolean isDescendant = false;
            MappedNode parent = node.getParent();

            while (parent != null) {
                if (insertedSet.contains(parent)) {
                    isDescendant = true;
                    break;
                }
                parent = parent.getParent();
            }

            if (!isDescendant) {
                roots.add(node);
            }
        }

        return roots;
    }

    private static MappedNode findFirstNonInsertedDescendant(MappedNode root, Set<MappedNode> insertedSet) {
        Queue<MappedNode> queue = new LinkedList<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            MappedNode current = queue.poll();

            for (Node<StringNodeData> child : current.getChildren()) {
                MappedNode mappedChild = (MappedNode) child;

                if (!insertedSet.contains(mappedChild)) {
                    return mappedChild;
                }

                queue.add(mappedChild);
            }
        }

        return null;
    }

    private static void handleDeletedSubtree(List<MappedNode> deletedNodes,
                                             List<EditOperation> operations,
                                             Set<MappedNode> relabeledNodes) {
        if (deletedNodes.isEmpty()) return;

        Set<MappedNode> deletedSet = new HashSet<>(deletedNodes);
        List<MappedNode> topLevelDeletions = filterDeletionRoots(deletedNodes, deletedSet, relabeledNodes);

        for (MappedNode deletedRoot : topLevelDeletions) {
            MappedNode firstRemainingDescendant = findFirstNonDeletedDescendant(deletedRoot, deletedSet);
            TreeNode from = deletedRoot.getTreeNode();
            TreeNode to = firstRemainingDescendant != null ? firstRemainingDescendant.getTreeNode() : null;

            operations.add(new EditOperation(EditOperation.Type.DELETE, from, to));
        }
    }


    private static List<MappedNode> filterDeletionRoots(
            List<MappedNode> deletedNodes,
            Set<MappedNode> deletedSet,
            Set<MappedNode> relabeledNodes
    ) {
        List<MappedNode> roots = new ArrayList<>();
        Set<MappedNode> visited = new HashSet<>();

        for (MappedNode node : deletedNodes) {
            if (visited.contains(node)) continue;

            boolean isDescendant = false;
            MappedNode parent = node.getParent();

            while (parent != null) {
                if (deletedSet.contains(parent)) {
                    isDescendant = true;
                    break;
                }
                parent = parent.getParent();
            }

            if (!isDescendant) {
                MappedNode maybeParent = node.getParent();
                if (maybeParent != null && relabeledNodes.contains(maybeParent)
                        && allChildrenDeleted(maybeParent, deletedSet)) {

                    roots.add(maybeParent);
                    for (Node<StringNodeData> child : maybeParent.getChildren()) {
                        visited.add((MappedNode) child);
                    }
                } else {
                    roots.add(node);
                }
            }
        }

        return roots;
    }

    private static boolean allChildrenDeleted(MappedNode parent, Set<MappedNode> deletedSet) {
        for (Node<StringNodeData> child : parent.getChildren()) {
            if (!deletedSet.contains((MappedNode) child)) {
                return false;
            }
        }
        return true;
    }

    private static boolean allChildrenInserted(MappedNode parent, Set<MappedNode> insertedSet) {
        for (Node<StringNodeData> child : parent.getChildren()) {
            if (!insertedSet.contains((MappedNode) child)) {
                return false;
            }
        }
        return true;
    }


    private static MappedNode findFirstNonDeletedDescendant(MappedNode root, Set<MappedNode> deletedSet) {
        Queue<MappedNode> queue = new LinkedList<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            MappedNode current = queue.poll();

            for (Node<StringNodeData> child : current.getChildren()) {
                MappedNode mappedChild = (MappedNode) child;

                if (!deletedSet.contains(mappedChild)) {
                    return mappedChild;
                }

                queue.add(mappedChild);
            }
        }

        return null;
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

    private static int assignPostOrderNumbers(MappedNode node, int currentIndex) {
        for (Node<StringNodeData> child : node.getChildren()) {
            currentIndex = assignPostOrderNumbers((MappedNode) child, currentIndex);
        }
        node.getTreeNode().setPostorderIndex(++currentIndex);
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
