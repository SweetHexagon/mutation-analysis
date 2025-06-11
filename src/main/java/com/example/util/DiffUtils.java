package com.example.util;

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.eclipse.jgit.diff.RawText;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class DiffUtils {

    /**
     * Counts only the "meaningful" changed lines between two commits: i.e.
     * non-blank, non-comment inserts/replaces/deletes.
     */
    public static int countMeaningfulChangedLines(Repository repo,
                                                  RevCommit oldCommit,
                                                  RevCommit newCommit,
                                                  DiffEntry diff) throws IOException {
        // Load raw file contents as RawText for old and new
        RawText oldText = loadRawText(repo, oldCommit, diff.getOldPath());
        RawText newText = loadRawText(repo, newCommit, diff.getNewPath());

        // Use a DiffFormatter to get the EditList
        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        df.setRepository(repo);
        EditList edits = df.toFileHeader(diff).toEditList();

        int meaningful = 0;
        for (Edit e : edits) {
            switch (e.getType()) {
                case INSERT:
                case REPLACE:
                    // if this is a 1→1 line replace, check for inline-comment–only
                    int oldCount = e.getEndA() - e.getBeginA();
                    int newCount = e.getEndB() - e.getBeginB();
                    if (oldCount == 1 && newCount == 1) {
                        String oldLine = oldText.getString(e.getBeginA());
                        String newLine = newText.getString(e.getBeginB());

                        // skip it if it's just "oldCode" → "oldCode  // comment"
                        if (isInlineCommentOnlyChange(oldLine, newLine)) {
                            break;
                        }

                        // otherwise fall through and count if it's not a pure comment/blank
                        if (!isCommentOrBlank(newLine)) {
                            meaningful++;
                        }
                    } else {
                        // fallback: multiple lines replaced—use existing logic
                        for (int i = e.getBeginB(); i < e.getEndB(); i++) {
                            String line = newText.getString(i);
                            if (!isCommentOrBlank(line)) {
                                meaningful++;
                            }
                        }
                    }
                    break;

                case DELETE:
                    // for each deleted/old line
                    for (int i = e.getBeginA(); i < e.getEndA(); i++) {
                        String line = oldText.getString(i);
                        if (!isCommentOrBlank(line)) {
                            meaningful++;
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        df.close();
        return meaningful;
    }

    /**
     * Counts only the "meaningful" changed lines within a single diff‐hunk (Edit).
     * This is what GitUtils.processRepo() will call on each Edit block.
     */
    public static int countMeaningfulChangedLinesInBlock(
            Repository repo,
            RevCommit oldCommit,
            RevCommit newCommit,
            DiffEntry diff,
            Edit edit) throws IOException {

        RawText oldText = loadRawText(repo, oldCommit, diff.getOldPath());
        RawText newText = loadRawText(repo, newCommit, diff.getNewPath());

        if (diff.getOldPath().contains("REMOVE_INCREMENTS")) {
            System.out.println("=== File: " + diff.getOldPath() + " ===");
        }
        int meaningful = 0;

        switch (edit.getType()) {
            case INSERT:
            case REPLACE:
                int lineCount = Math.max(edit.getEndA() - edit.getBeginA(), edit.getEndB() - edit.getBeginB());

                for (int i = 0; i < lineCount; i++) {
                    String oldLine = (edit.getBeginA() + i < oldText.size()) ? oldText.getString(edit.getBeginA() + i) : "";
                    String newLine = (edit.getBeginB() + i < newText.size()) ? newText.getString(edit.getBeginB() + i) : "";

                    if (isCommentOrBlank(oldLine) && isCommentOrBlank(newLine)) {
                        continue; // both are non-meaningful
                    }

                    if (isInlineCommentOnlyChange(oldLine, newLine) || isWhitespaceOnlyChange(oldLine, newLine)) {
                        continue; // superficial
                    }

                    // If we reach here, something meaningful changed
                    meaningful++;
                }
                break;


            case DELETE:
                for (int i = edit.getBeginA(); i < edit.getEndA(); i++) {
                    String line = oldText.getString(i);
                    if (!isCommentOrBlank(line)) {
                        meaningful++;
                    }
                }
                break;

            default:
                break;
        }

        return meaningful;
    }



    /** Loads the file at a given commit into a RawText. */
    private static RawText loadRawText(Repository repo,
                                       RevCommit commit,
                                       String path) throws IOException {
        // walk the commit's tree to find the blob for 'path'
        RevTree tree = commit.getTree();
        CanonicalTreeParser treeParser = new CanonicalTreeParser(null, repo.newObjectReader(), tree);
        try (TreeWalk tw = new TreeWalk(repo)) {
            tw.addTree(treeParser);
            tw.setRecursive(true);
            tw.setFilter(PathFilter.create(path));
            if (!tw.next()) return new RawText(new byte[0]);  // file not found
            ObjectLoader loader = repo.open(tw.getObjectId(0));
            byte[] data = loader.getBytes();
            return new RawText(data);
        }
    }

    /** Returns true if the line is blank or a Java comment. */
    private static boolean isCommentOrBlank(String line) {
        String t = line.trim();
        if (t.isEmpty()) return true;
        if (t.startsWith("//")) return true;
        if (t.startsWith("/*") || t.endsWith("*/")) return true;
        if (t.startsWith("*")) return true;
        if (t.startsWith("import ")) return true;
        if (t.startsWith("@")) return true;
        if (t.startsWith("/**")) return true;
        if (t.matches(".*Generated by.*") || t.matches(".*Licensed under.*")) return true;
        return false;
    }


    /**
     * Returns true if newLine is exactly oldLine plus only an inline comment suffix.
     */
    private static boolean isInlineCommentOnlyChange(String oldLine, String newLine) {
        String oldTrim = oldLine.trim();
        String newTrim = newLine.trim();
        // must start with the old code
        if (!newTrim.startsWith(oldTrim)) return false;
        // see what was appended
        String suffix = newTrim.substring(oldTrim.length()).trim();
        // if suffix is just "//…" or "/*…*/", it's a pure comment add
        return suffix.startsWith("//") || suffix.startsWith("/*");
    }
    private static boolean isWhitespaceOnlyChange(String oldLine, String newLine) {
        return oldLine != null && newLine != null &&
                oldLine.strip().equals(newLine.strip()) &&
                !oldLine.equals(newLine); // ensures change was only whitespace
    }

}

