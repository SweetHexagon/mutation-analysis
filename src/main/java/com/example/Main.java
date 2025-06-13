package com.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


import com.example.classifier.MutationKind;
import com.example.dto.CommitPairDTO;
import com.example.dto.FileResultDto;
import com.example.mapper.CommitPairMapper;
import com.example.mapper.ResultMapper;
import com.example.mutation_tester.mutation_metadata_processing.MutationLogParser;
import com.example.mutation_tester.mutations_applier.MutationApplier;
import com.example.pojo.FileResult;
import com.example.service.GitRepositoryManager;
import com.example.util.GitUtils;
import com.example.util.JsonUtils;
import com.example.mutation_tester.mutations_applier.custom_patterns.LoopBreakReplacement;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.SpringApplication;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.util.concurrent.*;

@SpringBootApplication
public class Main implements CommandLineRunner {
    private final GitRepositoryManager repoManager;
    private final GitUtils gitUtils;
    private final MutationApplier mutationApplier;

    ConcurrentMap<MutationKind, Integer> repoPatternCounts =
            new ConcurrentHashMap<>();


    private final ObjectProvider<TreeComparator> treeComparatorProvider;
    private  final int THREAD_COUNT = Runtime.getRuntime().availableProcessors()/2;


    public Main(GitRepositoryManager repoManager,
                TreeComparator treeComparator, GitUtils gitUtils, MutationApplier mutationApplier, ObjectProvider<TreeComparator> treeComparatorProvider) {
        this.repoManager     = repoManager;
        this.gitUtils = gitUtils;
        this.mutationApplier = mutationApplier;
        this.treeComparatorProvider = treeComparatorProvider;
    }


     String localPath = "repositories";


     final int BATCH_SIZE = 500;

    final String filteredDir  = "src/main/resources/programOutputFiltered";

     List<String> repoUrls = List.of(
            /*//"https://github.com/SweetHexagon/pitest-mutators"
            "https://github.com/Snailclimb/JavaGuide"    ,        // 5 800 commits
            "https://github.com/krahets/hello-algo",              // small
            "https://github.com/iluwatar/java-design-patterns",    // 4 327 commits
            "https://github.com/macrozheng/mall"   ,               // small
             "https://github.com/doocs/advanced-java",          // small
             //"https://github.com/spring-projects/spring-boot",     // 54 313 commits
            "https://github.com/MisterBooo/LeetCodeAnimation",     // small
            //"https://github.com/elastic/elasticsearch",            // 86 296 commits
            */"https://github.com/kdn251/interviews",                // small
            "https://github.com/TheAlgorithms/Java",               // 2 729 commits
             //"https://github.com/spring-projects/spring-framework" // 32 698 commits
            "https://github.com/NationalSecurityAgency/ghidra",    // 14 553 commits
             "https://github.com/Stirling-Tools/Stirling-PDF",      // small
            "https://github.com/google/guava",                     // 6 901 commits
            "https://github.com/ReactiveX/RxJava",                 // 6 218 commits
            "https://github.com/skylot/jadx",                      // small
            "https://github.com/dbeaver/dbeaver",                  // 27 028 commits
            "https://github.com/jeecgboot/JeecgBoot",              // small
            "https://github.com/apache/dubbo",                     // 8 414 commits
            "https://github.com/termux/termux-app",                // small
             "https://github.com/jhy/jsoup"
    );


     List<String> extensions = List.of(
            ".java");

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        //mutationApplier.triggerCrashOnMutatedFiles(MutationApplier.CustomMutations.PROCESS_ALL, "D:\\Java projects\\mutation-analysis\\repositories_for_tests\\jsoup");

        //mutationApplier.triggerCrashOnMutatedFiles(MutationApplier.CustomMutations.LOOP_BREAK_REPLACEMENT, "D:\\Java projects\\mutation-analysis\\repositories_for_tests\\jsoup");
        //mutationApplier.applyMutationToProjectDirectory(MutationApplier.CustomMutations.LOOP_BREAK_REPLACEMENT, "D:\\Java projects\\mutation-analysis\\repositories_for_tests\\jsoup");

        /*var temp = MutationLogParser.parseMutationTrace("D:\\Java projects\\mutation-analysis\\repositories_for_tests\\jsoup\\mutation-trace.log");

        Set<String> uniqueValues = new HashSet<>();
        for (Set<String> valueSet : temp.values()) {
            uniqueValues.addAll(valueSet);
        }
        System.out.println(uniqueValues.size());*/

        //MutationLogParser.printMethodToTests(temp);

        //loopBreakReplacementTest();

        //manualTest();

        //manualTestLocal();

        //presentation(repoUrls);

        JsonUtils.aggregateUniqueOperations(filteredDir, "src/main/resources/uniqueEditOperations/aggregated_unique_operations.json");
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

