package com.example;

import java.util.*;


import com.example.classifier.MutationKind;
import com.example.dto.CommitPairDTO;
import com.example.dto.FileResultDto;
import com.example.mapper.CommitPairMapper;
import com.example.mapper.ResultMapper;
import com.example.pojo.FileResult;
import com.example.service.GitRepositoryManager;
import com.example.util.GitUtils;
import com.example.util.JsonUtils;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.jmx.support.MetricType;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.util.concurrent.*;

@SpringBootApplication
public class Main implements CommandLineRunner {
    private final GitRepositoryManager repoManager;
    private final TreeComparator      treeComparator;
    private final GitUtils gitUtils;
    EnumMap<MutationKind,Integer> repoPatternCounts =
            new EnumMap<>(MutationKind.class);


    public Main(GitRepositoryManager repoManager,
                TreeComparator treeComparator, GitUtils gitUtils) {
        this.repoManager     = repoManager;
        this.treeComparator  = treeComparator;
        this.gitUtils = gitUtils;
    }


     String localPath = "repositories";

    private  final int THREAD_COUNT = Runtime.getRuntime().availableProcessors()/2;

     final int BATCH_SIZE = 500;

    final String filteredDir  = "src/main/resources/programOutputFiltered";

     List<String> repoUrls = List.of(
            /*"https://github.com/SweetHexagon/pitest-mutators"
            "https://github.com/Snailclimb/JavaGuide"    ,        // 5 800 commits
            "https://github.com/krahets/hello-algo",               // small
            "https://github.com/iluwatar/java-design-patterns"   , // 4 327 commits
            "https://github.com/macrozheng/mall"   ,               // small
             "https://github.com/doocs/advanced-java",           // small
            "https://github.com/spring-projects/spring-boot",     // 54 313 commits
            "https://github.com/MisterBooo/LeetCodeAnimation",     // small
            "https://github.com/elastic/elasticsearch",            // 86 296 commits
            "https://github.com/kdn251/interviews",                // small
            "https://github.com/TheAlgorithms/Java",               // 2 729 commits
            */ "https://github.com/spring-projects/spring-framework"/* // 32 698 commits
            "https://github.com/NationalSecurityAgency/ghidra",    // 14 553 commits
             "https://github.com/Stirling-Tools/Stirling-PDF",      // small
            "https://github.com/google/guava",                     // 6 901 commits
            "https://github.com/ReactiveX/RxJava",                 // 6 218 commits
            "https://github.com/skylot/jadx",                      // small
            "https://github.com/dbeaver/dbeaver",                  // 27 028 commits
            "https://github.com/jeecgboot/JeecgBoot",              // small
            "https://github.com/apache/dubbo",                     // 8 414 commits
            "https://github.com/termux/termux-app"   */              // small
    );


     List<String> extensions = List.of(
            ".java");

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Override
    public void run(String... args) throws Exception {

        //manualTest();

        presentation(repoUrls);

        //JsonUtils.aggregateUniqueOperations(filteredDir, "src/main/resources/uniqueEditOperations/aggregated_unique_operations.json");
    }

    public void presentation(List<String> repoUrls) throws InterruptedException {
        for (String repoUrl : repoUrls) {

            for (MutationKind k : MutationKind.values()) {
                repoPatternCounts.put(k, 0);
            }

            String repoName = repoUrl.substring(repoUrl.lastIndexOf("/") + 1);
            String repoDir  = localPath + File.separator + repoName;

            System.out.println("Processing repo: " + repoName);

            List<CommitPairWithFiles> commitPairs;

            try {
                var repository = GitUtils.ensureClonedAndLoaded(repoUrl, repoDir);

                String cacheFilePath = "src/main/resources/cache/" + repoName + "-commitPairs.json";
                File cacheFile = new File(cacheFilePath);

                if (cacheFile.exists()) {
                    System.out.println("Loading cached commit pairs...");
                    List<CommitPairDTO> dtos = JsonUtils.readCommitPairDTOsFromFile(cacheFilePath);
                    commitPairs = CommitPairMapper.fromDTOs(repository, dtos);
                } else {
                    System.out.println("Generating commit pairs...");
                    commitPairs = GitUtils.processRepo(repoUrl, repoDir, extensions, false);
                    List<CommitPairDTO> dtos = CommitPairMapper.toDTOs(commitPairs);
                    JsonUtils.writeCommitPairDTOsToFile(dtos, cacheFilePath);
                }

            } catch (Exception e) {
                System.err.println("Error processing " + repoUrl + ": " + e.getMessage());
                continue;
            }

            if (commitPairs == null || commitPairs.isEmpty()) {
                System.out.println("No commits for " + repoUrl);
                continue;
            }

            JsonUtils.initializeJsonOutput(repoUrl);

            int totalPairs = commitPairs.size();
            System.out.println("Total commit pairs to process: " + totalPairs);

            for (int batchStart = 0; batchStart < totalPairs; batchStart += BATCH_SIZE) {
                int batchEnd = Math.min(batchStart + BATCH_SIZE, totalPairs);
                List<CommitPairWithFiles> batch = commitPairs.subList(batchStart, batchEnd);

                System.out.printf("Processing batch %d - %d / %d%n", batchStart + 1, batchEnd, totalPairs);
                processBatch(batch, repoDir, repoUrl);

                long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                System.out.println("Memory used (MB): " + used / (1024 * 1024));
            }

            JsonUtils.appendPatternCounts(repoUrl, repoPatternCounts);
            System.out.println("Wrote patternCounts for " + repoName);

            JsonUtils.filterUniqueOperations(repoUrl);

            repoManager.closeRepository();

            System.out.println();
        }
    }

