package com.example;


import com.example.classifier.ChangeClassifier;
import com.example.classifier.MutationKind;
import com.example.pojo.ClassifiedOperation;
import com.example.util.TreeUtils;
import gumtree.spoon.AstComparator;
import gumtree.spoon.diff.Diff;
import gumtree.spoon.diff.operations.*;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtComment;
import spoon.reflect.declaration.CtMethod;


import com.example.pojo.FileResult;
import com.example.util.GitUtils;
import com.google.common.base.Stopwatch;

import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.declaration.CtElement;

import java.io.File;

import java.util.concurrent.TimeUnit;
import java.util.*;
import java.util.stream.Collectors;

import com.github.javaparser.ast.Node;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.Level;

import static com.example.EditOperation.Type.*;

@Component
public class TreeComparator {


    private final ChangeClassifier changeClassifier;

    @Autowired
    public TreeComparator(ChangeClassifier changeClassifier) {
        this.changeClassifier = changeClassifier;
    }
    
    private  final Logger log = LoggerFactory.getLogger(TreeComparator.class);

    public  FileResult compareFileInTwoCommits(String localPath, RevCommit oldCommit, RevCommit newCommit, String fileName) {
        return compareFileInTwoCommits(localPath, oldCommit, newCommit, fileName, false);
    }

    public  FileResult compareFileInTwoCommits(
            String localPath,
            RevCommit oldCommit,
            RevCommit newCommit,
            String fileName,
            boolean debug) {

        File oldFile = GitUtils.extractFileAtCommit(oldCommit, fileName);
        File newFile = GitUtils.extractFileAtCommit(newCommit, fileName);

        if (debug) {
            System.out.println("Temp file path: "
                    + (oldFile != null ? oldFile.getAbsolutePath() : "null")
                    + " → "
                    + (newFile != null ? newFile.getAbsolutePath() : "null"));
        }

        if (oldFile == null || newFile == null) {
            System.out.println("Couldn't extract both versions for: " + fileName);
            return null;
        }

        try {
            return compareFiles(oldFile, newFile, fileName, oldCommit, newCommit, debug);
        } catch (Exception e) {
            if (debug) {
                System.err.println("Failed comparing files: " + e.getMessage() + Arrays.toString(e.getStackTrace()));
            }
            return null;
        } finally {
            oldFile.delete();
            newFile.delete();
        }
    }

    public  FileResult compareTwoFilePaths(String oldFilePath, String newFilePath) {
        return compareTwoFilePaths(oldFilePath, newFilePath, false);
    }

    public  FileResult compareTwoFilePaths(
            String oldFilePath,
            String newFilePath,
            boolean debug) {
        if (oldFilePath == null || newFilePath == null) {
            System.out.println("Both file paths must be provided.");
            return null;
        }

        File oldFile = new File(oldFilePath);
        File newFile = new File(newFilePath);
        String fileName = extractName(oldFilePath, newFilePath);

        try {
            return compareFiles(oldFile, newFile, fileName, null, null, debug);
        } catch (Exception e) {
            if (debug) {
                System.err.println("Failed comparing files: " + e.getMessage());
            }
            return null;
        }
    }

    public FileResult compareFiles(
            File oldFile,
            File newFile,
            String fileName,
            RevCommit oldCommit,
            RevCommit newCommit,
            boolean debug
    ) throws Exception {
        Stopwatch sw = Stopwatch.createStarted();

        CtModel oldModel = buildModel(oldFile);
        CtModel newModel = buildModel(newFile);

        stripComments(oldModel);
        stripComments(newModel);

        Map<String, CtMethod<?>> oldMap = indexMethods(oldModel);
        Map<String, CtMethod<?>> newMap = indexMethods(newModel);

        AstComparator comparator = new AstComparator();
        List<EditOperation> allOps = new ArrayList<>();
        List<Operation> allRawOps = new ArrayList<>();
        Map<Operation, ClassifiedOperation> classification = new LinkedHashMap<>();
        Map<String, Integer> metrics = initMetrics();

        for (String sig : union(oldMap.keySet(), newMap.keySet())) {
            CtMethod<?> oldMethod = oldMap.get(sig);
            CtMethod<?> newMethod = newMap.get(sig);
            String methodName = (newMethod != null ? newMethod : oldMethod).getSimpleName();

            if (debug) System.out.printf("– diffing %s%n", sig);

            Diff diff = diffMethods(comparator, oldMethod, newMethod);
            processDiff(diff, oldFile, newFile, methodName, allOps, allRawOps, classification);
            classifyOperations(diff.getRootOperations(), classification);
            updateMetrics(classification.values(), metrics);

            if (debug) printDebugInfo(classification.values(), methodName);
        }
        sw.stop();

        if (debug) System.out.printf(
                "AST diff for %s took %d ms and yielded %d ops:%n",
                fileName,
                sw.elapsed(TimeUnit.MILLISECONDS),
                allOps.size()
        );

        List<EditOperation> unmutated = extractUnmutated(allRawOps, classification, allOps);

        List<EditOperation> filteredMoveOperations = mergeChildMovesIntoParent(unmutated);

        return createFileResult(
                fileName,
                oldFile.getAbsolutePath(),
                newFile.getAbsolutePath(),
                filteredMoveOperations,
                metrics,
                getCommitName(oldCommit),
                getCommitName(newCommit)
        );
    }

