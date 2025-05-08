package com.example;


import com.example.util.TreeUtils;
import com.github.javaparser.Range;
import gumtree.spoon.AstComparator;
import gumtree.spoon.diff.Diff;
import gumtree.spoon.diff.operations.*;

import java.util.List;
import spoon.reflect.declaration.CtMethod;


import com.example.pojo.FileResult;
import com.example.util.GitUtils;
import com.google.common.base.Stopwatch;

import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.declaration.CtElement;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.*;
import com.github.javaparser.ast.Node;

public class TreeComparator {


    private static final boolean SHOW_DEEP_CONTEXT = false;

    private static final int CONTEXT_MARGIN = SHOW_DEEP_CONTEXT ? 0 : 1;


    private static final Logger log = LoggerFactory.getLogger(TreeComparator.class);

    public static FileResult compareFileInTwoCommits(String localPath, RevCommit oldCommit, RevCommit newCommit, String fileName) {
        return compareFileInTwoCommits(localPath, oldCommit, newCommit, fileName, false);
    }

    public static FileResult compareFileInTwoCommits(
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

    public static FileResult compareTwoFilePaths(String oldFilePath, String newFilePath) {
        return compareTwoFilePaths(oldFilePath, newFilePath, false);
    }

    public static FileResult compareTwoFilePaths(
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

    private static FileResult compareFiles(
            File oldFile,
            File newFile,
            String fileName,
            RevCommit oldCommit,
            RevCommit newCommit,
            boolean debug) throws Exception {

        Stopwatch sw = Stopwatch.createStarted();
        AstComparator comp = new AstComparator();
        Diff diff = comp.compare(oldFile, newFile);
        sw.stop();

        if (debug) {
            System.out.printf("AST diff for %s took %d ms%n", fileName, sw.elapsed(TimeUnit.MILLISECONDS));
        }

        List<Operation> ops = diff.getRootOperations();
        List<EditOperation> allOps = new ArrayList<>();

        for (Operation op : ops) {
            // 1) normalize the action name
            String raw = op.getAction().getName();
            String base = raw.contains("-") ? raw.substring(0, raw.indexOf('-')) : raw;
            EditOperation.Type type;
            try {
                type = EditOperation.Type.valueOf(base.toUpperCase());
            } catch (IllegalArgumentException e) {
                if (debug) System.err.println("Skipping unrecognized action: " + raw);
                continue;
            }

            CtElement src = null, dst = null;

            switch (op) {
                case InsertOperation ins ->
                        dst = ins.getNode();
                case DeleteOperation del ->
                        src = del.getNode();
                case UpdateOperation upd -> {
                    src = upd.getSrcNode();
                    dst = upd.getDstNode();
                }
                case MoveOperation mov -> {
                    src = mov.getSrcNode();
                    dst = mov.getDstNode();
                }
                default -> throw new IllegalArgumentException("Unknown edit type: " + type);
            }

            Node javaParserSrcNode = (src  != null ? TreeUtils.findJavaParserNode(oldFile,  src).orElse(null) : null);
            Node javaParserDstNode = (dst  != null ? TreeUtils.findJavaParserNode(newFile, dst).orElse(null) : null);

            String methodName = null;
            CtElement contextCt = (dst != null ? dst : src);

            if (contextCt != null) {
                CtElement parent = contextCt;
                while (parent != null && !(parent instanceof CtMethod<?>)) {
                    parent = parent.getParent();
                }
                if (parent != null) {
                    methodName = ((CtMethod<?>) parent).getSimpleName();
                }
            }

            CtElement contextElem = (dst != null ? dst : src);

            List<String> contextList = List.of();
            if (contextElem != null) {
                File fileForContext = (dst != null ? newFile : oldFile);
                contextList = TreeUtils.extractCtElementContext(fileForContext, contextElem, 1);
            }

            EditOperation eop = new EditOperation(
                    type,
                    src,
                    dst,
                    javaParserSrcNode,
                    javaParserDstNode,
                    methodName,
                    contextList
            );
            allOps.add(eop);
        }

        Map<Metrics, Integer> metrics = Map.of(Metrics.EDITS, ops.size());

        return createFileResult(
                fileName,
                oldFile.getAbsolutePath(),
                newFile.getAbsolutePath(),
                allOps,
                metrics,
                oldCommit != null ? oldCommit.getName() : null,
                newCommit != null ? newCommit.getName() : null
        );
    }




    private static FileResult createFileResult(String fileName, String original, String changed,
                                               List<EditOperation> ops, Map<Metrics, Integer> metrics,
                                               String oldCommit, String newCommit) {
        return FileResult.builder()
                .name(fileName)
                .editOperations(ops)
                .metrics(metrics)
                .oldCommit(oldCommit)
                .newCommit(newCommit)
                .build();
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

        return suffix.replace('_', '/');
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
