package com.example;

import com.example.cparser.CParser;
import com.example.javaparser.Java20Parser;
import com.example.parser.*;
import com.example.pojo.FileResult;
import com.example.pojo.ParsedFile;
import com.example.util.GitUtils;
import com.example.util.TreeUtils;
import com.google.common.base.Stopwatch;
import eu.mihosoft.ext.apted.costmodel.StringUnitCostModel;
import eu.mihosoft.ext.apted.distance.APTED;
import eu.mihosoft.ext.apted.node.Node;
import eu.mihosoft.ext.apted.node.StringNodeData;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;

import java.util.*;

public class TreeComparator {

    private static final Logger log = LoggerFactory.getLogger(TreeComparator.class);

    public static FileResult compareFileInTwoCommits(String localPath, RevCommit oldCommit, RevCommit newCommit, String fileName) {
        return compareFileInTwoCommits(localPath, oldCommit, newCommit, fileName, false);
    }

    public static FileResult compareFileInTwoCommits(String localPath, RevCommit oldCommit, RevCommit newCommit,
                                                     String fileName, boolean debug) {
        String oldFilePath = GitUtils.extractFileAtCommit(localPath, oldCommit, fileName);
        String newFilePath = GitUtils.extractFileAtCommit(localPath, newCommit, fileName);


        System.out.println("File path: " + oldFilePath + " -> " + newFilePath);


        if (oldFilePath == null || newFilePath == null) {
            System.out.println("Couldn't extract both versions for: " + fileName);
            return null;
        }

        return compareFiles(oldFilePath, newFilePath, fileName, oldCommit, newCommit, debug);
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


    private static FileResult compareFiles(
            String oldFilePath,
            String newFilePath,
            String fileName,
            RevCommit oldCommit,
            RevCommit newCommit,
            boolean debug) {

        Stopwatch sw = Stopwatch.createUnstarted();

        // 1) PARSING
        sw.start();
        ParsedFile oldParsed = ASTParser.parseFile(oldFilePath);
        ParsedFile newParsed = ASTParser.parseFile(newFilePath);
        sw.stop();
        if (debug) log.info("Parsing took {} ms", sw.elapsed(TimeUnit.MILLISECONDS));
        sw.reset();

        if (oldParsed == null || newParsed == null
                || oldParsed.tree == null || newParsed.tree == null) {
            log.warn("Skipping file due to parse failure: {}", fileName);
            return null;
        }

        // 2) SPLITTING INTO METHODS
        sw.start();
        List<ParseTree> oldMethods = FunctionSplitter.splitIntoFunctionTrees(oldParsed.tree, oldParsed.parser);
        List<ParseTree> newMethods = FunctionSplitter.splitIntoFunctionTrees(newParsed.tree, newParsed.parser);

        sw.stop();
        if (debug) log.info("Splitting into methods took {} ms", sw.elapsed(TimeUnit.MILLISECONDS));
        sw.reset();

        List<EditOperation> allOps = new ArrayList<>();
        int totalCost = 0;

        // 3) PER‑METHOD CONVERT + EDIT‑DISTANCE
        int count = Math.max(oldMethods.size(), newMethods.size());
        for (int i = 0; i < count; i++) {
            ParseTree o = i < oldMethods.size() ? oldMethods.get(i) : null;
            ParseTree n = i < newMethods.size() ? newMethods.get(i) : null;

            int oldPTSize = countParseTreeNodes(o);
            int newPTSize = countParseTreeNodes(n);
            if (debug) log.info("Method #{} -> parseTreeNodes old={} new={}", i, oldPTSize, newPTSize);

            String methodName = o != null
                    ? extractMethodName(o, oldParsed.parser)
                    : (n != null ? extractMethodName(n, newParsed.parser) : "<empty>");

            // a) convert to APTED trees
            sw.start();
            MappedNode mOld = (o != null
                    ? TreeUtils.convertToApted(TreeUtils.convert(o, oldParsed.tokens), null)
                    : TreeUtils.emptyMappedPlaceholder());
            assignPostOrderNumbers(mOld, 0);

            MappedNode mNew = (n != null
                    ? TreeUtils.convertToApted(TreeUtils.convert(n, newParsed.tokens), null)
                    : TreeUtils.emptyMappedPlaceholder());
            assignPostOrderNumbers(mNew, 0);
            sw.stop();
            long convertMs = sw.elapsed(TimeUnit.MILLISECONDS);

            List<MappedNode> oldNodes = getPostOrder(mOld);
            List<MappedNode> newNodes = getPostOrder(mNew);

            if (debug) log.info("Comparing method \"{}\": oldTreeNodes={} newTreeNodes={}",
                    methodName, oldNodes.size(), newNodes.size());

            sw.reset();
            // b) compute edit distance
            sw.start();
            APTED<StringUnitCostModel, StringNodeData> apted =
                    new APTED<>(new StringUnitCostModel());


            int cost = (int) apted.computeEditDistance(mOld, mNew);
            sw.stop();
            long distMs = sw.elapsed(TimeUnit.MILLISECONDS);
            sw.reset();

            totalCost += cost;

            if (debug) {
                log.info("Method #{} [{}] -> convert={} ms, editDistance={} ms (cost={}) \n",
                        i, methodName, convertMs, distMs, cost);
            }

            allOps.addAll(getOperations(mOld, mNew, apted, debug));
        }

        // 4) BUILD RESULT
        sw.start();
        HashMap<Metrics,Integer> metrics = new HashMap<>();
        metrics.put(Metrics.TED, totalCost);
        FileResult result = createFileResult(
                fileName, oldFilePath, newFilePath,
                allOps, metrics,
                oldCommit  != null ? oldCommit.getName() : null,
                newCommit  != null ? newCommit.getName() : null
        );
        sw.stop();
        if (debug) log.info("Building FileResult took {} ms", sw.elapsed(TimeUnit.MILLISECONDS));

        return result;
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
                        //System.out.println("RELABELED: [" + oldNode.getNodeData().getLabel() + "] ↔ [" + newNode.getNodeData().getLabel() + "]");
                    }

                    TreeNode fromNode = oldNode.toTreeNode();
                    TreeNode toNode = newNode.toTreeNode();

                    operations.removeIf(op ->
                            op.type() == EditOperation.Type.RELABEL &&
                                    isDescendant(op.fromNode(), fromNode)
                    );

                    relabeledNodes.add(oldNode);

                    operations.add(new EditOperation(EditOperation.Type.RELABEL, fromNode, toNode));


                } else if (debug) {
                    //System.out.println("MATCHED: [" + oldNode.getNodeData().getLabel() + "] ↔ [" + newNode.getNodeData().getLabel() + "]");
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
                    //System.out.println("DELETED: [" + oldNode.getNodeData().getLabel() + "]");
                }

