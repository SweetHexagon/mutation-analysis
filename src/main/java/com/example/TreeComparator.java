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
import org.antlr.v4.runtime.tree.ParseTree;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import java.util.*;

public class TreeComparator {


    private static final boolean SHOW_DEEP_CONTEXT = false;

    private static final int CONTEXT_MARGIN = SHOW_DEEP_CONTEXT ? 0 : 1;


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

        String fileName = extractName(oldFilePath,newFilePath);
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

            try {
                allOps.addAll(getOperations(
                        mOld, mNew, apted,
                        Paths.get(oldFilePath), Paths.get(newFilePath),
                        methodName, debug));
            } catch (IOException io) {
                log.error("Failed to build edit‑operation contexts for {}", fileName, io);
            }
        }

        // 4) BUILD RESULT
        sw.start();
        HashMap<Metrics,Integer> metrics = new HashMap<>();
        metrics.put(Metrics.TREE_EDIT_DISTANCE, totalCost);
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

//----

// (Add this to the existing FileComparator class)

    private static FileResult createFileResult(String fileName, String original, String changed,
                                               List<EditOperation> ops, HashMap<Metrics, Integer> metrics,
                                               String oldCommit, String newCommit) {
        return FileResult.builder()
                .name(fileName)
                .editOperations(ops)
                .metrics(metrics)
                .oldCommit(oldCommit)
                .newCommit(newCommit)
                .build();
    }

    private static void printDebugInfo(MappedNode oldTree, MappedNode newTree, int cost,
                                       APTED<StringUnitCostModel, StringNodeData> apted) {
        System.out.println("APTed Old Tree: " + oldTree);
        System.out.println("APTed New Tree: " + newTree);
        System.out.println("Edit Distance Cost: " + cost);
    }

    private static List<EditOperation> getOperations(MappedNode oldTree,
                                                     MappedNode newTree,
                                                     APTED<StringUnitCostModel, StringNodeData> apted,
                                                     Path oldFile,
                                                     Path newFile,
                                                     String methodName,
                                                     boolean debug) throws IOException {

        List<EditOperation> raw = new ArrayList<>();
        List<int[]> mappingPairs = apted.computeEditMapping();
        List<MappedNode> oldNodes = getPostOrder(oldTree);
        List<MappedNode> newNodes = getPostOrder(newTree);

        List<MappedNode> deleteStreak = new ArrayList<>();
        List<MappedNode> insertStreak = new ArrayList<>();
        Set<MappedNode> relabeled = new HashSet<>();

        for (int[] p : mappingPairs) {
            int oldIdx = p[0] - 1, newIdx = p[1] - 1;

            if (oldIdx >= 0 && newIdx >= 0) {
                MappedNode o = oldNodes.get(oldIdx);
                MappedNode n = newNodes.get(newIdx);

                if (!Objects.equals(o.getNodeData().getLabel(), n.getNodeData().getLabel())) {
                    TreeNode from = o.toTreeNode();
                    TreeNode to = n.toTreeNode();

                    raw.removeIf(op -> op.type() == EditOperation.Type.RELABEL
                            && isDescendant(op.fromNode(), from));

                    relabeled.add(o);
                    raw.add(new EditOperation(EditOperation.Type.RELABEL, from, to));
                }

                if (!deleteStreak.isEmpty()) {
                    handleDeletedSubtree(deleteStreak, raw, relabeled);
                    deleteStreak.clear();
                }
                if (!insertStreak.isEmpty()) {
                    handleInsertedSubtree(insertStreak, raw);
                    insertStreak.clear();
                }

            } else if (oldIdx >= 0) {
                deleteStreak.add(oldNodes.get(oldIdx));

                if (!insertStreak.isEmpty()) {
                    handleInsertedSubtree(insertStreak, raw);
                    insertStreak.clear();
                }

            } else if (newIdx >= 0) {
                insertStreak.add(newNodes.get(newIdx));

                if (!deleteStreak.isEmpty()) {
                    handleDeletedSubtree(deleteStreak, raw, relabeled);
                    deleteStreak.clear();
                }
            }
        }

        if (!deleteStreak.isEmpty()) handleDeletedSubtree(deleteStreak, raw, relabeled);
        if (!insertStreak.isEmpty()) handleInsertedSubtree(insertStreak, raw);

        List<EditOperation> enriched = new ArrayList<>(raw.size());

        for (EditOperation op : raw) {
            TreeNode anchor = switch (op.type()) {
                case RELABEL -> lca(op.fromNode(), op.toNode());
                case DELETE -> SHOW_DEEP_CONTEXT
                        ? op.fromNode() != null ? op.fromNode().getParent() : null
                        : op.fromNode();
                case INSERT -> SHOW_DEEP_CONTEXT
                        ? op.toNode() != null ? op.toNode().getParent() : null
                        : op.toNode();
            };

            if (anchor == null) anchor = (op.fromNode() != null ? op.fromNode() : op.toNode());

            Path source = (op.type() == EditOperation.Type.INSERT) ? newFile : oldFile;
            List<String> ctx = context(source, anchor, CONTEXT_MARGIN);

            enriched.add(new EditOperation(
                    op.type(), op.fromNode(), op.toNode(), methodName, ctx));
        }

        return enriched;
    }

    private static void handleInsertedSubtree(List<MappedNode> insertedNodes,
                                              List<EditOperation> operations) {
        if (insertedNodes.isEmpty()) return;

        Set<MappedNode> insertedSet = new HashSet<>(insertedNodes);
        List<MappedNode> topLevelInsertions = filterInsertionRoots(insertedNodes, insertedSet);

        for (MappedNode insertedRoot : topLevelInsertions) {
            MappedNode maybeParent = insertedRoot.getParent();
            if (maybeParent != null && allChildrenInserted(maybeParent, insertedSet)) {
                insertedRoot = maybeParent;
            }

            MappedNode firstSurvivingDescendant = findFirstNonInsertedDescendant(insertedRoot, insertedSet);
            TreeNode from = firstSurvivingDescendant != null ? firstSurvivingDescendant.getTreeNode() : null;
            TreeNode to = insertedRoot.getTreeNode();

            operations.add(new EditOperation(EditOperation.Type.INSERT, from, to));
        }
    }

    private static List<MappedNode> filterInsertionRoots(List<MappedNode> insertedNodes,
                                                         Set<MappedNode> insertedSet) {
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

    private static MappedNode findFirstNonInsertedDescendant(MappedNode root,
                                                             Set<MappedNode> insertedSet) {
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

    // (Add this to the existing FileComparator class)

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
        if (parser instanceof JavaParserImpl) {
            return javaMethodName(tree);
        }

        if (parser instanceof CParserImpl
                && tree instanceof CParser.FunctionDefinitionContext) {
            return cMethodName((CParser.FunctionDefinitionContext) tree);
        }

        throw new UnsupportedOperationException(
                "Unsupported parser/context combo: " + parser.getClass().getSimpleName());
    }

    private static String javaMethodName(ParseTree node) {
        if (node instanceof Java20Parser.MethodDeclarationContext m) {
            return m.methodHeader()
                    .methodDeclarator()
                    .identifier()
                    .getText();
        }

        if (node instanceof Java20Parser.ConstructorDeclarationContext c) {
            try {
                return c.constructorDeclarator()
                        .simpleTypeName()
                        .typeIdentifier()
                        .Identifier()
                        .getText();
            } catch (Exception ignored) {}
            return "<constructor>";
        }

        if (node instanceof Java20Parser.CompactConstructorDeclarationContext cc) {
            try {
                return cc.simpleTypeName().typeIdentifier().Identifier().getText();
            } catch (Exception ignored) {}
            return "<compact‑ctor>";
        }

        return "<anonymous>";
    }

    private static String cMethodName(CParser.FunctionDefinitionContext ctx) {
        CParser.DirectDeclaratorContext dd = ctx.declarator().directDeclarator();
        while (dd != null && dd.directDeclarator() != null) {
            dd = dd.directDeclarator();
        }
        return (dd != null && dd.Identifier() != null)
                ? dd.Identifier().getText()
                : "<anonymous>";
    }

    private static int countParseTreeNodes(ParseTree t) {
        if (t == null) return 0;
        int cnt = 1;
        for (int i = 0; i < t.getChildCount(); i++) {
            cnt += countParseTreeNodes(t.getChild(i));
        }
        return cnt;
    }

    private static TreeNode lca(TreeNode a, TreeNode b) {
        Set<TreeNode> path = new HashSet<>();
        for (TreeNode n = a; n != null; n = n.getParent()) path.add(n);
        for (TreeNode n = b; n != null; n = n.getParent())
            if (path.contains(n)) return n;
        return null;
    }

    private static List<String> context(Path file,
                                        TreeNode anchor,
                                        int margin) throws IOException {
        List<String> all = Files.readAllLines(file);
        int from = Math.max(0, anchor.getStartLine() - 1 - margin);
        int to   = Math.min(all.size(), anchor.getEndLine() + margin);
        List<String> out = new ArrayList<>();
        for (int i = from; i < to; i++) {
            out.add(String.format("%5d %s", i + 1, all.get(i)));
        }
        return out;
    }

    private static String extractName(String oldFilePath, String newFilePath) {
        if (oldFilePath == null || newFilePath == null) {
            return "Both file paths must be provided.";
        }

        oldFilePath = oldFilePath.replace("\\", "/");
        newFilePath = newFilePath.replace("\\", "/");

        String suffix = commonSuffix(oldFilePath, newFilePath);

        if (suffix.startsWith(".") || !suffix.contains("_")) {
            return oldFilePath + " -> " + newFilePath;
        }

        if (suffix.startsWith("_")) {
            suffix = suffix.substring(1);
        }

        String formattedPath = suffix.replace('_', '/');

        return formattedPath;
    }

    private static String commonSuffix(String a, String b) {
        if (a == null || b == null) return "";

        int aLen = a.length();
        int bLen = b.length();
        int i = 0;

        while (i < aLen && i < bLen && a.charAt(aLen - 1 - i) == b.charAt(bLen - 1 - i)) {
            i++;
        }

        return a.substring(aLen - i);
    }



}
