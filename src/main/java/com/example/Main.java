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
import com.example.mutation_tester.mutations_applier.custom_patterns.TakeWhileDropWhileReplacement;
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
        //String projectPath = "D:\\Java projects\\mutation-analysis\\repositories_for_tests\\jsoup";
        String projectPath = "D:\\Java projects\\mutation-analysis\\repositories_for_tests\\commons-lang";

        // Extract project name from path
        String projectName = extractProjectName(projectPath);
        System.out.println("Working with project: " + projectName);

        // =========================================================================
        // STEP -2: Check if PITest reports already exist BEFORE any clean/build
        // =========================================================================
        System.out.println("STEP -2: Checking for existing PITest reports...");

        // Check if PITest reports already exist
        File pitReportsDir = new File(projectPath, "target/pit-reports");
        File lineCoverageFile = new File(pitReportsDir, "linecoverage.xml");

        boolean pitestReportsExist = lineCoverageFile.exists() && lineCoverageFile.isFile();

        if (pitestReportsExist) {
            System.out.println("✅ PITest reports already exist, skipping compilation and PITest execution.");
            System.out.println("✅ Found existing linecoverage.xml at: " + lineCoverageFile.getAbsolutePath());
        } else {
            System.out.println("❌ No existing PITest reports found.");

            // =========================================================================
            // STEP -1: Setup PITest Plugin and Compile Project (only if reports don't exist)
            // =========================================================================
            System.out.println("STEP -1: Setting up PITest plugin and compiling project...");
            setupPitestPlugin(projectPath);
            compileMavenProject(projectPath);

            // Run PITest mutation coverage analysis
            runPitestMutationCoverage(projectPath);

            System.out.println("✅ Project setup and compilation complete.");
        }

        String pitReportPath = projectPath + "/target/pit-reports/linecoverage.xml";
        String jsonMapPath = projectName + "-method-test-mapping.json";

        // =========================================================================
        // STEP 0: Generate the Method-to-Test Map from the PIT Report
        // =========================================================================
        System.out.println("\nSTEP 0: Generating method-to-test map...");
        MethodCallMapper mapper = new MethodCallMapper();
        mapper.processCoverage(pitReportPath, "", projectName);
        System.out.println("✅ Map generation complete. Saved to: " + jsonMapPath);

        // =========================================================================
        // STEP 1: Get List of All Mutable Methods (without applying mutations yet)
        // =========================================================================
        System.out.println("\nSTEP 1: Identifying mutable methods...");
        var potentialMutationResults = mutationApplier.identifyMutableMethods(
                MutationApplier.CustomMutations.SAFE_STREAM_METHOD_REPLACEMENT, projectPath);
        Set<String> mutableMethods = new HashSet<>(potentialMutationResults);
        System.out.println("✅ Found " + mutableMethods.size() + " mutable methods across all files.");

        // =========================================================================
        // STEP 2: Load the Full Test Map from the Generated JSON
        // =========================================================================
        System.out.println("\nSTEP 2: Loading test map from JSON...");
        Map<String, Set<String>> allTestsMap = loadTestMapFromJson(jsonMapPath);

        // =========================================================================
        // STEP 3: Filter for Tests Covering Mutable Methods
        // =========================================================================
        System.out.println("\nSTEP 3: Filtering tests for mutable methods...");
        Map<String, Set<String>> testsToRunMap = allTestsMap.entrySet().stream()
                .filter(entry -> mutableMethods.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        System.out.println("✅ Found " + testsToRunMap.values().stream().mapToLong(Set::size).sum()
                + " relevant tests to run.");
        long uniqueCount = testsToRunMap.values().stream()
                .flatMap(Set::stream)
                .distinct()
                .count();
        System.out.println("✅ Found " + uniqueCount + " unique relevant tests to run.");

        // =========================================================================
        // STEP 4: Mutate and Test Each Method Individually
        // =========================================================================
        System.out.println("\nSTEP 4: Testing mutations one method at a time...");

        Map<String, Set<String>> globalFailedTestsReport = new HashMap<>();
        int methodCount = 0;
        int totalMethods = testsToRunMap.size();

        for (Map.Entry<String, Set<String>> entry : testsToRunMap.entrySet()) {
            String methodSignature = entry.getKey();
            Set<String> relevantTests = entry.getValue();
            methodCount++;

            System.out.printf("\n[%d/%d] Processing method: %s%n", methodCount, totalMethods, methodSignature);
            System.out.println("  Relevant tests: " + relevantTests.size());

            try {
                // Reset project to clean state
                System.out.println("  🔄 Resetting project to clean state...");
                resetProjectToCleanState(projectPath);

                // Apply mutation to this specific method only
                System.out.println("  🧬 Applying mutation to method...");
                boolean mutationApplied = mutationApplier.applyMutationToSpecificMethod(
                        MutationApplier.CustomMutations.SAFE_STREAM_METHOD_REPLACEMENT,
                        projectPath,
                        methodSignature);

                if (!mutationApplied) {
                    System.out.println("  ⚠️ No mutation could be applied to this method, skipping...");
                    continue;
                }

                // Run tests for this method
                System.out.println("  🧪 Running tests for mutated method...");
                Map<String, Set<String>> methodTestsMap = new HashMap<>();
                methodTestsMap.put(methodSignature, relevantTests);

                Map<String, Set<String>> failedTestsForMethod = runBatchedMavenTests(methodTestsMap, projectPath);

                // Collect results
                if (!failedTestsForMethod.isEmpty()) {
                    globalFailedTestsReport.putAll(failedTestsForMethod);
                    System.out.println("  ✅ Mutation detected! " + failedTestsForMethod.get(methodSignature).size() + " tests failed.");
                } else {
                    System.out.println("  ❌ Mutation not detected - all tests passed.");
                }

            } catch (Exception e) {
                System.err.println("  ❌ Error processing method " + methodSignature + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Final reset to clean state
        System.out.println("\n🔄 Final reset to clean state...");
        resetProjectToCleanState(projectPath);

        // =========================================================================
        // STEP 5: Print Final Report
        // =========================================================================
        System.out.println("\n--- FINAL REPORT ---");
        if (globalFailedTestsReport.isEmpty()) {
            System.out.println("✅ No mutations were detected by the test suite.");
        } else {
            System.out.println("❌ Mutations Detected! Found " + globalFailedTestsReport.size() + " methods with failing tests.");
        }

        // =====================
        // STATISTICS PER METHOD
        // =====================
        System.out.println("\n--- STATISTICS PER MUTATED METHOD ---");
        testsToRunMap.forEach((method, relevantTests) -> {
            int totalRelevant = relevantTests.size();
            Set<String> failedTests = globalFailedTestsReport.getOrDefault(method, Collections.emptySet());
            int failed = failedTests.size();

            // For passed calculation, we need to be more careful
            // If we have failed tests, then passed = relevant - failed
            // But we need to make sure we don't go negative
            int passed = Math.max(0, totalRelevant - failed);

            // Calculate mutant survival and effectiveness
            boolean mutantSurvived = failed == 0;
            // CORRECTED: Mutant is MORE effective when MORE tests PASS (mutation not detected)
            double effectiveness = totalRelevant > 0 ? (double) passed / totalRelevant * 100.0 : 0.0;

            System.out.println("Method: " + method);
            System.out.println("  Total relevant tests: " + totalRelevant);
            System.out.println("  Passed: " + passed);
            System.out.println("  Failed: " + failed);
            System.out.printf("  Mutant survived: %s%n", mutantSurvived ? "YES" : "NO");
            System.out.printf("  Mutant effectiveness: %.1f%% (%d/%d tests passed after mutation)%n",
                    effectiveness, passed, totalRelevant);
            System.out.println();
        });

        // =====================
        // SUMMARY STATISTICS
        // =====================
        System.out.println("--- MUTATION ANALYSIS SUMMARY ---");
        totalMethods = testsToRunMap.size();
        int detectedMutants = globalFailedTestsReport.size();
        int survivedMutants = totalMethods - detectedMutants;
        double overallDetectionRate = totalMethods > 0 ? (double) detectedMutants / totalMethods * 100.0 : 0.0;

        int totalRelevantTests = testsToRunMap.values().stream().mapToInt(Set::size).sum();
        int totalFailedTests = globalFailedTestsReport.values().stream().mapToInt(Set::size).sum();
        int totalPassedTests = totalRelevantTests - totalFailedTests;
        // CORRECTED: Overall mutant effectiveness = how many tests passed across all mutations
        double overallMutantEffectiveness = totalRelevantTests > 0 ? (double) totalPassedTests / totalRelevantTests * 100.0 : 0.0;

        System.out.printf("Total mutated methods: %d%n", totalMethods);
        System.out.printf("Detected mutants: %d%n", detectedMutants);
        System.out.printf("Survived mutants: %d%n", survivedMutants);
        System.out.printf("Mutation detection rate: %.1f%%%n", overallDetectionRate);
        System.out.printf("Overall mutant effectiveness: %.1f%% (%d passed out of %d total relevant tests)%n",
                overallMutantEffectiveness, totalPassedTests, totalRelevantTests);
        System.out.printf("Test suite strength: %.1f%% (%d failed out of %d total relevant tests)%n",
                100.0 - overallMutantEffectiveness, totalFailedTests, totalRelevantTests);
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

            try {
                // Convert test format for Maven Surefire
                Set<String> testClasses = new HashSet<>();
                for (String test : tests) {
                    if (test.contains("#")) {
                        // Extract class name from "package.Class#method"
                        String className = test.substring(0, test.indexOf("#"));
                        testClasses.add(className);
                    } else {
                        // If it's just a class name
                        testClasses.add(test);
                    }
                }

                // Join test classes with comma for -Dtest parameter
                String testParameter = String.join(",", testClasses);

                // Debug: print what tests we're running
                System.out.println("DEBUG: Running tests for method " + batchKey + ":");
                System.out.println("  Original tests: " + tests.size());
                System.out.println("  Test classes: " + testClasses.size());
                System.out.println("  Test parameter: " + testParameter);

                InvocationRequest req = new DefaultInvocationRequest();
                req.setPomFile(new File(projectPath, "pom.xml"));
                req.setGoals(Collections.singletonList("test"));
                req.setInputStream(InputStream.nullInputStream());

                Properties props = new Properties();
                // Use -Dtest parameter instead of includesFile
                props.setProperty("test", testParameter);
                props.setProperty("maven.test.failure.ignore", "true");
                props.setProperty("failIfNoTests", "false");
                req.setProperties(props);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStreamHandler handler = new PrintStreamHandler(new PrintStream(baos), false);
                invoker.setOutputHandler(handler);
                invoker.setErrorHandler(handler);

                InvocationResult result = invoker.execute(req);
                String mvnLog = baos.toString();

                // Debug: print Maven log excerpt for verification
                System.out.println("DEBUG: Maven execution result (exit code: " + result.getExitCode() + ")");
                String[] logLines = mvnLog.split("\n");
                for (String logLine : logLines) {
                    if (logLine.contains("Tests run:") || logLine.contains("Running") ||
                        logLine.contains("No tests to run") || logLine.contains("T E S T S")) {
                        System.out.println("  " + logLine.trim());
                    }
                }

                Matcher summaryMatcher = TEST_SUMMARY_PATTERN.matcher(mvnLog);
                int run = 0, failures = 0, errors = 0, skipped = 0;
                while (summaryMatcher.find()) {
                    run = Integer.parseInt(summaryMatcher.group(1));
                    failures = Integer.parseInt(summaryMatcher.group(2));
                    errors = Integer.parseInt(summaryMatcher.group(3));
                    skipped = Integer.parseInt(summaryMatcher.group(4));
                }

                // If no tests were found, this might indicate a mapping issue
                if (run == 0) {
                    System.out.println("WARNING: No tests were executed for method " + batchKey);
                    System.out.println("This could indicate a mapping issue between method and test names.");
                    continue;
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

                // Collect failed tests
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

                // If we detected failures but didn't capture any failed tests, use fallback
                if ((failures > 0 || errors > 0) && failedThisBatch.isEmpty()) {
                    System.out.println("Warning: Detected " + failures + " failures and " + errors + " errors but couldn't parse test names. Using fallback strategy.");
                    // Add all tests from this batch as potentially failed
                    failedThisBatch.addAll(tests);
                }

                if (!failedThisBatch.isEmpty()) {
                    failedTestsReport.put(batchKey, failedThisBatch);
                    System.out.println("Captured " + failedThisBatch.size() + " failed tests for method: " + batchKey);
                }

            } finally {
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

    /**
     * Extracts the project name from the given project path.
     * @param projectPath the full path to the project
     * @return the project name (last directory in the path)
     */
    private String extractProjectName(String projectPath) {
        if (projectPath == null || projectPath.isEmpty()) {
            return "unknown-project";
        }

        // Handle both forward and backward slashes
        String normalizedPath = projectPath.replace('/', File.separatorChar);

        // Remove trailing separator if present
        if (normalizedPath.endsWith(File.separator)) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
        }

        int lastSeparatorIndex = normalizedPath.lastIndexOf(File.separatorChar);
        if (lastSeparatorIndex == -1) {
            return normalizedPath; // No separator found, return the whole path
        }

        return normalizedPath.substring(lastSeparatorIndex + 1);
    }

    /**
     * Resets the project to clean state using git checkout .
     * @param projectPath the path to the project directory
     * @throws Exception if git reset fails
     */
    private void resetProjectToCleanState(String projectPath) throws Exception {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "checkout", ".");
            pb.directory(new File(projectPath));
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Read output for debugging
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                System.err.println("Git checkout failed with exit code: " + exitCode);
                System.err.println("Output: " + output.toString());
                throw new RuntimeException("Failed to reset project to clean state");
            }

            // Optional: also clean any untracked files
            ProcessBuilder pbClean = new ProcessBuilder("git", "clean", "-fd");
            pbClean.directory(new File(projectPath));
            pbClean.redirectErrorStream(true);

            Process cleanProcess = pbClean.start();
            cleanProcess.waitFor(); // We don't check exit code for clean as it's optional

        } catch (IOException | InterruptedException e) {
            throw new Exception("Failed to reset project state: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // STEP -1: Setup PITest Plugin and Compile Project
    // =========================================================================
    private void setupPitestPlugin(String projectPath) throws Exception {
        System.out.println("Checking PITest plugin configuration...");

        // Check if the PITest plugin is already configured in the pom.xml
        File pomFile = new File(projectPath, "pom.xml");
        if (!pomFile.exists()) {
            throw new FileNotFoundException("pom.xml not found in the project directory: " + projectPath);
        }

        // Check for the presence of the PITest plugin in the pom.xml
        // Look for both groupId and artifactId separately since they're on different lines
        boolean hasOrgPitest = false;
        boolean hasPitestMaven = false;

        try (var lines = Files.lines(pomFile.toPath())) {
            for (String line : lines.collect(Collectors.toList())) {
                if (line.contains("org.pitest")) {
                    hasOrgPitest = true;
                }
                if (line.contains("pitest-maven")) {
                    hasPitestMaven = true;
                }
                // If we found both, no need to continue
                if (hasOrgPitest && hasPitestMaven) {
                    break;
                }
            }
        }

        boolean pitestConfigured = hasOrgPitest && hasPitestMaven;

        if (pitestConfigured) {
            System.out.println("✅ PITest plugin is already configured.");
            return;
        }

        System.out.println("⚠️ PITest plugin not found. Adding configuration to pom.xml...");

        // For this demo, we'll assume the plugin should already be configured manually
        // In a real scenario, you would programmatically add it to the pom.xml
        System.out.println("Please ensure PITest plugin is configured in your pom.xml");
    }

    // =========================================================================
    // STEP -2: Compile Maven Project
    // =========================================================================
    private void compileMavenProject(String projectPath) throws Exception {
        System.out.println("Compiling Maven project...");

        Invoker invoker = new DefaultInvoker();
        invoker.setWorkingDirectory(new File(projectPath));
        File mavenHome = new File(System.getenv("MAVEN_HOME"));
        if (!mavenHome.isDirectory()) {
            throw new IllegalStateException("MAVEN_HOME must point to a valid directory");
        }
        invoker.setMavenHome(mavenHome);

        InvocationRequest req = new DefaultInvocationRequest();
        req.setPomFile(new File(projectPath, "pom.xml"));
        req.setGoals(Arrays.asList("clean", "compile", "test-compile"));
        req.setInputStream(InputStream.nullInputStream());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStreamHandler handler = new PrintStreamHandler(new PrintStream(baos), false);
        invoker.setOutputHandler(handler);
        invoker.setErrorHandler(handler);

        InvocationResult result = invoker.execute(req);
        String mvnLog = baos.toString();

        if (result.getExitCode() != 0) {
            System.err.println("Maven compilation failed. Output:");
            System.err.println(mvnLog);
            throw new IllegalStateException("Maven compilation failed with exit code: " + result.getExitCode());
        }

        System.out.println("✅ Maven project compiled successfully.");
    }

    // =========================================================================
    // STEP -1a: Run PITest Mutation Coverage Analysis
    // =========================================================================
    private void runPitestMutationCoverage(String projectPath) throws Exception {
        System.out.println("Running PITest mutation coverage analysis...");

        // Check if PITest reports already exist
        File pitReportsDir = new File(projectPath, "target/pit-reports");
        File lineCoverageFile = new File(pitReportsDir, "linecoverage.xml");

        if (lineCoverageFile.exists() && lineCoverageFile.isFile()) {
            System.out.println("✅ PITest reports already exist, skipping PITest execution.");
            System.out.println("✅ Found existing linecoverage.xml at: " + lineCoverageFile.getAbsolutePath());
            return;
        }

        System.out.println("No existing PITest reports found. Running PITest...");

        Invoker invoker = new DefaultInvoker();
        invoker.setWorkingDirectory(new File(projectPath));
        File mavenHome = new File(System.getenv("MAVEN_HOME"));
        if (!mavenHome.isDirectory()) {
            throw new IllegalStateException("MAVEN_HOME must point to a valid directory");
        }
        invoker.setMavenHome(mavenHome);

        InvocationRequest req = new DefaultInvocationRequest();
        req.setPomFile(new File(projectPath, "pom.xml"));
        req.setGoals(Collections.singletonList("org.pitest:pitest-maven:mutationCoverage"));
        req.setInputStream(InputStream.nullInputStream());

        // Set properties to ensure PITest runs with proper configuration
        Properties props = new Properties();
        props.setProperty("maven.test.failure.ignore", "true");
        req.setProperties(props);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStreamHandler handler = new PrintStreamHandler(new PrintStream(baos), false);
        invoker.setOutputHandler(handler);
        invoker.setErrorHandler(handler);

        System.out.println("⏳ This may take several minutes depending on project size...");

        InvocationResult result = invoker.execute(req);
        String mvnLog = baos.toString();

        if (result.getExitCode() != 0) {
            System.err.println("PITest mutation coverage failed. Output:");
            System.err.println(mvnLog);
            throw new IllegalStateException("PITest execution failed with exit code: " + result.getExitCode());
        }

        System.out.println("✅ PITest mutation coverage analysis completed successfully.");

        // Verify that the PITest reports were generated
        if (pitReportsDir.exists() && pitReportsDir.isDirectory()) {
            System.out.println("✅ PITest reports generated in: " + pitReportsDir.getAbsolutePath());
        } else {
            System.out.println("⚠️ Warning: PITest reports directory not found. Check PITest configuration.");
        }
    }
}