    private void processBatch(List<CommitPairWithFiles> batch, String repoDir, String repoUrl)
    {
        long totalStart = System.currentTimeMillis();

        int totalFiles = batch.stream()
                .mapToInt(pair -> pair.changedFiles().size())
                .sum();
        System.out.printf("Processing %d commit pairs with a total of %d changed files%n", batch.size(), totalFiles);

        ExecutorService exec = Executors.newFixedThreadPool(THREAD_COUNT);
        CompletionService<Void> cs = new ExecutorCompletionService<>(exec);

        Queue<FileResultDto> batchResults = new ConcurrentLinkedQueue<>();
        ConcurrentMap<MutationKind, Integer> safePatternCounts = new ConcurrentHashMap<>();

        // Silence Spoon JDT errors
        System.setErr(new ErrorFilterPrintStream(System.err));

        ThreadLocal<TreeComparator> localComparator = ThreadLocal.withInitial(() -> treeComparatorProvider.getObject());

        long submissionStart = System.currentTimeMillis();
        int taskCount = 0;

        for (CommitPairWithFiles pair : batch) {
            for (String file : pair.changedFiles()) {
                cs.submit(() -> {
                    if (Thread.currentThread().isInterrupted()) return null;

                    FileResult result = localComparator.get().compareFileInTwoCommits(
                            repoDir, pair.oldCommit(), pair.newCommit(), file, false
                    );

                    if (result != null) {

                        Map<String, Integer> fileMetrics = result.getMetrics();
                        if (fileMetrics != null) {
                            fileMetrics.forEach((key, count) -> {
                                try {
                                    MutationKind kind = MutationKind.valueOf(key);
                                    safePatternCounts.merge(kind, count, Integer::sum);
                                } catch (IllegalArgumentException ignored) {
                                    // Ignore non-pattern metrics
                                }
                            });
                        }

                        if (!result.getEditOperations().isEmpty()) {
                            FileResultDto dto = ResultMapper.toDto(result);
                            batchResults.add(dto);
                        }

                    }



                    return null;
                });
                taskCount++;
            }
        }

        long submissionEnd = System.currentTimeMillis();
        System.out.printf("Time to submit %d tasks: %.2f seconds%n", taskCount, (submissionEnd - submissionStart) / 1000.0);

        int completed = 0;
        long processingStart = System.currentTimeMillis();
        long lastPrintTime = System.currentTimeMillis();

        while (completed < taskCount) {
            try {
                Future<Void> future = cs.take();
                future.get();
                completed++;

                long now = System.currentTimeMillis();
                if (now - lastPrintTime >= 1000 || completed == taskCount) { // throttle to once per second or on final
                    printProgressBar(completed, taskCount);
                    lastPrintTime = now;
                }

            } catch (ExecutionException e) {
                System.err.println("Task failed: " + e.getCause());
            } catch (InterruptedException e) {
                System.err.println("Processing interrupted.");
                Thread.currentThread().interrupt(); // Preserve interrupt flag
                break;
            }
        }

        long processingEnd = System.currentTimeMillis();
        System.out.printf("Time to process batch: %.2f seconds%n", (processingEnd - processingStart) / 1000.0);

        exec.shutdown();
        System.out.println();

        long jsonStart = System.currentTimeMillis();
        JsonUtils.appendBatchResults(repoUrl, new ArrayList<>(batchResults));
        batchResults.clear();
        long jsonEnd = System.currentTimeMillis();
        System.out.printf("Time to write JSON results: %.2f seconds%n", (jsonEnd - jsonStart) / 1000.0);

        long totalEnd = System.currentTimeMillis();
        System.out.printf("Total time for processBatch: %.2f seconds%n", (totalEnd - totalStart) / 1000.0);

        // Update shared repoPatternCounts after batch is done
        safePatternCounts.forEach((k, v) ->
                repoPatternCounts.merge(k, v, Integer::sum)
        );
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

    public void loopBreakReplacementTest(){
        try {
            Path inputPath = Paths.get("src/main/java/com/example/mutations_applier/test/before.java");
            // Path to the output file "after.java"
            Path outputPath = Paths.get("src/main/java/com/example/mutations_applier/test/after.java");

            // Read the content of "before.java"
            String originalCode = new String(Files.readAllBytes(inputPath));

            // Apply the mutation using loopBreakReplacement
            LoopBreakReplacement mutator = new LoopBreakReplacement();
            String mutatedCode = mutator.mutate(originalCode);

            // Write the mutated code to "after.java"
            Files.write(outputPath, mutatedCode.getBytes());

            System.out.println("Mutation applied. Check after.java.");
        } catch (IOException e) {
            e.printStackTrace();
        }
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

        TreeComparator localComparator = treeComparatorProvider.getObject();
        FileResult result = localComparator.compareFileInTwoCommits("", oldCommit, newCommit, fileName, true);

        if (result != null) {
            System.out.println(result);
        } else {
            System.out.println("Comparison failed.");
        }

    }

    public  void manualTestLocal() {

        TreeComparator localComparator = treeComparatorProvider.getObject();
        FileResult result = localComparator.compareTwoFilePaths(
                "D:\\Java projects\\mutation-analysis\\src\\main\\java\\com\\example\\test\\file1.java",
                "D:\\Java projects\\mutation-analysis\\src\\main\\java\\com\\example\\test\\file2.java",
                true
        );

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
