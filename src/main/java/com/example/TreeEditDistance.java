package com.example;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

public class TreeEditDistance {

    private final int costDel = 1;
    private final int costIns = 1;
    private final int costRel = 1;

    private final Map<String, TEDResult> memo = new HashMap<>();
    private List<EditOperation> editOperations;

    public static class TEDResult {
        int cost;
        List<EditOperation> operations;

        TEDResult(int cost, List<EditOperation> operations) {
            this.cost = cost;
            this.operations = operations;
        }
    }

    public TEDResult compare(TreeNode t1, TreeNode t2) {
        memo.clear();
        editOperations = new ArrayList<>();

        List<TreeNode> postorderT1 = new ArrayList<>();
        assignPostorderIndices(t1, postorderT1);
        computeLeftBoundaryIndices(t1);

        List<TreeNode> postorderT2 = new ArrayList<>();
        assignPostorderIndices(t2, postorderT2);
        computeLeftBoundaryIndices(t2);

        int n = postorderT1.size();
        int m = postorderT2.size();

        TEDResult result = computeTED(0, n - 1, 0, m - 1, postorderT1, postorderT2);
        editOperations = result.operations;
        return result;
    }

    public List<EditOperation> getEditOperations() {
        return new ArrayList<>(editOperations);
    }

    private TEDResult computeTED(int i1, int j1, int i2, int j2, List<TreeNode> postorderT1, List<TreeNode> postorderT2) {
        String key = i1 + "," + j1 + "," + i2 + "," + j2;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }

// Base cases for the tree edit distance computation:

// Case 1: Both subtrees are empty (i1 > j1 and i2 > j2)
// - No edit operations needed, cost is 0
        if (i1 > j1 && i2 > j2) {
            TEDResult result = new TEDResult(0, new ArrayList<>());
            memo.put(key, result);
            return result;
        }

// Case 2: Subtree in t2 is empty (i2 > j2)
// - All nodes in t1's current subtree must be deleted
        else if (i2 > j2) {
            List<EditOperation> operations = new ArrayList<>();
            for (int k = i1; k <= j1; k++) {
                operations.add(new EditOperation(EditOperation.Type.DELETE, postorderT1.get(k), null));
            }
            TEDResult result = new TEDResult((j1 - i1 + 1) * costDel, operations);
            memo.put(key, result);
            return result;
        }

// Case 3: Subtree in t1 is empty (i1 > j1)
// - All nodes in t2's current subtree must be inserted
// - LIMITATION: `fromNode` is null because we don't track the parent
//   insertion point in t1 for root-level insertions
        else if (i1 > j1) {
            List<EditOperation> operations = new ArrayList<>();
            for (int k = i2; k <= j2; k++) {
                // Insertion is treated as root-level for this empty subtree
                // To track parent relationships, structural changes would be needed
                operations.add(new EditOperation(EditOperation.Type.INSERT, null, postorderT2.get(k)));
            }
            TEDResult result = new TEDResult((j2 - i2 + 1) * costIns, operations);
            memo.put(key, result);
            return result;
        }

        TreeNode r1 = postorderT1.get(j1);
        TreeNode r2 = postorderT2.get(j2);

        int l1 = r1.leftBoundaryIndex;
        int l2 = r2.leftBoundaryIndex;

        // Recursive cases
        // Case 1: Delete r1
        TEDResult delResult = computeTED(i1, j1 - 1, i2, j2, postorderT1, postorderT2);
        List<EditOperation> delOps = new ArrayList<>(delResult.operations);
        delOps.add(new EditOperation(EditOperation.Type.DELETE, r1, r2)); // should be like this, but I need that second node delOps.add(new EditOperation(EditOperation.Type.DELETE, r1, null))
        int tedDelCost = delResult.cost + costDel;

        // Case 2: Insert r2
        TEDResult insResult = computeTED(i1, j1, i2, j2 - 1, postorderT1, postorderT2);
        List<EditOperation> insOps = new ArrayList<>(insResult.operations);
        insOps.add(new EditOperation(EditOperation.Type.INSERT, r1, r2)); // should be like this, but I need that second node insOps.add(new EditOperation(EditOperation.Type.INSERT, null, r2));

        int tedInsCost = insResult.cost + costIns;

        // Case 3: Relabel r1 to r2
        int relabelCost = r1.label.equals(r2.label) ? 0 : costRel;
        TEDResult leftResult = computeTED(i1, l1 - 1, i2, l2 - 1, postorderT1, postorderT2);
        TEDResult rightResult = computeTED(l1, j1 - 1, l2, j2 - 1, postorderT1, postorderT2);
        int tedRelCost = leftResult.cost + relabelCost + rightResult.cost;

        List<EditOperation> relOps = new ArrayList<>();
        relOps.addAll(leftResult.operations);
        if (relabelCost > 0) {
            relOps.add(new EditOperation(EditOperation.Type.RELABEL, r1, r2));
        }
        relOps.addAll(rightResult.operations);

        // minimal cost
        TEDResult minResult;
        if (tedDelCost <= tedInsCost && tedDelCost <= tedRelCost) {
            minResult = new TEDResult(tedDelCost, delOps);
        } else if (tedInsCost <= tedDelCost && tedInsCost <= tedRelCost) {
            minResult = new TEDResult(tedInsCost, insOps);
        } else {
            minResult = new TEDResult(tedRelCost, relOps);
        }

        memo.put(key, minResult);
        return minResult;
    }

    private void assignPostorderIndices(TreeNode node, List<TreeNode> postorderList) {
        for (TreeNode child : node.children) {
            assignPostorderIndices(child, postorderList);
        }
        postorderList.add(node);
        node.postorderIndex = postorderList.size() - 1;
    }

    private void computeLeftBoundaryIndices(TreeNode node) {
        if (node.children.isEmpty()) {
            node.leftBoundaryIndex = node.postorderIndex;
        } else {
            for (TreeNode child : node.children) {
                computeLeftBoundaryIndices(child);
            }
            node.leftBoundaryIndex = node.children.get(0).leftBoundaryIndex;
        }
    }
}