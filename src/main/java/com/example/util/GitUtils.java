package com.example.util;

import com.example.CommitPairWithFiles;
import com.example.service.GitRepositoryManager;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;

@Component
public class GitUtils {

    private static GitRepositoryManager repoManager;

    @Autowired
    public GitUtils(GitRepositoryManager manager) {
        GitUtils.repoManager = manager;
    }

    public static Repository getRepository(String repoDir) throws IOException {
        return new FileRepositoryBuilder()
                .setGitDir(new File(repoDir + "/.git"))
                .readEnvironment()
                .findGitDir()
                .build();
    }


    public static Repository ensureClonedAndLoaded(
            String repoUrl,
            String localRepoPath
    ) {
        File repoDir = new File(localRepoPath);

        // 1) make sure parent folder exists
        File parent = repoDir.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        // 2) try to load it
        try {
            System.out.println("Attempting to load repo from " + localRepoPath);
            return repoManager.loadRepository(localRepoPath);
        } catch (RuntimeException loadEx) {
            System.out.println("Load failed: " + loadEx.getMessage());
            System.out.println("Will clone afresh and retry.");

            // if there's something there that's not a repo, clear it out
            if (repoDir.exists()) {
                deleteRecursively(repoDir);
            }

            // 3) clone
            try {
                Git.cloneRepository()
                        .setURI(repoUrl)
                        .setDirectory(repoDir)
                        .call();
                System.out.println("Cloned to: " + localRepoPath);
            } catch (GitAPIException gitEx) {
                throw new RuntimeException("Failed to clone " + repoUrl, gitEx);
            }

            // 4) load again (this should now succeed)
            return repoManager.loadRepository(localRepoPath);
        }
    }

    // helper to wipe out a directory tree
    private static void deleteRecursively(File f) {
        if (f.isDirectory()) {
            for (File child : f.listFiles()) {
                deleteRecursively(child);
            }
        }
        f.delete();
    }


    public static List<RevCommit> getCommits(String repoPath) {
        List<RevCommit> commits = new ArrayList<>();
        try (Git git = Git.open(new File(repoPath))) {
            for (RevCommit commit : git.log().call()) {
                commits.add(commit);
                System.out.println("Commit: " + commit.getName() + " | " + commit.getShortMessage());
            }
        } catch (Exception e) {
            System.err.println("Błąd pobierania commitów: " + e.getMessage());
        }
        return commits;
    }

    public static List<CommitPairWithFiles> processRepo(
            String repoUrl,
            String localRepoPath,
            List<String> allowedExtensions,
            boolean debug
    ) throws Exception {

        File repoDir = new File(localRepoPath);
        if (!repoDir.exists() || Objects.requireNonNull(repoDir.listFiles()).length == 0) {
            ensureClonedAndLoaded(repoUrl, localRepoPath);
        }

        Repository repository = repoManager.loadRepository(localRepoPath);
        Git git = new Git(repository);

        Iterable<RevCommit> allCommits = git.log().all().call();

        // Build unique commit pairs
        Set<String> seenIds = new HashSet<>();
        List<AbstractMap.SimpleEntry<RevCommit, RevCommit>> pairs = new ArrayList<>();
        for (RevCommit newCommit : allCommits) {
            for (RevCommit parent : newCommit.getParents()) {
                String id = parent.getName() + ":" + newCommit.getName();
                if (seenIds.add(id)) {
                    pairs.add(new AbstractMap.SimpleEntry<>(parent, newCommit));
                }
            }
        }

        int total = pairs.size();
        AtomicInteger completed = new AtomicInteger(0);
        List<CommitPairWithFiles> result = Collections.synchronizedList(new ArrayList<>());
        Map<String, List<DiffEntry>> diffCache = new ConcurrentHashMap<>();

        // Setup thread pool
        int threads = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CompletionService<Void> cs = new ExecutorCompletionService<>(executor);

        // Submit tasks
        for (AbstractMap.SimpleEntry<RevCommit, RevCommit> entry : pairs) {
            cs.submit(() -> {
                RevCommit oldC = entry.getKey();
                RevCommit newC = entry.getValue();
                String pairId = oldC.getName() + ":" + newC.getName();
                try {
                    List<DiffEntry> diffs = diffCache.computeIfAbsent(pairId, k -> {
                        try {
                            return getDiffs(repository, oldC, newC);
                        } catch (IOException e) {
                            System.err.println("Error computing diffs for " + k + ": " + e.getMessage());
                            return Collections.emptyList();
                        }
                    });

                    List<String> smallFiles = diffs.stream()
                            // 1) only modified files with an allowed extension
                            .filter(d -> d.getChangeType() == DiffEntry.ChangeType.MODIFY)
                            .filter(d -> allowedExtensions.stream().anyMatch(ext -> d.getNewPath().endsWith(ext)))
                            // 2) only diffs where total changed lines ≤ 3
                            .filter(d -> {
                                try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                                    df.setRepository(repository);
                                    df.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
                                    df.setContext(0);
                                    df.setDetectRenames(true);

                                    // split the diff into individual edit blocks
                                    EditList edits = df.toFileHeader(d).toEditList();
                                    if (edits.isEmpty()) {
                                        return false;  // no real changes at all
                                    }

                                    // for every block, count the meaningful lines
                                    boolean pass = true;
                                    for (Edit e : edits) {
                                        int blockMeanings = DiffUtils.countMeaningfulChangedLinesInBlock(
                                                repository, oldC, newC, d, e);

                                        if (debug) {
                                            System.out.println("Block " + e + " has "
                                                    + blockMeanings + " meaningful lines");
                                        }
                                        if (blockMeanings > 2) {
                                            pass = false;
                                        }
                                    }

                                    // passed: every block had exactly one meaningful changed line
                                    return pass;
                                } catch (IOException io) {
                                    if (debug) io.printStackTrace();
                                    return false;
                                }
                            })
                            // 4) map to the file path
                            .map(DiffEntry::getNewPath)
                            .collect(Collectors.toList());


                    if (!smallFiles.isEmpty()) {
                        result.add(new CommitPairWithFiles(oldC, newC, smallFiles));
                    }
                } catch (Exception e) {
                    System.err.println("Error processing " + pairId + ": " + e.getMessage());
                }

                int done = completed.incrementAndGet();
                printProgressBar(done, total);
                return null;
            });
        }

