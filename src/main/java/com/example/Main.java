package com.example;

import com.example.dto.FileResult;
import com.example.util.GitUtils;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.io.FileUtils;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class Main {
    static String localPath = "repo";
    //static String repoUrl = "https://github.com/JakGad/synthetic_mutations";
    static String repoUrl = "https://github.com/bartobri/no-more-secrets";
    static List<String> extensions = List.of(".c");

    public static void main(String[] args) {

        test();

        //presentation();

    }

    public static void presentation() {

        cleanUp();

        List<CommitPairWithFiles> commitPairs = GitUtils.processRepo(repoUrl, localPath, extensions);

        if (commitPairs.isEmpty()) {
            System.out.println("No commit pairs with small changes found.");
            return;
        }

        for (CommitPairWithFiles pair : commitPairs) {
            System.out.println("--------------------------------------------------");
            System.out.println("Comparing commits:");
            System.out.println("Old Commit: " + pair.oldCommit().getName());
            System.out.println("New Commit: " + pair.newCommit().getName());

            for (String file : pair.changedFiles()) {

                System.out.println("\nFile: " + file);
                var result = TreeComparator.compareFileInTwoCommits(
                        localPath,
                        pair.oldCommit(),
                        pair.newCommit(),
                        file,
                        false
                );


                if ( result != null && result.getMetrics().get(Metrics.TED) > 0){
                    System.out.println(result);
                } else {
                  System.out.println("Tree Edit distance: 0");
                }

            }

            System.out.println("--------------------------------------------------\n");
        }
    }

    public static void test() {
//File path: C:\Users\aless\AppData\Local\Temp\commit_b771aa9663511fd60d4a01572a0c579e2edbbddc8000983893690568961_src_sneakers.c -> C:\Users\aless\AppData\Local\Temp\commit_b34fc9bf1155a22e881a1b0c806ed349e17ae57f2132728151862476374_src_sneakers.c

        String oldFilePath = "C:\\Users\\aless\\AppData\\Local\\Temp\\commit_ed5e9f689fa28c6fe88be1f3076e6d9804ab3a013721886724850845387_src_nms.c";
        String newFilePath = "C:\\Users\\aless\\AppData\\Local\\Temp\\commit_2eaafe1ef3dcf9db93796c62cbdca6ad184de26b6615462805272876347_src_nms.c";
        printFileContents("Old File", oldFilePath);
        printFileContents("New File", newFilePath);
        //String oldFilePath = "D:\\Java projects\\mutation-analysis\\src\\main\\java\\com\\example\\test\\file1.c";
        //String newFilePath = "D:\\Java projects\\mutation-analysis\\src\\main\\java\\com\\example\\test\\file2.c";

        FileResult result = TreeComparator.compareTwoFilePaths(oldFilePath, newFilePath, true);

        if (result != null) {
            System.out.println(result.toString());
        } else {
            System.out.println("Comparison failed or files could not be parsed.");
        }
    }

    private static void printFileContents(String label, String filePath) {
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
            System.out.println("Error during cleanup");
        }
    }
}