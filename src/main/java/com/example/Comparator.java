package com.example;

import com.example.parser.ASTParser;
import org.antlr.v4.runtime.tree.ParseTree;
import com.example.dto.FileResult;
import org.eclipse.jgit.revwalk.RevCommit;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class Comparator {

    public static FileResult compareFileInTwoCommits(String localPath, RevCommit oldCommit, RevCommit newCommit, String fileName) {
        return compareFileInTwoCommits(localPath, oldCommit, newCommit, fileName, false);
    }

    public static FileResult compareFileInTwoCommits(String localPath, RevCommit oldCommit, RevCommit newCommit,
                                                     String fileName, boolean debug) {
        TreeEditDistance treeEdit = new TreeEditDistance();
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
        TreeEditDistance.TEDResult tedResult = treeEdit.compare(convertedOldTree, convertedNewTree);

        if (tedResult.operations.isEmpty()) {
            metrics.put(Metrics.TED, 0);
            return createFileResult(fileName, "", "", tedResult.operations, metrics);
        }

        EditOperation highestEditOperation = getHighestEditOperation(tedResult);
        metrics.put(Metrics.TED, tedResult.cost);

        String originalFragment = resolveOriginalFragment(highestEditOperation);
        String changedFragment = resolveChangedFragment(highestEditOperation, convertedNewTree);

        if (debug) {
            printDebugInfo(convertedOldTree, convertedNewTree, tedResult, highestEditOperation);
        }

        return createFileResult(fileName, originalFragment, changedFragment, tedResult.operations, metrics);
    }

    private static EditOperation getHighestEditOperation(TreeEditDistance.TEDResult tedResult) {
        if (tedResult.operations.isEmpty()) return null;

        EditOperation highestOp = null;
        int minDepth = Integer.MAX_VALUE;

        for (EditOperation op : tedResult.operations) {
            TreeNode node = op.type == EditOperation.Type.INSERT ? op.toNode : op.fromNode;
            if (node != null && node.depth < minDepth) {
                minDepth = node.depth;
                highestOp = op;
            }
        }
        return highestOp;
    }

    private static String resolveOriginalFragment(EditOperation op) {
        if (op == null || op.fromNode == null) return "";
        return getParentContext(op.fromNode.parseTreeOriginalNode);
    }

    private static String resolveChangedFragment(EditOperation op, TreeNode newTreeRoot) {
        if (op == null) return "";

        if (op.type == EditOperation.Type.DELETE) {
            return findNewTreeContext(newTreeRoot, op.fromNode.getParent());
        }
        if (op.toNode != null) {
            return getParentContext(op.toNode.parseTreeOriginalNode);
        }
        return "";
    }

    private static String getParentContext(ParseTree node) {
        if (node == null) return "";
        ParseTree parent = node.getParent();
        return parent != null ? parent.getText() : node.getText();
    }

    private static String findNewTreeContext(TreeNode newTreeRoot, TreeNode oldNode) {
        if (oldNode == null) return "";

        // Find the nearest ancestor that exists in the new tree
        TreeNode current = oldNode;
        while (current != null) {
            TreeNode newTreeNode = findNodeByTarget(newTreeRoot, current);
            if (newTreeNode != null) {
                return getParentContext(newTreeNode.parseTreeOriginalNode);
            }
            current = current.getParent();
        }
        return "";
    }

    private static TreeNode findNodeByTarget(TreeNode root, TreeNode target) {
        if (Objects.equals(root.label, target.label)) return root;
        for (TreeNode child : root.children) {
            TreeNode found = findNodeByTarget(child, target);
            if (found != null) return found;
        }
        return null;
    }

    private static FileResult createFileResult(String fileName, String original, String changed,
                                               List<EditOperation> ops, HashMap<Metrics, Integer> metrics) {
        return FileResult.builder()
                .name(fileName)
                .changedTreeFragment(changed != null ? changed : "")
                .originalTreeFragment(original != null ? original : "")
                .editOperations(ops)
                .metrics(metrics)
                .build();
    }

    private static void printDebugInfo(TreeNode oldTree, TreeNode newTree,
                                       TreeEditDistance.TEDResult result, EditOperation highestOp) {
        System.out.println("Converted Old Tree: " + oldTree);
        System.out.println("Converted New Tree: " + newTree);
        System.out.println("Operations: " + result.operations);
        System.out.println("Highest node: " + highestOp);
    }
}