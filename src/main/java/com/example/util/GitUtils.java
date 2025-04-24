package com.example.util;

import com.example.CommitPairWithFiles;
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
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
            String localPath,
            List<String> allowedExtensions,
            boolean debug
    ) throws Exception {
        // Clone or open repository
        File repoDir = new File(localPath);
        Git git;
        if (!repoDir.exists() || Objects.requireNonNull(repoDir.listFiles()).length == 0) {
            git = Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(repoDir)
                    .call();
        } else {
            git = Git.open(repoDir);
        }
        Repository repository = git.getRepository();
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
                                try {
                                    // raw changed lines
                                    int rawChangeCount = countChangedLines(git, d, debug);
                                    if (debug) {
                                        System.out.println("Raw changed lines in " + d.getNewPath() + ": " + rawChangeCount);
                                    }
                                    // must be between 1 and 3
                                    if (rawChangeCount < 1 || rawChangeCount > 3) {
                                        return false;
                                    }

                                    // meaningful changed lines
                                    int meaningfulChangeCount = DiffUtils.countMeaningfulChangedLines(
                                            repository, oldC, newC, d);
                                    if (debug) {
                                        System.out.println("Meaningful changed lines in "
                                                + d.getNewPath() + ": " + meaningfulChangeCount);
                                    }

                                    // only keep if exactly one of the changes was non-meaningful
                                    return meaningfulChangeCount == 1;
                                } catch (IOException e) {
                                    if (debug) {
                                        System.err.println("Error analyzing diff for "
                                                + d.getNewPath() + ": " + e.getMessage());
                                    }
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
            df.setDiffComparator(RawTextComparator.DEFAULT);
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

    public static List<String> extractFileAtTwoCommits(String repoPath, String relativeFilePath, String oldCommitSha, String newCommitSha, String outputDir) {
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