        // Shutdown and await
        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
            executor.shutdownNow();
        }
        System.out.println();
        return result;
    }

    private static List<DiffEntry> getDiffs(Repository repo, RevCommit oldC, RevCommit newC) throws IOException {
        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            df.setRepository(repo);
            df.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
            df.setContext(0);
            df.setDetectRenames(true);
            return df.scan(oldC.getTree(), newC.getTree());
        }
    }

    private static int countChangedLines(Git git, DiffEntry diff, boolean debug) throws IOException {
        if (diff == null) return 0;
        try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            formatter.setRepository(git.getRepository());
            formatter.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
            formatter.setDetectRenames(true);
            EditList edits = formatter.toFileHeader(diff).toEditList();
            int changed = 0;
            for (Edit edit : edits) {
                switch (edit.getType()) {
                    case REPLACE:
                        changed += Math.max(
                                edit.getEndA() - edit.getBeginA(),
                                edit.getEndB() - edit.getBeginB()
                        );
                        break;
                    case INSERT:
                        changed += (edit.getEndB() - edit.getBeginB());
                        break;
                    case DELETE:
                        changed += (edit.getEndA() - edit.getBeginA());
                        break;
                    default:
                        break;
                }
            }
            if (debug) {
                System.out.println("Changed lines (" + diff.getNewPath() + "): " + changed);
            }
            return changed;
        }
    }


    private static void printProgressBar(int current, int total) {
        int length = 50;
        int filled = (int) (length * current / (double) total);
        StringBuilder sb = new StringBuilder("\r[");
        for (int i = 0; i < length; i++) sb.append(i < filled ? '=' : ' ');
        sb.append("] ").append(String.format("%3d%%", (int) (current * 100.0 / total)));
        System.out.print(sb);
        System.out.flush();
    }

    public static File extractFileAtCommit(RevCommit commit, String filePath) {
        Repository repository = repoManager.getCurrentRepository();

        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            RevTree tree = commit.getTree();

            treeWalk.addTree(tree);
            treeWalk.setRecursive(filePath.contains("/"));
            treeWalk.setFilter(PathFilter.create(filePath));

            if (!treeWalk.next()) {
                System.out.println("File not found in commit: " + filePath + " (commit: " + commit.getName() + ")");
                return null;
            }

            ObjectId objectId = treeWalk.getObjectId(0);

            ObjectLoader loader;
            try {
                loader = repository.open(objectId);
            } catch (IOException e) {
                System.err.println("Failed to open Git object for file: " + filePath +
                        " in commit: " + commit.getName() +
                        " (object ID: " + objectId.name() + ")");
                e.printStackTrace();
                return null;
            }

            File tempFile = File.createTempFile(
                    "commit_" + commit.getName(), "_" + filePath.replace("/", "_"));

            try (FileOutputStream out = new FileOutputStream(tempFile)) {
                loader.copyTo(out);
            }

            return tempFile;

        } catch (IOException e) {
            System.err.println("Error extracting file at commit: " + commit.getName() +
                    " | file: " + filePath);
            e.printStackTrace();
            return null;
        }
    }


    public static List<String> extractFileAtTwoCommits(
            String repoPath,
            String relativeFilePath,
            String oldCommitSha,
            String newCommitSha,
            String outputDir) {

        List<String> extractedPaths = new ArrayList<>();

        try (Repository repository = Git.open(new File(repoPath)).getRepository();
             RevWalk revWalk = new RevWalk(repository)) {

            RevCommit oldCommit = revWalk.parseCommit(repository.resolve(oldCommitSha));
            RevCommit newCommit = revWalk.parseCommit(repository.resolve(newCommitSha));

            String oldExtracted = extractFileAtCommitToDirectory(repository, oldCommit, relativeFilePath, outputDir);
            String newExtracted = extractFileAtCommitToDirectory(repository, newCommit, relativeFilePath, outputDir);

            if (oldExtracted != null) extractedPaths.add(oldExtracted);
            if (newExtracted != null) extractedPaths.add(newExtracted);

        } catch (Exception e) {
            System.err.println("Error extracting files: " + e.getMessage());
        }

        return extractedPaths;
    }


    private static String extractFileAtCommitToDirectory(Repository repository, RevCommit commit, String filePath, String outputDir) {
        try {
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

            File outputFile = new File(outputDir, "commit_" + commit.getName() + "_" + filePath.replace("/", "_"));
            outputFile.getParentFile().mkdirs(); // Ensure the directory exists

            try (FileOutputStream out = new FileOutputStream(outputFile)) {
                loader.copyTo(out);
            }

            return outputFile.getAbsolutePath();

        } catch (IOException e) {
            System.err.println("Error extracting file: " + e.getMessage());
            return null;
        }
    }
}



