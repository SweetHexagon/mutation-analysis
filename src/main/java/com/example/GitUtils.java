package com.example;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

public class GitUtils {
    public static void cloneRepository(String repoUrl, String localPath) {
        try {
            Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(new File(localPath))
                    .call();
            System.out.println("Repozytorium sklonowane do: " + localPath);
        } catch (GitAPIException e) {
            System.err.println("Błąd klonowania repozytorium: " + e.getMessage());
        }
    }

    public static List<RevCommit> getCommits(String repoPath) {
        List<RevCommit> commits = new ArrayList<>();
        try {
            Git git = Git.open(new File(repoPath));
            Iterable<RevCommit> log = git.log().call();
            for (RevCommit commit : log) {
                commits.add(commit);
                System.out.println("Commit: " + commit.getName() + " | " + commit.getShortMessage());
            }
        } catch (Exception e) {
            System.err.println("Błąd pobierania commitów: " + e.getMessage());
        }
        return commits;
    }


    public static String extractFileAtCommit(String repoPath, RevCommit commit, String filePath) {
        try (Repository repository = Git.open(new File(repoPath)).getRepository();
             RevWalk revWalk = new RevWalk(repository)) {

            RevTree tree = commit.getTree();
            TreeWalk treeWalk = new TreeWalk(repository);
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(filePath));

            if (!treeWalk.next()) {
                System.out.println("File not found in commit: " + filePath);
                return null;
            }

            ObjectId objectId = treeWalk.getObjectId(0);
            ObjectLoader loader = repository.open(objectId);

            File tempFile = File.createTempFile("commit_" + commit.getName(), "_" + filePath.replace("/", "_"));
            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                loader.copyTo(out);
            }

            return tempFile.getAbsolutePath();

        } catch (IOException e) {
            System.err.println("Error extracting file at commit: " + e.getMessage());
            return null;
        }
    }

    public static CommitPairWithFiles processRepoMock(String repoUrl, String localPath) {
        GitUtils.cloneRepository(repoUrl, localPath);
        List<RevCommit> commits = GitUtils.getCommits(localPath);

        if (commits.size() < 2) {
            System.err.println("Not enough commits to compare.");
            return null;
        }

        RevCommit newer = commits.get(0);
        RevCommit older = commits.get(1);

        List<String> filePathsToCompare = new ArrayList<>();

        try (Repository repository = Git.open(new File(localPath)).getRepository();
             Git git = new Git(repository)) {

            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            try (var reader = repository.newObjectReader()) {
                oldTreeIter.reset(reader, older.getTree());
            }

            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            try (var reader = repository.newObjectReader()) {
                newTreeIter.reset(reader, newer.getTree());
            }

            try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                formatter.setRepository(repository);
                List<DiffEntry> diffs = formatter.scan(oldTreeIter, newTreeIter);
                for (DiffEntry entry : diffs) {
                    filePathsToCompare.add(entry.getNewPath());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return new CommitPairWithFiles(older, newer, filePathsToCompare);
    }

    public static List<CommitPairWithFiles> processRepo(String repoUrl, String localPath) {
        try {
            // Clone or open the repository
            File repoDir = new File(localPath);
            Git git;
            if (!repoDir.exists() || repoDir.listFiles() == null || Objects.requireNonNull(repoDir.listFiles()).length == 0) {
                git = Git.cloneRepository()
                        .setURI(repoUrl)
                        .setDirectory(repoDir)
                        .call();
            } else {
                git = Git.open(repoDir);
            }

            Repository repository = git.getRepository();
            List<CommitPairWithFiles> allPairs = new ArrayList<>();

            Set<String> seenPairs = new HashSet<>();

            List<Ref> branches = git.branchList().call();

            for (Ref branch : branches) {
                String branchName = branch.getName();
                System.out.println("Analyzing branch: " + branchName);

                try (RevWalk revWalk = new RevWalk(repository)) {
                    ObjectId branchHead = repository.resolve(branchName);
                    RevCommit headCommit = revWalk.parseCommit(branchHead);

                    revWalk.markStart(headCommit);

                    List<RevCommit> commitList = new ArrayList<>();
                    for (RevCommit commit : revWalk) {
                        commitList.add(commit);
                    }

                    for (int i = 1; i < commitList.size(); i++) {
                        RevCommit oldCommit = commitList.get(i - 1);
                        RevCommit newCommit = commitList.get(i);

                        //check if those pairs were already checked
                        String pairId = oldCommit.getName() + ":" + newCommit.getName();
                        if (seenPairs.contains(pairId)) {
                            continue;
                        }
                        seenPairs.add(pairId);

                        List<DiffEntry> diffs = getDiffs(repository, oldCommit, newCommit);
                        List<String> smallChangeFiles = new ArrayList<>();

                        for (DiffEntry diff : diffs) {
                            if (diff.getChangeType() != DiffEntry.ChangeType.MODIFY) continue;

                            int changedLines = countChangedLines(git, diff);
                            if (changedLines <= 3) {
                                smallChangeFiles.add(diff.getNewPath());
                            }
                        }

                        if (!smallChangeFiles.isEmpty()) {
                            allPairs.add(new CommitPairWithFiles(oldCommit, newCommit, smallChangeFiles));
                        }
                    }
                }
            }

            return allPairs;

        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }


    private static List<DiffEntry> getDiffs(Repository repo, RevCommit oldCommit, RevCommit newCommit) throws IOException {
        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            df.setRepository(repo);
            df.setDiffComparator(RawTextComparator.DEFAULT);
            df.setDetectRenames(true);
            return df.scan(oldCommit.getTree(), newCommit.getTree());
        }
    }

    private static int countChangedLines(Git git, DiffEntry diff) {
        try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            boolean includeInsertEdits = false; // change this count changes like "if" "return" "assert" etc

            formatter.setRepository(git.getRepository());
            formatter.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
            formatter.setDetectRenames(true);

            EditList edits = formatter.toFileHeader(diff).toEditList();

            int semanticCount = 0;
            int structuralCount = 0;

            for (var edit : edits) {
                switch (edit.getType()) {
                    case REPLACE:
                        semanticCount += 1;
                        structuralCount += (edit.getEndA() - edit.getBeginA()) + (edit.getEndB() - edit.getBeginB());
                        break;
                    case INSERT:
                        if (includeInsertEdits) {
                            semanticCount += 1;
                            structuralCount += (edit.getEndB() - edit.getBeginB());
                        }
                        break;
                    case DELETE:
                        semanticCount += 1;
                        structuralCount += (edit.getEndA() - edit.getBeginA());
                        break;
                    case EMPTY:
                        break;
                }
            }

            System.out.printf("Semantic: %d, Structural: %d, File: %s%n", semanticCount, structuralCount, diff.getNewPath());

            return semanticCount;
        } catch (IOException e) {
            System.err.println("Error in countChangedLines: " + e.getMessage());
            return Integer.MAX_VALUE;
        }
    }

}