    private void processBatch(List<CommitPairWithFiles> batch, String repoDir, String repoUrl) {
        long totalStart = System.currentTimeMillis();

        ExecutorService exec = Executors.newFixedThreadPool(THREAD_COUNT);
        CompletionService<CommitPairWithFiles> cs = new ExecutorCompletionService<>(exec);

        final int total = batch.size();
        List<FileResultDto> batchResults = Collections.synchronizedList(new ArrayList<>());

        // Silence spoon jdt errors
        System.setErr(new ErrorFilterPrintStream(System.err));

        long submissionStart = System.currentTimeMillis();
        for (CommitPairWithFiles pair : batch) {
            cs.submit(() -> {
                for (String file : pair.changedFiles()) {
                    long comparisonStart = System.nanoTime();
                    FileResult result = treeComparator.compareFileInTwoCommits(
                            repoDir, pair.oldCommit(), pair.newCommit(), file, false
                    );
                    long comparisonEnd = System.nanoTime();
                    //System.out.printf("Time to compare file %s: %.2f ms%n", file, (comparisonEnd - comparisonStart) / 1_000_000.0);

                    if (result != null) {
                        Map<String,Integer> fileMetrics = result.getMetrics();
                        if (fileMetrics != null) {
                            for (var entry : fileMetrics.entrySet()) {
                                String metricName = entry.getKey().toString();
                                int count = entry.getValue();
                                try {
                                    MutationKind kind = MutationKind.valueOf(metricName);
                                    repoPatternCounts.merge(kind, count, Integer::sum);
                                } catch (IllegalArgumentException ignored) {
                                    // skip non‐pattern metrics like EDITS
                                }
                            }
                        }

                        if (!result.getEditOperations().isEmpty()) {
                            FileResultDto dto = ResultMapper.toDto(result);
                            batchResults.add(dto);
                        }
                    }
                }
                return pair;
            });
        }
        long submissionEnd = System.currentTimeMillis();
        System.out.printf("Time to submit tasks: %.2f seconds%n", (submissionEnd - submissionStart) / 1000.0);

        int completed = 0;
        long processingStart = System.currentTimeMillis();
        while (completed < total) {
            try {
                Future<CommitPairWithFiles> future = cs.take();
                future.get();
                completed++;
                printProgressBar(completed, total);
            } catch (ExecutionException | InterruptedException e) {
                System.err.println("Task failed: " + e.getMessage());
            }
        }
        long processingEnd = System.currentTimeMillis();
        System.out.printf("Time to process batch: %.2f seconds%n", (processingEnd - processingStart) / 1000.0);

        exec.shutdown();
        System.out.println();

        long jsonStart = System.currentTimeMillis();
        JsonUtils.appendBatchResults(repoUrl, batchResults);
        batchResults.clear();
        long jsonEnd = System.currentTimeMillis();
        System.out.printf("Time to write JSON results: %.2f seconds%n", (jsonEnd - jsonStart) / 1000.0);

        System.gc();
        long totalEnd = System.currentTimeMillis();
        System.out.printf("Total time for processBatch: %.2f seconds%n", (totalEnd - totalStart) / 1000.0);
    }


    private  void printProgressBar(int current, int total) {
        int width = 50;
        int filled = (int)(width * current / (double)total);
        StringBuilder b = new StringBuilder("\r[");
        for (int i = 0; i < width; i++) {
            b.append(i < filled ? '=' : ' ');
        }
        b.append("] ")
                .append(String.format("%3d%%", (int)(current*100.0/total)));
        System.out.print(b);
        System.out.flush();
    }

    public  void manualTest() {


        String localRepoName = "D:\\Java projects\\mutation-analysis\\repositories\\spring-framework\\.git";
        String oldSha = "253f321e8b81aa5f652117514c37cb541f2e5ecf";
        String newSha = "014a395aed7b3f40bb05f1591149e833f2bf7537";
        String fileName = "spring-context/src/test/java/org/springframework/aop/framework/AbstractAopProxyTests.java";

        String outputDir = "D:\\Java projects\\mutation-analysis\\src\\main\\resources\\extractedFiles";
        cleanUp(outputDir);


        Repository repo = GitUtils.ensureClonedAndLoaded("", localRepoName);
        RevCommit oldCommit = null;
        RevCommit newCommit = null;
        try (RevWalk walk = new RevWalk(repo)) {
            oldCommit = walk.parseCommit(repo.resolve(oldSha));
            newCommit = walk.parseCommit(repo.resolve(newSha));
        } catch (Exception exception){
            exception.printStackTrace();
        }


        //List<String> extractedPaths = GitUtils.extractFileAtTwoCommits(localPath + "\\" + localRepoName, relativePath, oldSha, newSha, outputDir);
        //List<String> extractedPaths = List.of("D:\\\\Java projects\\\\mutation-analysis\\\\src\\\\main\\\\java\\\\com\\\\example\\\\test\\\\file1.java", "D:\\\\Java projects\\\\mutation-analysis\\\\src\\\\main\\\\java\\\\com\\\\example\\\\test\\\\file2.java");

        FileResult result = treeComparator.compareFileInTwoCommits("", oldCommit, newCommit, fileName, true);

        if (result != null) {
            System.out.println(result);
        } else {
            System.out.println("Comparison failed.");
        }

    }

    public  void cleanUp(String path) {
        try {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                FileUtils.deleteDirectory(dir);
            }
        } catch (IOException e) {
            System.err.println("Cleanup failed on: " + ((e instanceof FileSystemException)
                    ? ((FileSystemException)e).getFile() + "  because: " + ((FileSystemException)e).getReason()
                    : e.getMessage()));
            e.printStackTrace();
        }
    }
}
