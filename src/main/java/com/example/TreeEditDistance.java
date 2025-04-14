package com.example;

import java.util.ArrayList;
import java.util.List;

public class TreeEditDistance {

    private final int costDel = 1;
    private final int costIns = 1;
    private final int costRel = 1;

    // We no longer need a memoization cache for recursion, since we use an iterative DP approach.
    private List<EditOperation> editOperations;

    public static class TEDResult {
        int cost;
        List<EditOperation> operations;
        TEDResult(int cost, List<EditOperation> operations) {
            this.cost = cost;
            this.operations = operations;
        }
    }

    /**
     * Compute the Tree Edit Distance between two trees (t1 -> t2) using the Zhang-Shasha algorithm.
     * Returns a TEDResult containing the minimum edit cost and the corresponding list of edit operations.
     */
    public TEDResult compare(TreeNode t1, TreeNode t2) {
        // Initialize structures
        editOperations = new ArrayList<>();
        List<TreeNode> postorderT1 = new ArrayList<>();
        List<TreeNode> postorderT2 = new ArrayList<>();
        assignPostorderIndices(t1, postorderT1);
        computeLeftBoundaryIndices(t1);
        assignPostorderIndices(t2, postorderT2);
        computeLeftBoundaryIndices(t2);

        int n = postorderT1.size();
        int m = postorderT2.size();

        // Handle edge cases where one or both trees are empty
        if (n == 0 && m == 0) {
            return new TEDResult(0, new ArrayList<>());  // both trees empty
        }
        if (n == 0) {
            // t1 is empty, so cost is m insertions (insert all nodes of t2)
            List<EditOperation> ops = new ArrayList<>();
            for (TreeNode node : postorderT2) {
                ops.add(new EditOperation(EditOperation.Type.INSERT, null, node));
            }
            editOperations = ops;
            return new TEDResult(m * costIns, ops);
        }
        if (m == 0) {
            // t2 is empty, so cost is n deletions (delete all nodes of t1)
            List<EditOperation> ops = new ArrayList<>();
            for (TreeNode node : postorderT1) {
                ops.add(new EditOperation(EditOperation.Type.DELETE, node, null));
            }
            editOperations = ops;
            return new TEDResult(n * costDel, ops);
        }

        // Prepare arrays for leftmost descendant indices (1-indexed for convenience)
        int[] left1 = new int[n + 1];
        int[] left2 = new int[m + 1];
        for (int i = 1; i <= n; i++) {
            left1[i] = postorderT1.get(i - 1).leftBoundaryIndex + 1;
        }
        for (int j = 1; j <= m; j++) {
            left2[j] = postorderT2.get(j - 1).leftBoundaryIndex + 1;
        }

        // Identify key root indices for both trees
        List<Integer> keyroots1 = new ArrayList<>();
        List<Integer> keyroots2 = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            boolean isKeyroot = true;
            for (int j = i + 1; j <= n; j++) {
                if (left1[j] == left1[i]) {
                    // A later node has the same leftmost leaf, so i is an ancestor (not a key root)
                    isKeyroot = false;
                    break;
                }
            }
            if (isKeyroot) {
                keyroots1.add(i);
            }
        }
        for (int j = 1; j <= m; j++) {
            boolean isKeyroot = true;
            for (int k = j + 1; k <= m; k++) {
                if (left2[k] == left2[j]) {
                    isKeyroot = false;
                    break;
                }
            }
            if (isKeyroot) {
                keyroots2.add(j);
            }
        }

