package com.example;

import com.example.dto.CommitPairDTO;
import com.example.dto.FileResultDto;
import com.example.mapper.CommitPairMapper;
import com.example.mapper.ResultMapper;
import com.example.pojo.FileResult;
import com.example.service.GitRepositoryManager;
import com.example.util.GitUtils;
import com.example.util.JsonUtils;
import org.apache.commons.io.FileUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.SpringApplication;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

@SpringBootApplication
public class Main implements CommandLineRunner {
    private final GitRepositoryManager repoManager;

    public Main(GitRepositoryManager repoManager) {
        this.repoManager = repoManager;
    }

    static String localPath = "repositories";

    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();

    static final int BATCH_SIZE = 500;

    final String filteredDir  = "src/main/resources/programOutputFiltered";

    static List<String> repoUrls = List.of(
            "https://github.com/SweetHexagon/pitest-mutators"
            ////"https://github.com/Snailclimb/JavaGuide",            // 5 800 commits
            ////"https://github.com/krahets/hello-algo",               // small
            ////"https://github.com/iluwatar/java-design-patterns",    // 4 327 commits
            ////"https://github.com/macrozheng/mall",                  // small
            ////"https://github.com/doocs/advanced-java",              // small
            //"https://github.com/spring-projects/spring-boot",     // 54 313 commits
            ////"https://github.com/MisterBooo/LeetCodeAnimation",     // small
            //"https://github.com/elastic/elasticsearch",            // 86 296 commits
            ////"https://github.com/kdn251/interviews",                // small
            ////"https://github.com/TheAlgorithms/Java",               // 2 729 commits
            //"https://github.com/spring-projects/spring-framework", // 32 698 commits
            ////"https://github.com/NationalSecurityAgency/ghidra",    // 14 553 commits
            ////"https://github.com/Stirling-Tools/Stirling-PDF",      // small
            ////"https://github.com/google/guava",                     // 6 901 commits
            ////"https://github.com/ReactiveX/RxJava",                 // 6 218 commits
            ////"https://github.com/skylot/jadx",                      // small
            //"https://github.com/dbeaver/dbeaver"                  // 27 028 commits
            ////"https://github.com/jeecgboot/JeecgBoot",              // small
            ////"https://github.com/apache/dubbo",                     // 8 414 commits
            ////"https://github.com/termux/termux-app"                 // small
    );


    static List<String> extensions = List.of(
            ".java");

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        manualTest();

        //presentation(repoUrls);

        //JsonUtils.aggregateUniqueOperations(filteredDir, "src/main/resources/uniqueEditOperations/aggregated_unique_operations.json");
    }

    public void presentation(List<String> repoUrls) throws InterruptedException {
        for (String repoUrl : repoUrls) {
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

            JsonUtils.filterUniqueOperations(repoUrl);

            repoManager.closeRepository();

            System.out.println();
        }
    }

    private static void processBatch(List<CommitPairWithFiles> batch, String repoDir, String repoUrl) {
        ExecutorService exec = Executors.newFixedThreadPool(THREAD_COUNT);
        CompletionService<CommitPairWithFiles> cs = new ExecutorCompletionService<>(exec);

        final int total = batch.size();
        List<FileResultDto> batchResults = Collections.synchronizedList(new ArrayList<>());

        for (CommitPairWithFiles pair : batch) {
            cs.submit(() -> {
                for (String file : pair.changedFiles()) {
                    FileResult result = TreeComparator.compareFileInTwoCommits(
                            repoDir,
                            pair.oldCommit(),
                            pair.newCommit(),
                            file,
                            true
                    );
                    if (result != null) {
                        int ted = result.getMetrics().get(Metrics.TREE_EDIT_DISTANCE);
                        if (ted > 0 && ted < 10) {
                            batchResults.add(ResultMapper.toDto(result));
                        }
                    }
                }
                return pair;
            });
        }

        int completed = 0;
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

        exec.shutdown();
        System.out.println();

        JsonUtils.appendBatchResults(repoUrl, batchResults);
        batchResults.clear();
        System.gc();
    }

    private static void printProgressBar(int current, int total) {
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

    public static void manualTest() {
        String repoName = "elasticsearch";
        String oldSha = "0a599f924bf91e22ec8fa2a3d327bf224f8a5f25";
        String newSha = "7abbaf0a24c970021992bd909236cf563d28ebc4";
        String relativePath = "x-pack/plugin/core/src/test/java/org/elasticsearch/xpack/core/security/authz/store/ReservedRolesStoreTests.java";
        String outputDir = "D:\\Java projects\\mutation-analysis\\src\\main\\resources\\extractedFiles";

        cleanUp(outputDir);

        //List<String> extractedPaths = GitUtils.extractFileAtTwoCommits(localPath + "\\" + repoName, relativePath, oldSha, newSha, outputDir);
        List<String> extractedPaths = List.of("D:\\\\Java projects\\\\mutation-analysis\\\\src\\\\main\\\\java\\\\com\\\\example\\\\test\\\\file1.java", "D:\\\\Java projects\\\\mutation-analysis\\\\src\\\\main\\\\java\\\\com\\\\example\\\\test\\\\file2.java");
        if (extractedPaths.size() == 2) {

            //String oldFilePath = extractedPaths.get(0);
            //String newFilePath = extractedPaths.get(1);
            //printFileContents("Old File", oldFilePath);
            //printFileContents("New File", newFilePath);

            FileResult result = TreeComparator.compareTwoFilePaths(extractedPaths.get(0), extractedPaths.get(1), true);

            if (result != null) {
                System.out.println(result);
            } else {
                System.out.println("Comparison failed.");
            }
        } else {
            System.out.println("File extraction failed.");
        }
    }

    public static void cleanUp(String path) {
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
