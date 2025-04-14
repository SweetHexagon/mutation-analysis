package com.example;

import java.util.*;

public class TreeEditDistance {

    private final int costDel = 1;
    private final int costIns = 1;
    private final int costRel = 1;

    public static class TEDResult {
        int cost;
        List<EditOperation> operations;

        TEDResult(int cost, List<EditOperation> operations) {
            this.cost = cost;
            this.operations = operations;
        }
    }

    public TEDResult compare(TreeNode t1, TreeNode t2) {
        List<TreeNode> t1Nodes = new ArrayList<>();
        List<TreeNode> t2Nodes = new ArrayList<>();

        postOrderTraversal(t1, t1Nodes);
        postOrderTraversal(t2, t2Nodes);

        Map<TreeNode, Integer> t1PostMap = new HashMap<>();
        Map<TreeNode, Integer> t2PostMap = new HashMap<>();
        for (int i = 0; i < t1Nodes.size(); i++) t1PostMap.put(t1Nodes.get(i), i + 1);
        for (int i = 0; i < t2Nodes.size(); i++) t2PostMap.put(t2Nodes.get(i), i + 1);

        int[] t1L = leftMostDescendant(t1Nodes);
        int[] t2L = leftMostDescendant(t2Nodes);

        List<Integer> t1Keyroots = computeKeyroots(t1L);
        List<Integer> t2Keyroots = computeKeyroots(t2L);

        int[][] td = new int[t1Nodes.size() + 2][t2Nodes.size() + 2];

        for (int iKey : t1Keyroots) {
            for (int jKey : t2Keyroots) {
                computeForestDistance(t1Nodes, t2Nodes, t1L, t2L, td, iKey, jKey);
            }
        }

        int finalCost = td[t1Nodes.size()][t2Nodes.size()];
        return new TEDResult(finalCost, new ArrayList<>()); // Stub: editOperations are empty
    }

    private void postOrderTraversal(TreeNode node, List<TreeNode> list) {
        for (TreeNode child : node.children) {
            postOrderTraversal(child, list);
        }
        list.add(node);
    }

    private int[] leftMostDescendant(List<TreeNode> nodes) {
        int[] lmd = new int[nodes.size() + 1];
        for (int i = 0; i < nodes.size(); i++) {
            TreeNode node = nodes.get(i);
            TreeNode current = node;
            while (!current.isLeaf()) {
                current = current.children.get(0);
            }
            lmd[i + 1] = findIndex(nodes, current) + 1;
        }
        return lmd;
    }

    private int findIndex(List<TreeNode> nodes, TreeNode node) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i) == node) return i;
        }
        return -1;
    }

    private List<Integer> computeKeyroots(int[] lmd) {
        Set<Integer> seen = new HashSet<>();
        List<Integer> keyroots = new ArrayList<>();
        for (int i = 1; i < lmd.length; i++) {
            if (!seen.contains(lmd[i])) {
                keyroots.add(i);
                seen.add(lmd[i]);
            }
        }
        return keyroots;
    }

    private void computeForestDistance(
            List<TreeNode> t1Nodes, List<TreeNode> t2Nodes,
            int[] l1, int[] l2, int[][] TD, int i, int j) {

        int m = i - l1[i] + 2;
        int n = j - l2[j] + 2;

        int[][] FD = new int[m][n];

        for (int x = 1; x < m; x++) {
            FD[x][0] = FD[x - 1][0] + costDel;
        }
        for (int y = 1; y < n; y++) {
            FD[0][y] = FD[0][y - 1] + costIns;
        }

        for (int x = 1; x < m; x++) {
            for (int y = 1; y < n; y++) {
                int t1i = l1[i] + x - 2;
                int t2j = l2[j] + y - 2;

                int cost = t1Nodes.get(t1i).label.equals(t2Nodes.get(t2j).label) ? 0 : costRel;

                if (l1[i + x - 1] == l1[i] && l2[j + y - 1] == l2[j]) {
                    FD[x][y] = Math.min(
                            Math.min(FD[x - 1][y] + costDel, FD[x][y - 1] + costIns),
                            FD[x - 1][y - 1] + cost
                    );
                    TD[i + x - 1][j + y - 1] = FD[x][y];
                } else {
                    int a = l1[i + x - 1] - l1[i];
                    int b = l2[j + y - 1] - l2[j];
                    FD[x][y] = Math.min(
                            Math.min(FD[x - 1][y] + costDel, FD[x][y - 1] + costIns),
                            FD[a][b] + TD[i + x - 1][j + y - 1]
                    );
                }
            }
        }
    }
}