                deleteStreak.add(oldNode);

                if (!insertStreak.isEmpty()) {
                    handleInsertedSubtree(insertStreak, operations);
                    insertStreak.clear();
                }
            } else if (newIdx >= 0) {
                MappedNode newNode = newNodes.get(newIdx);
                if (debug) {
                    //System.out.println("INSERTED: [" + newNode.getNodeData().getLabel() + "]");
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

    private static String extractMethodName(ParseTree tree, LanguageParser parser) {
        if (parser instanceof JavaParserImpl && tree instanceof Java20Parser.MethodDeclarationContext) {
            Java20Parser.MethodDeclarationContext ctx = (Java20Parser.MethodDeclarationContext) tree;
            return ctx.methodHeader().methodDeclarator().identifier().getText();

        } else if (parser instanceof CParserImpl && tree instanceof CParser.FunctionDefinitionContext) {
            CParser.FunctionDefinitionContext ctx = (CParser.FunctionDefinitionContext) tree;
            return ctx.declarator().directDeclarator().directDeclarator().getText();

        }
        // Add more parser-specific extraction logic here if needed.

        throw new UnsupportedOperationException("Unsupported parser type or context: " + parser.getClass().getSimpleName());
    }

    private static int countParseTreeNodes(ParseTree t) {
        if (t == null) return 0;
        int cnt = 1;                    // count this node
        for (int i = 0; i < t.getChildCount(); i++) {
            cnt += countParseTreeNodes(t.getChild(i));
        }
        return cnt;
    }



}
