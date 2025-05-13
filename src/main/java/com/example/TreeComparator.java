package com.example;


import com.example.classifier.ChangeClassifier;
import com.example.classifier.MutationKind;
import com.example.pojo.ClassifiedOperation;
import com.example.util.TreeUtils;
import com.github.gumtreediff.matchers.CompositeMatchers;
import com.github.gumtreediff.matchers.ConfigurationOptions;
import com.github.gumtreediff.matchers.GumtreeProperties;
import gumtree.spoon.AstComparator;
import gumtree.spoon.diff.Diff;
import gumtree.spoon.diff.DiffConfiguration;
import gumtree.spoon.diff.operations.*;
import java.util.List;

import org.apache.commons.lang3.builder.DiffBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import spoon.Launcher;
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
                System.err.println("Failed comparing files: " + e.getMessage());
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

    private  FileResult compareFiles(
            File oldFile,
            File newFile,
            String fileName,
            RevCommit oldCommit,
            RevCommit newCommit,
            boolean debug
    ) throws Exception {
        Stopwatch sw = Stopwatch.createStarted();

        // 1) Build Spoon models for old and new versions
        Launcher oldLauncher = new Launcher();
        oldLauncher.addInputResource(oldFile.getPath());
        oldLauncher.buildModel();
        CtModel oldModel = oldLauncher.getModel();

        Launcher newLauncher = new Launcher();
        newLauncher.addInputResource(newFile.getPath());
        newLauncher.buildModel();
        CtModel newModel = newLauncher.getModel();

        // 2) Remove all comments so they don't show up in the diff
        oldModel.getElements(new TypeFilter<>(CtComment.class))
                .forEach(CtComment::delete);
        newModel.getElements(new TypeFilter<>(CtComment.class))
                .forEach(CtComment::delete);

        // 3) Index methods by signature
        List<CtMethod<?>> oldMethods = oldModel.getElements(new TypeFilter<>(CtMethod.class));
        List<CtMethod<?>> newMethods = newModel.getElements(new TypeFilter<>(CtMethod.class));

        Map<String, CtMethod<?>> oldMap = oldMethods.stream()
                .collect(Collectors.toMap(CtMethod::getSignature, m -> m));
        Map<String, CtMethod<?>> newMap = newMethods.stream()
                .collect(Collectors.toMap(CtMethod::getSignature, m -> m));

        // 4) Prepare comparator and accumulator
        AstComparator comp = new AstComparator();

        List<EditOperation> allOps = new ArrayList<>();
        List<Operation>     allRawOps = new ArrayList<>();

        // 5) For each method signature (including added or removed)
        Set<String> allSignatures = new HashSet<>();
        allSignatures.addAll(oldMap.keySet());
        allSignatures.addAll(newMap.keySet());

        Map<Operation, ClassifiedOperation> table = new LinkedHashMap<>();
        Map<String, Integer> fileMetricsAllMethods = new LinkedHashMap<>();
        for (MutationKind k : MutationKind.values()) {
            fileMetricsAllMethods.put(k.name(), 0);
        }

        for (String sig : allSignatures) {

            CtMethod<?> oldM = oldMap.get(sig);
            CtMethod<?> newM = newMap.get(sig);
            String methodName = (newM != null ? newM : oldM).getSimpleName();

            if (debug) {
                System.out.printf("  – diffing method %s%n", sig);
            }

            // Clone so comparator always has two inputs
            CtElement left = oldM != null ? oldM.clone() : newM.clone();
            CtElement right = newM != null ? newM.clone() : oldM.clone();

            Diff diff = comp.compare(left, right);

            // 6) Translate operations, tagging with methodName
            for (Operation op : diff.getRootOperations()) {
                String raw = op.getAction().getName();
                String base = raw.contains("-") ? raw.substring(0, raw.indexOf('-')) : raw;
                EditOperation.Type type;
                try {
                    type = EditOperation.Type.valueOf(base.toUpperCase());
                } catch (IllegalArgumentException ex) {
                    if (debug) System.err.println("    Skipping unrecognized: " + raw);
                    continue;
                }

                CtElement src = null, dst = null;
                switch (op) {
                    case InsertOperation ins -> dst = ins.getNode();
                    case DeleteOperation del -> src = del.getNode();
                    case UpdateOperation upd -> {
                        src = upd.getSrcNode();
                        dst = upd.getDstNode();
                    }
                    case MoveOperation mov -> {
                        src = mov.getSrcNode();
                        dst = mov.getDstNode();
                    }
                    default -> throw new IllegalArgumentException("Unknown edit: " + type);
                }

                Node jpSrc = (src != null)
                        ? TreeUtils.findJavaParserNode(oldFile, src).orElse(null)
                        : null;
                Node jpDst = (dst != null)
                        ? TreeUtils.findJavaParserNode(newFile, dst).orElse(null)
                        : null;

                CtElement ctx = dst != null ? dst : src;
                List<String> contextList = (ctx != null)
                        ? TreeUtils.extractCtElementContext(
                        dst != null ? newFile : oldFile, ctx, 1
                )
                        : List.of();
                allRawOps.add(op);
                allOps.add(new EditOperation(
                        type, src, dst, jpSrc, jpDst, methodName, contextList
                ));


            }

            // 1) wrap them all
            List<Operation> rawOps = diff.getRootOperations();

            for (Operation o : rawOps) {
                table.putIfAbsent(o, new ClassifiedOperation(o));
            }

            // 2) for each registered pattern, see if it fires,
            //    and *also* let it tell us which ops it “uses”
            for (var e : changeClassifier.getRegisteredPatterns().entrySet()) {
                var kind = e.getKey();
                var pat = e.getValue();

                List<Operation> used = pat.matchingOperations(rawOps);
                if (!used.isEmpty()) {
                    // tag each op as before...
                    used.forEach(o -> table.get(o).addKind(kind));

                }
            }

            // 3) now you have a list of ClassifiedOperation
            List<ClassifiedOperation> classified = new ArrayList<>(table.values());

            Set<MutationKind> methodMutations = classified.stream()
                    .flatMap(c -> c.getKinds().stream())
                    .collect(Collectors.toSet());

            methodMutations.forEach(kind -> fileMetricsAllMethods.merge(kind.name(), 1, Integer::sum));

            // 4) printing / filtering
            if (debug) {
                classified.forEach(c -> {
                    System.out.println(c.getOperation().getAction().getName()
                            + ": ||" + c.getOperation().getAction().getNode() + " ||"
                            + "  – kinds=" + c.getKinds());
                });
                System.out.printf("   → method %s had mutations %s%n",
                        methodName, methodMutations);
            }

        }
        sw.stop();


        if (debug) {
            System.out.printf("AST diff for %s took %d ms and yielded %d ops:%n",
                    fileName, sw.elapsed(TimeUnit.MILLISECONDS), allOps.size());
        }


        List<EditOperation> unmutated = new ArrayList<>();
        for (int i = 0; i < allRawOps.size(); i++) {
            Operation raw = allRawOps.get(i);
            ClassifiedOperation c = table.get(raw);
            if (c.getKinds().isEmpty()) {
                unmutated.add(allOps.get(i));
            }
        }

        return createFileResult(
                fileName,
                oldFile.getAbsolutePath(),
                newFile.getAbsolutePath(),
                unmutated,
                fileMetricsAllMethods,
                oldCommit != null ? oldCommit.getName() : null,
                newCommit != null ? newCommit.getName() : null
        );
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