        // DP table for tree distances and parallel table for operations
        int[][] TD = new int[n + 1][m + 1];
        List<EditOperation>[][] TDops = new List[n + 1][m + 1];
        // Initialize operation table with nulls
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= m; j++) {
                TDops[i][j] = null;
            }
        }

        // Iterate over all combinations of key roots (subtree roots)
        for (int i : keyroots1) {
            for (int j : keyroots2) {
                // Compute distance for subtree rooted at postorder index i in t1 and j in t2
                int li = left1[i];
                int lj = left2[j];
                // Forest distance table for subtrees (dimensions [0..i] x [0..j])
                int[][] forestdist = new int[i + 1][j + 1];
                List<EditOperation>[][] forestdistOps = new List[i + 1][j + 1];

                // Initialize base cases for forests:
                forestdist[0][0] = 0;
                forestdistOps[0][0] = new ArrayList<>();
                // Deletions: transform non-empty t1 prefix to empty t2
                for (int i1 = li; i1 <= i; i1++) {
                    // If we are at the left boundary of the subtree, use 0 as previous index (outside subtree)
                    int prevI = (i1 - 1 < li) ? 0 : (i1 - 1);
                    forestdist[i1][0] = forestdist[prevI][0] + costDel;
                    // Copy ops from [prevI][0] and add delete op for node (i1-1) of t1
                    List<EditOperation> opsPrev = forestdistOps[prevI][0];
                    if (opsPrev == null) opsPrev = new ArrayList<>();
                    forestdistOps[i1][0] = new ArrayList<>(opsPrev);
                    TreeNode nodeT1 = postorderT1.get(i1 - 1);
                    forestdistOps[i1][0].add(new EditOperation(EditOperation.Type.DELETE, nodeT1, null));
                }
                // Insertions: transform empty t1 to non-empty t2 prefix
                for (int j1 = lj; j1 <= j; j1++) {
                    int prevJ = (j1 - 1 < lj) ? 0 : (j1 - 1);
                    forestdist[0][j1] = forestdist[0][prevJ] + costIns;
                    // Copy ops from [0][prevJ] and add insert op for node (j1-1) of t2
                    List<EditOperation> opsPrev = forestdistOps[0][prevJ];
                    if (opsPrev == null) opsPrev = new ArrayList<>();
                    forestdistOps[0][j1] = new ArrayList<>(opsPrev);
                    TreeNode nodeT2 = postorderT2.get(j1 - 1);
                    forestdistOps[0][j1].add(new EditOperation(EditOperation.Type.INSERT, null, nodeT2));
                }

                // Fill forest distance DP for this subtree pair
                for (int i1 = li; i1 <= i; i1++) {
                    for (int j1 = lj; j1 <= j; j1++) {
                        // Calculate temporary indices for sub-forests
                        int i_temp = (left1[i] > i1 - 1) ? 0 : (i1 - 1);
                        int j_temp = (left2[j] > j1 - 1) ? 0 : (j1 - 1);

                        if (left1[i] == left1[i1] && left2[j] == left2[j1]) {
                            // Both i1 and j1 are at the root of their subtrees
                            int costMatch = postorderT1.get(i1 - 1).label.equals(postorderT2.get(j1 - 1).label) ? 0 : costRel;
                            // Three possible operations:
                            int delCost = forestdist[i_temp][j1] + costDel;
                            int insCost = forestdist[i1][j_temp] + costIns;
                            int repCost = forestdist[i_temp][j_temp] + costMatch;
                            forestdist[i1][j1] = Math.min(Math.min(delCost, insCost), repCost);

                            // Build operation list for minimum case
                            if (forestdist[i1][j1] == repCost) {
                                // Match or relabel operation chosen
                                forestdistOps[i1][j1] = new ArrayList<>();
                                if (forestdistOps[i_temp][j_temp] != null) {
                                    forestdistOps[i1][j1].addAll(forestdistOps[i_temp][j_temp]);
                                }
                                if (costMatch != 0) {
                                    TreeNode nodeT1 = postorderT1.get(i1 - 1);
                                    TreeNode nodeT2 = postorderT2.get(j1 - 1);
                                    forestdistOps[i1][j1].add(new EditOperation(EditOperation.Type.RELABEL, nodeT1, nodeT2));
                                }
                            } else if (forestdist[i1][j1] == delCost) {
                                forestdistOps[i1][j1] = new ArrayList<>();
                                if (forestdistOps[i_temp][j1] != null) {
                                    forestdistOps[i1][j1].addAll(forestdistOps[i_temp][j1]);
                                }
                                TreeNode nodeT1 = postorderT1.get(i1 - 1);
                                forestdistOps[i1][j1].add(new EditOperation(EditOperation.Type.DELETE, nodeT1, null));
                            } else {
                                // insCost is minimum
                                forestdistOps[i1][j1] = new ArrayList<>();
                                if (forestdistOps[i1][j_temp] != null) {
                                    forestdistOps[i1][j1].addAll(forestdistOps[i1][j_temp]);
                                }
                                TreeNode nodeT2 = postorderT2.get(j1 - 1);
                                forestdistOps[i1][j1].add(new EditOperation(EditOperation.Type.INSERT, null, nodeT2));
                            }

                            // Store subtree result in global table
                            TD[i1][j1] = forestdist[i1][j1];
                            TDops[i1][j1] = new ArrayList<>(forestdistOps[i1][j1]);
                        } else {
                            // Not both at subtree roots – use previously computed subtree (if any)
                            int i1_temp = left1[i1] - 1;
                            int j1_temp = left2[j1] - 1;
                            int i_temp2 = (left1[i] > i1_temp) ? 0 : i1_temp;
                            int j_temp2 = (left2[j] > j1_temp) ? 0 : j1_temp;

                            int delCost = forestdist[i_temp][j1] + costDel;
                            int insCost = forestdist[i1][j_temp] + costIns;
                            int combCost = forestdist[i_temp2][j_temp2] + TD[i1][j1];
                            forestdist[i1][j1] = Math.min(Math.min(delCost, insCost), combCost);

                            if (forestdist[i1][j1] == combCost) {
                                // Combination of a previously computed subtree edit and the rest of the forest
                                forestdistOps[i1][j1] = new ArrayList<>();
                                if (forestdistOps[i_temp2][j_temp2] != null) {
                                    forestdistOps[i1][j1].addAll(forestdistOps[i_temp2][j_temp2]);
                                }
                                if (TDops[i1][j1] != null) {
                                    forestdistOps[i1][j1].addAll(TDops[i1][j1]);
                                }
                            } else if (forestdist[i1][j1] == delCost) {
                                forestdistOps[i1][j1] = new ArrayList<>();
                                if (forestdistOps[i_temp][j1] != null) {
                                    forestdistOps[i1][j1].addAll(forestdistOps[i_temp][j1]);
                                }
                                TreeNode nodeT1 = postorderT1.get(i1 - 1);
                                forestdistOps[i1][j1].add(new EditOperation(EditOperation.Type.DELETE, nodeT1, null));
                            } else {
                                // insCost chosen
                                forestdistOps[i1][j1] = new ArrayList<>();
                                if (forestdistOps[i1][j_temp] != null) {
                                    forestdistOps[i1][j1].addAll(forestdistOps[i1][j_temp]);
                                }
                                TreeNode nodeT2 = postorderT2.get(j1 - 1);
                                forestdistOps[i1][j1].add(new EditOperation(EditOperation.Type.INSERT, null, nodeT2));
                            }
                        }
                    }
                }
                // end of filling forestdist for subtree (i, j)
            }
        }

        // The edit distance for the full trees is now at TD[n][m]
        int totalCost = TD[n][m];
        List<EditOperation> operations = (TDops[n][m] != null) ? TDops[n][m] : new ArrayList<>();
        editOperations = operations;
        return new TEDResult(totalCost, operations);
    }

    /**
     * Postorder traversal: assign postorder indices to each node and collect nodes in postorder.
     */
    private void assignPostorderIndices(TreeNode node, List<TreeNode> postorderList) {
        for (TreeNode child : node.children) {
            assignPostorderIndices(child, postorderList);
        }
        postorderList.add(node);
        node.postorderIndex = postorderList.size() - 1;
    }

    /**
     * Compute leftBoundaryIndex for each node (postorder index of leftmost leaf in its subtree).
     */
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

    public List<EditOperation> getEditOperations() {
        // Return a copy of the operations list to avoid external mutation
        return new ArrayList<>(editOperations);
    }


}
