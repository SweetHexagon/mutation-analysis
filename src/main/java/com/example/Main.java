package com.example;

import com.example.mapper.ResultMapper;
import com.example.pojo.FileResult;
import com.example.pojo.RepoResult;
import com.example.util.GitUtils;
import com.example.util.JsonUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.io.FileUtils;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    static String localPath = "repo";
    //static String repoUrl = "https://github.com/JakGad/synthetic_mutations";
    //static String repoUrl = "https://github.com/bartobri/no-more-secrets";
    //static String repoUrl = "https://github.com/cfenollosa/os-tutorial";
    static String repoUrl = "https://github.com/apache/commons-lang";

    static List<String> extensions = List.of(".c", ".java");

    public static void main(String[] args) {
        //cleanUp();

        test();

        //presentation();

    }

    public static void presentation() {

        boolean debug = false;

        cleanUp();

        List<CommitPairWithFiles> commitPairs = GitUtils.processRepo(repoUrl, localPath, extensions, false);

        if (commitPairs.isEmpty()) {
            System.out.println("No commit pairs with small changes found or lack of files with appropriate extensions.");
            return;
        }

        List<FileResult> potentialMutants = new ArrayList<>();

        int totalPairs = commitPairs.size();
        int currentPair = 1;

        for (CommitPairWithFiles pair : commitPairs) {
            System.out.println("analyzing " + currentPair + "/" + totalPairs + " commit pair");

            for (String file : pair.changedFiles()) {

                var result = TreeComparator.compareFileInTwoCommits(
                        localPath,
                        pair.oldCommit(),
                        pair.newCommit(),
                        file,
                        false
                );

                if (
                        result != null &&
                                result.getMetrics().get(Metrics.TED) > 0 &&
                                result.getMetrics().get(Metrics.TED) < 15
                ) {
                    potentialMutants.add(result);
                } else {
                    if (debug) {
                        if (result != null) {
                            System.out.println("Tree Edit distance: " + result.getMetrics().get(Metrics.TED));
                        } else {
                            System.out.println("Cant retrieve TED");
                        }
                    }

                }

            }
            currentPair++;
        }
        RepoResult repoResult = new RepoResult(repoUrl, potentialMutants);

        JsonUtils.writeJsonToFile(
                ResultMapper.toDto(repoResult),
                "src/main/resources/programOutput/repoResult.json"
        );


        System.out.println(repoResult);

    }



    public static void test() {
        String oldSha = "62d7aacb4f3eb318045496f115a7171f0bc2c6c1";
        String newSha = "6da891529bb17ece3bb572d5ab6fef5a233c1b5c";
        String relativePath = "src/main/java/org/apache/commons/lang3/builder/HashCodeBuilder.java";
        String outputDir = "D:\\Java projects\\mutation-analysis\\src\\main\\resources\\extracted_files";


        List<String> extractedPaths = GitUtils.extractFileAtTwoCommits(localPath, relativePath, oldSha, newSha, outputDir);
        //List<String> extractedPaths = List.of("D:\\\\Java projects\\\\mutation-analysis\\\\src\\\\main\\\\java\\\\com\\\\example\\\\test\\\\file1.java", "D:\\\\Java projects\\\\mutation-analysis\\\\src\\\\main\\\\java\\\\com\\\\example\\\\test\\\\file2.java");
        if (extractedPaths.size() == 2) {
            String oldFilePath = extractedPaths.get(0);
            String newFilePath = extractedPaths.get(1);

            printFileContents("Old File", oldFilePath);
            printFileContents("New File", newFilePath);

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

    public static void cleanUp() {
        try {
            File dir = new File(localPath);
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