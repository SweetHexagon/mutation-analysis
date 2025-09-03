package com.example;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.example.classifier.MutationKind;
import com.example.dto.CommitPairDTO;
import com.example.dto.FileResultDto;
import com.example.mapper.CommitPairMapper;
import com.example.mapper.ResultMapper;
import com.example.mutation_tester.mutation_metadata_processing.MethodCallMapper;
import com.example.mutation_tester.mutations_applier.MutationApplier;
import com.example.pojo.FileResult;
import com.example.service.GitRepositoryManager;
import com.example.util.GitUtils;
import com.example.util.JsonUtils;
import com.example.mutation_tester.mutations_applier.custom_patterns.LoopBreakReplacement;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.maven.shared.invoker.*;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.SpringApplication;

import java.nio.file.FileSystemException;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SpringBootApplication
public class Main implements CommandLineRunner {
    private final GitRepositoryManager repoManager;
    private final GitUtils gitUtils;
    private final MutationApplier mutationApplier;

    ConcurrentMap<MutationKind, Integer> repoPatternCounts =
            new ConcurrentHashMap<>();

    private final ObjectProvider<TreeComparator> treeComparatorProvider;
    private final int THREAD_COUNT = Runtime.getRuntime().availableProcessors()/2;

    String localPath = "repositories";
    final int BATCH_SIZE = 500;
    final String filteredDir = "src/main/resources/programOutputFiltered";

    List<String> repoUrls = List.of(
            "https://github.com/kdn251/interviews",
            "https://github.com/TheAlgorithms/Java",
            "https://github.com/NationalSecurityAgency/ghidra",
            "https://github.com/Stirling-Tools/Stirling-PDF",
            "https://github.com/google/guava",
            "https://github.com/ReactiveX/RxJava",
            "https://github.com/skylot/jadx",
            "https://github.com/dbeaver/dbeaver",
            "https://github.com/jeecgboot/JeecgBoot",
            "https://github.com/apache/dubbo",
            "https://github.com/termux/termux-app",
            "https://github.com/jhy/jsoup"
    );

    List<String> extensions = List.of(".java");