    private CtModel buildModel(File file) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(file.getPath());
        launcher.buildModel();
        return launcher.getModel();
    }

    private void stripComments(CtModel model) {
        model.getElements(new TypeFilter<>(CtComment.class))
                .forEach(CtComment::delete);
    }

    private Map indexMethods(CtModel model) {
        return model.getElements(new TypeFilter<>(CtMethod.class)).stream()
                .collect(Collectors.toMap(CtMethod::getSignature, m -> m));
    }

    private Map<String, Integer> initMetrics() {
        Map<String, Integer> metrics = new LinkedHashMap<>();
        for (MutationKind kind : MutationKind.values()) {
            metrics.put(kind.name(), 0);
        }
        return metrics;
    }

    private Set<String> union(Set<String> a, Set<String> b) {
        Set<String> all = new HashSet<>(a);
        all.addAll(b);
        return all;
    }

    private Diff diffMethods(AstComparator comparator,
                             CtMethod<?> oldMethod,
                             CtMethod<?> newMethod) {
        CtElement left = oldMethod != null ? oldMethod.clone() : newMethod.clone();
        CtElement right = newMethod != null ? newMethod.clone() : oldMethod.clone();
        return comparator.compare(left, right);
    }

    private void processDiff(
            Diff diff,
            File oldFile,
            File newFile,
            String methodName,
            List<EditOperation> allOps,
            List<Operation> allRawOps,
            Map<Operation, ClassifiedOperation> classification
    ) {
        for (Operation op : diff.getRootOperations()) {
            EditOperation eo = toEditOperation(op, oldFile, newFile, methodName);
            if (eo != null) {
                allRawOps.add(op);
                allOps.add(eo);
                classification.putIfAbsent(op, new ClassifiedOperation(op));
            }
        }
    }

    private EditOperation toEditOperation(
            Operation op,
            File oldFile,
            File newFile,
            String methodName
    ) {
        // 1) Determine the edit type
        String action = op.getAction().getName();
        String base = action.contains("-")
                ? action.substring(0, action.indexOf('-'))
                : action;
        EditOperation.Type type;
        try {
            type = EditOperation.Type.valueOf(base.toUpperCase());
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid operation type: " + base);
            return null;
        }

        // 2) Extract src and dst AST nodes
        CtElement src = null, dst = null;
        if (op instanceof InsertOperation insOp) {
            dst = insOp.getNode();
        } else if (op instanceof DeleteOperation delOp) {
            src = delOp.getNode();
        } else if (op instanceof UpdateOperation updOp) {
            src = updOp.getSrcNode();
            dst = updOp.getDstNode();
        } else if (op instanceof MoveOperation movOp) {
            src = movOp.getSrcNode();
            dst = movOp.getDstNode();
        }

        // 3) Find corresponding JavaParser nodes (if any)
        Node jpSrc = null, jpDst = null;
        try {
            jpSrc = src != null ? TreeUtils.findJavaParserNode(oldFile, src).orElse(null) : null;
            jpDst = dst != null ? TreeUtils.findJavaParserNode(newFile, dst).orElse(null) : null;
        } catch (Exception e) {

            //System.err.println("Error extracting JavaParser node" + e);
        }

        // 4) Build a uniform "before / after" context for any edit
        List<String> context = new ArrayList<>();
        try {
            if (src != null) {
                context.add("--- before ---");
                context.addAll(TreeUtils.extractCtElementContext(oldFile, src, 1));
            }
            if (dst != null) {
                context.add("--- after ---");
                context.addAll(TreeUtils.extractCtElementContext(newFile, dst, 1));
            }
        } catch (Exception e) {
            throw new RuntimeException("Error extracting context", e);
        }

        // 5) Construct and return the EditOperation
        return new EditOperation(
                type,
                src,
                dst,
                jpSrc,
                jpDst,
                methodName,
                context
        );
    }



    private void classifyOperations(
            List<Operation> ops,
            Map<Operation, ClassifiedOperation> classification
    ) {
        for (var entry : changeClassifier.getRegisteredPatterns().entrySet()) {
            MutationKind kind = entry.getKey();
            ChangeClassifier.MutationPattern pattern = entry.getValue();
            List<Operation> matches = pattern.matchingOperations(ops);
            for (Operation o : matches) {
                classification.get(o).addKind(kind);
            }
        }
    }

    private void updateMetrics(Collection<ClassifiedOperation> classified,
                               Map<String, Integer> metrics) {
        Set<MutationKind> kinds = classified.stream()
                .flatMap(c -> c.getKinds().stream())
                .collect(Collectors.toSet());
        for (MutationKind k : kinds) {
            metrics.merge(k.name(), 1, Integer::sum);
        }
    }

    private void printDebugInfo(Collection<ClassifiedOperation> classified,
                                String methodName) {
        classified.forEach(c -> System.out.println(
                c.getOperation().getAction().getName()
                        + ": " + c.getKinds()
        ));
        System.out.printf("→ method %s had mutations %s%n",
                methodName,
                classified.stream()
                        .flatMap(c -> c.getKinds().stream())
                        .collect(Collectors.toSet()));
    }

    private List<EditOperation> extractUnmutated(
            List<Operation> allRawOps,
            Map<Operation, ClassifiedOperation> classification,
            List<EditOperation> allOps
    ) {
        List<EditOperation> unmutated = new ArrayList<>();
        for (int i = 0; i < allRawOps.size(); i++) {
            Operation raw = allRawOps.get(i);

            if (classification.get(raw).getKinds().isEmpty() ) {
                unmutated.add(allOps.get(i));
            }
        }
        return unmutated;
    }

    private List<EditOperation> mergeChildMovesIntoParent(List<EditOperation> edits) {
        // We'll build a mutable copy
        List<EditOperation> result = new ArrayList<>(edits);

        // For each MOVE in the original list
        for (EditOperation moveEo : edits) {
            if (moveEo.type() != EditOperation.Type.MOVE) {
                continue;
            }
            CtElement moved = moveEo.dstNode();

            // Find the first DELETE/INSERT whose AST subtree contains the moved node
            Optional<EditOperation> parentOpt = result.stream()
                    .filter(e -> e.type() == EditOperation.Type.DELETE
                            || e.type() == EditOperation.Type.INSERT)
                    .filter(e -> {
                        CtElement root = nodeOf(e);
                        if (root == null) return false;
                        // Does root’s subtree include moved?
                        return root.getElements(new TypeFilter<>(CtElement.class))
                                .contains(moved);
                    })
                    .findFirst();

            if (parentOpt.isPresent()) {
                EditOperation parent = parentOpt.get();
                EditOperation updated;

                // Reconstruct the parent edit with the new src/dst
                if (parent.type() == EditOperation.Type.DELETE) {
                    // For DELETE, update src to the moved node
                    updated = new EditOperation(
                            UPDATE,
                            parent.srcNode(),
                            moved,               // new dst
                            parent.srcJavaNode(),
                            parent.dstJavaNode(),
                            parent.method(),
                            parent.context()
                    );
                } else {
                    // For INSERT, update dst to the moved node
                    updated = new EditOperation(
                            UPDATE,
                            moved,        // new src
                            parent.dstNode(),
                            parent.srcJavaNode(),
                            parent.dstJavaNode(),
                            parent.method(),
                            parent.context()
                    );
                }

                // Replace the parent in our result list:
                int idx = result.indexOf(parent);
                result.set(idx, updated);

                // Finally drop the MOVE itself:
                result.remove(moveEo);
            }
        }

        return result;
    }

    // helper to pull the relevant CtElement out of an EditOperation
    private CtElement nodeOf(EditOperation eo) {
        return switch (eo.type()) {
            case DELETE  -> eo.srcNode();
            case INSERT,
                 MOVE,
                 UPDATE  -> eo.dstNode();
            default      -> null;
        };
    }


    private String getCommitName(RevCommit commit) {
        return commit != null ? commit.getName() : null;
    }



    private  FileResult createFileResult(String fileName, String original, String changed,
                                               List<EditOperation> ops, Map<String, Integer> metrics,
                                               String oldCommit, String newCommit) {
        return FileResult.builder()
                .name(fileName)
                .editOperations(ops)
                .metrics(metrics)
                .oldCommit(oldCommit)
                .newCommit(newCommit)
                .build();
    }


    private  String extractName(String oldFilePath, String newFilePath) {
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

        return suffix.replace('_', '/');
    }

    private  String commonSuffix(String a, String b) {
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
