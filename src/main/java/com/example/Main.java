package com.example;

import com.example.mapper.ResultMapper;
import com.example.pojo.FileResult;
import com.example.pojo.RepoResult;
import com.example.util.GitUtils;
import com.example.util.JsonUtils;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.io.FileUtils;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

public class Main {
    static String localPath = "repositories";
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();
    //C files: static String repoUrl = "https://github.com/JakGad/synthetic_mutations";
    //static String repoUrl = "https://github.com/apache/commons-lang";

    static List<String> repoUrls = List.of(
            //"https://github.com/Snailclimb/JavaGuide",
            //"https://github.com/krahets/hello-algo",
            //"https://github.com/iluwatar/java-design-patterns",
            //"https://github.com/macrozheng/mall",
            //"https://github.com/doocs/advanced-java",
            //"https://github.com/spring-projects/spring-boot",
            //"https://github.com/MisterBooo/LeetCodeAnimation",
            "https://github.com/elastic/elasticsearch",
            "https://github.com/kdn251/interviews",
            "https://github.com/TheAlgorithms/Java",
            "https://github.com/spring-projects/spring-framework",
            "https://github.com/NationalSecurityAgency/ghidra",
            "https://github.com/Stirling-Tools/Stirling-PDF",
            "https://github.com/google/guava",
            "https://github.com/ReactiveX/RxJava",
            "https://github.com/skylot/jadx",
            "https://github.com/dbeaver/dbeaver",
            "https://github.com/jeecgboot/JeecgBoot",
            "https://github.com/apache/dubbo",
            "https://github.com/termux/termux-app"
    );


    static List<String> extensions = List.of(
            //".c",
            ".java");

    public static void main(String[] args) throws InterruptedException {
        //cleanUp(localPath);

        test();

        //presentation(repoUrls);

    }

    public static void presentation(List<String> repoUrls) throws InterruptedException {
        cleanUp(localPath);

        for (String repoUrl : repoUrls) {
            String repoName = repoUrl.substring(repoUrl.lastIndexOf("/") + 1);
            String repoDir  = localPath + File.separator + repoName;

            List<CommitPairWithFiles> commitPairs;

            try {
                commitPairs = GitUtils.processRepo(repoUrl, repoDir, extensions, false);
            } catch (Exception e) {
                System.err.println("Error processing " + repoUrl + ": " + e.getMessage());
                continue;
            }

            if (commitPairs == null || commitPairs.isEmpty()) {
                System.out.println("No commits for " + repoUrl);
                continue;
            }

            // thread-safe collector
            List<FileResult> potentialMutants = Collections.synchronizedList(new ArrayList<>());

            ExecutorService exec = Executors.newFixedThreadPool(THREAD_COUNT);
            CompletionService<Void> cs = new ExecutorCompletionService<>(exec);

            final int total = commitPairs.size();
            System.out.println("Commits to process: " + total);
            for (int i = 0; i < total; i++) {
                final int idx = i;
                final CommitPairWithFiles pair = commitPairs.get(i);
                cs.submit(() -> {
                    // each task gets its own parser instances (thanks to the factory change)
                    for (String file : pair.changedFiles()) {
                        FileResult result = TreeComparator.compareFileInTwoCommits(
                                repoDir,
                                pair.oldCommit(),
                                pair.newCommit(),
                                file,
                                false
                        );
                        if (result != null) {
                            int ted = result.getMetrics().get(Metrics.TREE_EDIT_DISTANCE);
                            if (ted > 0 && ted < 15) {
                                potentialMutants.add(result);
                            }
                        }
                    }

                    // update the progress bar
                    printProgressBar(idx + 1, total);
                    return null;
                });
            }

            // wait for them all
            for (int i = 0; i < total; i++) {
                try {
                    cs.take().get();
                } catch (ExecutionException ee) {
                    System.err.println("Task failed: " + ee.getCause());
                }
            }
            exec.shutdown();
            System.out.println();  // newline after the bar

            // write out JSON
            RepoResult rr = new RepoResult(repoUrl, potentialMutants);
            String out = JsonUtils.generateComparisonFileName(repoUrl);
            JsonUtils.writeJsonToFile(
                    ResultMapper.toDto(rr),
                    "src/main/resources/programOutput/" + out
            );
        }
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




    public static void test() {
        String oldSha = "62d7aacb4f3eb318045496f115a7171f0bc2c6c1";
        String newSha = "6da891529bb17ece3bb572d5ab6fef5a233c1b5c";
        String relativePath = "src/main/java/org/apache/commons/lang3/builder/HashCodeBuilder.java";
        String outputDir = "D:\\Java projects\\mutation-analysis\\src\\main\\resources\\extracted_files";

        cleanUp(outputDir);

        //List<String> extractedPaths = GitUtils.extractFileAtTwoCommits(localPath, relativePath, oldSha, newSha, outputDir);
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

    public static void printFileContents(String label, String filePath) {
        System.out.println("=== " + label + " ===");
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            lines.forEach(System.out::println);
        } catch (IOException e) {
            System.out.println("Error reading " + label + ": " + e.getMessage());
        }
    }

    public static void printTokens(ParseTree tree) {
        printTokensHelper(tree);
        System.out.println();
    }

    public static void printTokensHelper(ParseTree tree) {
        if (tree instanceof TerminalNode) {
            System.out.print(tree.getText() + " ");
        } else {
            for (int i = 0; i < tree.getChildCount(); i++) {
                printTokensHelper(tree.getChild(i));
            }
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

    public static void extractSpecificFiles() {
        String oldSha = "62d7aacb4f3eb318045496f115a7171f0bc2c6c1";
        String newSha = "6da891529bb17ece3bb572d5ab6fef5a233c1b5c";
        String relativePath = "src/main/java/org/apache/commons/lang3/builder/HashCodeBuilder.java";
        String outputDir = "C:\\Users\\aless\\Documents\\extracted_files";

        List<String> extractedPaths = GitUtils.extractFileAtTwoCommits(localPath, relativePath, oldSha, newSha, outputDir);

        if (extractedPaths.size() == 2) {
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

}