    public Main(GitRepositoryManager repoManager,
                TreeComparator treeComparator,
                GitUtils gitUtils,
                MutationApplier mutationApplier,
                ObjectProvider<TreeComparator> treeComparatorProvider) {
        this.repoManager = repoManager;
        this.gitUtils = gitUtils;
        this.mutationApplier = mutationApplier;
        this.treeComparatorProvider = treeComparatorProvider;
    }

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @Override
    public void run(String... args) throws Exception {

        // --- Configuration Paths ---
        String projectPath = "D:\\Java projects\\mutation-analysis\\repositories_for_tests\\jsoup";
        String pitReportPath = projectPath + "/target/pit-reports/linecoverage.xml";
        String jsonMapPath = "method-test-mapping.json";

        // =========================================================================
        // STEP 0: Generate the Method-to-Test Map from the PIT Report
        // =========================================================================
        System.out.println("STEP 0: Generating method-to-test map...");
        MethodCallMapper mapper = new MethodCallMapper();
        mapper.processCoverage(pitReportPath, "");
        System.out.println("✅ Map generation complete. Saved to: " + jsonMapPath);

        // =========================================================================
        // STEP 1: Apply Mutations and Get List of Changed Methods
        // =========================================================================
        System.out.println("\nSTEP 1: Applying mutations...");
        var mutationResults = mutationApplier.applyMutationToProjectDirectory(
                MutationApplier.CustomMutations.LOOP_BREAK_REPLACEMENT, projectPath);
        Set<String> mutatedMethods = new HashSet<>(mutationResults);
        System.out.println("✅ Found " + mutatedMethods.size() + " unique mutated methods across all files.");

        // =========================================================================
        // STEP 2: Load the Full Test Map from the Generated JSON
        // =========================================================================
        System.out.println("\nSTEP 2: Loading test map from JSON...");
        Map<String, Set<String>> allTestsMap = loadTestMapFromJson(jsonMapPath);

        // =========================================================================
        // STEP 3: Filter for Tests Covering Mutated Methods
        // =========================================================================
        System.out.println("\nSTEP 3: Filtering tests for mutated methods...");
        Map<String, Set<String>> testsToRunMap = allTestsMap.entrySet().stream()
                .filter(entry -> mutatedMethods.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        System.out.println("✅ Found " + testsToRunMap.values().stream().mapToLong(Set::size).sum()
                + " relevant tests to run.");
        long uniqueCount = testsToRunMap.values().stream()
                .flatMap(Set::stream)
                .distinct()
                .count();
        System.out.println("✅ Found " + uniqueCount + " unique relevant tests to run.");

        // =========================================================================
        // STEP 4: Execute Relevant Tests and Collect Failures
        // =========================================================================
        System.out.println("\nSTEP 4: Executing relevant tests...");
        Map<String, Set<String>> failedTestsReport = runBatchedMavenTests(testsToRunMap, projectPath);

        // =========================================================================
        // STEP 5: Print Final Report
        // =========================================================================
        System.out.println("\n--- FINAL REPORT ---");
        if (failedTestsReport.isEmpty()) {
            System.out.println("✅ All relevant tests passed. The mutations were not detected by the test suite.");
        } else {
            System.out.println("❌ Mutation Detected! Found " + failedTestsReport.size() + " methods with failing tests.");
        }

        // =====================
        // STATISTICS PER METHOD
        // =====================
        System.out.println("\n--- STATISTICS PER MUTATED METHOD ---");
        testsToRunMap.forEach((method, relevantTests) -> {
            int total = relevantTests.size();
            int failed = failedTestsReport.getOrDefault(method, Collections.emptySet()).size();
            int passed = total - failed;
            System.out.println("Method: " + method);
            System.out.println("  Total relevant tests: " + total);
            System.out.println("  Passed: " + passed);
            System.out.println("  Failed: " + failed);
        });
    }

    private static Map<String, Set<String>> loadTestMapFromJson(String jsonFilePath) {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Set<String>> testMap = new HashMap<>();

        try {
            Map<String, List<String>> rawMap = objectMapper.readValue(
                    new File(jsonFilePath),
                    new TypeReference<Map<String, List<String>>>() {}
            );

            for (Map.Entry<String, List<String>> entry : rawMap.entrySet()) {
                testMap.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }

            System.out.println("Successfully loaded test map from: " + jsonFilePath);

        } catch (IOException e) {
            System.err.println("Error loading test map from JSON file: " + jsonFilePath);
            System.err.println("Error details: " + e.getMessage());
        }
        return testMap;
    }

    private static final Pattern TEST_SUMMARY_PATTERN = Pattern.compile("Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)");
    private static final Pattern FAILURE_LINE_PATTERN = Pattern.compile("\\[ERROR\\]\\s+([^\\s]+)\\s+--\\s+Time elapsed:.*<<< FAILURE!");
    private static final Pattern ERROR_LINE_PATTERN = Pattern.compile("\\[ERROR\\]\\s+([^\\s]+)\\s+--\\s+Time elapsed:.*<<< ERROR!");

    public Map<String, Set<String>> runBatchedMavenTests(
            Map<String, Set<String>> testsToRunMap,
            String projectPath
    ) throws IOException, MavenInvocationException {
        Map<String, Set<String>> failedTestsReport = new HashMap<>();
        int totalRun = 0, totalFailures = 0, totalErrors = 0, totalSkipped = 0;

        Invoker invoker = new DefaultInvoker();
        invoker.setWorkingDirectory(new File(projectPath));
        File mavenHome = new File(System.getenv("MAVEN_HOME"));
        if (!mavenHome.isDirectory()) {
            throw new IllegalStateException("MAVEN_HOME must point to a valid directory");
        }
        invoker.setMavenHome(mavenHome);

        for (Map.Entry<String, Set<String>> entry : testsToRunMap.entrySet()) {
            String batchKey = entry.getKey();
            Set<String> tests = entry.getValue();
            if (tests.isEmpty()) continue;

            Path includesFile = null;
            try {
                includesFile = Files.createTempFile("mvn-includes-", ".txt");
                Files.write(includesFile, tests, StandardCharsets.UTF_8);

                InvocationRequest req = new DefaultInvocationRequest();
                req.setPomFile(new File(projectPath, "pom.xml"));
                req.setGoals(Collections.singletonList("test"));
                req.setInputStream(InputStream.nullInputStream());

                Properties props = new Properties();
                props.setProperty("surefire.includesFile", includesFile.toAbsolutePath().toString());
                props.setProperty("maven.test.failure.ignore", "true");
                req.setProperties(props);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStreamHandler handler = new PrintStreamHandler(new PrintStream(baos), false);
                invoker.setOutputHandler(handler);
                invoker.setErrorHandler(handler);

                InvocationResult result = invoker.execute(req);
                String mvnLog = baos.toString();

                Matcher summaryMatcher = TEST_SUMMARY_PATTERN.matcher(mvnLog);
                int run = 0, failures = 0, errors = 0, skipped = 0;
                while (summaryMatcher.find()) {
                    run = Integer.parseInt(summaryMatcher.group(1));
                    failures = Integer.parseInt(summaryMatcher.group(2));
                    errors = Integer.parseInt(summaryMatcher.group(3));
                    skipped = Integer.parseInt(summaryMatcher.group(4));
                }
                if (run == 0 && result.getExitCode() != 0) {
                    run = tests.size();
                    failures = tests.size();
                }

                int passed = run - failures - errors - skipped;
                totalRun += run;
                totalFailures += failures;
                totalErrors += errors;
                totalSkipped += skipped;

                System.out.printf(
                        "[BATCH %s] run=%d, passed=%d, failures=%d, errors=%d, skipped=%d%n",
                        batchKey, run, passed, failures, errors, skipped
                );

                // THE KEY FIX: Collect ALL failed tests, not just those in the original test set
                Set<String> failedThisBatch = new HashSet<>();

                Matcher fm = FAILURE_LINE_PATTERN.matcher(mvnLog);
                while (fm.find()) {
                    String formatted = convertSurefireToCustomFormat(fm.group(1));
                    failedThisBatch.add(formatted);
                }

                Matcher em = ERROR_LINE_PATTERN.matcher(mvnLog);
                while (em.find()) {
                    String formatted = convertSurefireToCustomFormat(em.group(1));
                    failedThisBatch.add(formatted);
                }

                if (!failedThisBatch.isEmpty()) {
                    failedTestsReport.put(batchKey, failedThisBatch);
                }

            } finally {
                if (includesFile != null) {
                    Files.deleteIfExists(includesFile);
                }
                invoker.setOutputHandler(new PrintStreamHandler(System.out, true));
                invoker.setErrorHandler(new PrintStreamHandler(System.err, true));
            }
        }

        int totalPassed = totalRun - totalFailures - totalErrors - totalSkipped;
        System.out.printf(
                "TOTAL → run=%d, passed=%d, failures=%d, errors=%d, skipped=%d%n",
                totalRun, totalPassed, totalFailures, totalErrors, totalSkipped
        );

        return failedTestsReport;
    }

    private static String convertSurefireToCustomFormat(String surefireFormat) {
        if (surefireFormat == null || surefireFormat.isEmpty()) {
            return surefireFormat;
        }
        int lastDot = surefireFormat.lastIndexOf(".");
        if (lastDot != -1) {
            String className = surefireFormat.substring(0, lastDot);
            String methodName = surefireFormat.substring(lastDot + 1);
            return className + "#" + methodName;
        }
        return surefireFormat;
    }

    // Helper method to extract failed test names from Maven output.
// This is a rudimentary parser; for production, consider parsing Surefire's XML reports.
    private static String extractFailedTestName(String line, Set<String> allTestsInBatch) {
        // Example lines:
        // [ERROR] org.jsoup.nodes.AttributesTest.testListSkipsInternal -- Time elapsed: 0.035 s <<< FAILURE!
        // [ERROR] Failures: AttributesTest.testListSkipsInternal
        // [ERROR] Error: TestClass.testMethod
        if (line.contains("<<< FAILURE!")) {
            // Try to find the full test name (class#method)
            int classMethodEnd = line.indexOf(" -- Time elapsed:");
            if (classMethodEnd != -1) {
                String candidate = line.substring(line.indexOf("]") + 2, classMethodEnd).trim();
                // Need to convert to your format (e.g., org.jsoup.nodes.AttributesTest#testListSkipsInternal)
                if (candidate.contains(".")) {
                    int lastDot = candidate.lastIndexOf(".");
                    if (lastDot != -1) {
                        String className = candidate.substring(0, lastDot);
                        String methodName = candidate.substring(lastDot + 1);
                        String formatted = className + "#" + methodName;
                        if (allTestsInBatch.contains(formatted)) {
                            return formatted;
                        }
                        // Also check for just class name if the test filter was a class
                        if (allTestsInBatch.contains(className)) {
                            return className;
                        }
                    }
                }
            }
        } else if (line.contains("[ERROR] Failures: ") || line.contains("[ERROR] Error: ")) {
            String testPart = line.substring(line.indexOf(":") + 1).trim();
            // It might be "TestClass.testMethod" or "TestClass.testMethod: lineNumber"
            int colonIndex = testPart.indexOf(":");
            if (colonIndex != -1) {
                testPart = testPart.substring(0, colonIndex);
            }
            testPart = testPart.trim();

            // Convert Surefire's "package.Class.method" to your "package.Class#method"
            int lastDot = testPart.lastIndexOf(".");
            if (lastDot != -1) {
                String className = testPart.substring(0, lastDot);
                String methodName = testPart.substring(lastDot + 1);
                String formatted = className + "#" + methodName;
                if (allTestsInBatch.contains(formatted)) {
                    return formatted;
                }
                // Also check for just class name if the test filter was a class
                if (allTestsInBatch.contains(className)) {
                    return className;
                }
            } else {
                // If it's just a class name (e.g., "MyTestClass")
                if (allTestsInBatch.contains(testPart)) {
                    return testPart;
                }
            }
        }
        return null; // Test name not found or line didn't match expected failure pattern
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
            String mutatedCode = mutator.mutate(originalCode).getMutatedUnit().toString();

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
