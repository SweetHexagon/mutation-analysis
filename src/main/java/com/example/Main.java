package com.example;

import org.antlr.v4.runtime.tree.TerminalNode;
import org.apache.commons.io.FileUtils;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class Main {
    static String localPath = "repo";
    static String repoUrl = "https://github.com/JakGad/synthetic_mutations";

    public static void main(String[] args) {
        //test();
        presentation();

    }

    public static void presentation() {
        try {
            cleanUp();
        } catch (IOException e) {
            System.out.println("Error during cleanup");
        }

        List<CommitPairWithFiles> commitPairs = GitUtils.processRepo(repoUrl, localPath);

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
                var result = Comparator.compareFileInTwoCommits(
                        localPath,
                        pair.oldCommit(),
                        pair.newCommit(),
                        file,
                        true
                );
                System.out.println(result);
            }

            System.out.println("--------------------------------------------------\n");
        }
    }

    public static void test() {
        ParseTree oldTree = ASTParser.parseCFile("D:\\Java projects\\mutation-analysis\\src\\main\\java\\com\\example\\test\\file1.c");
        ParseTree newTree = ASTParser.parseCFile("D:\\Java projects\\mutation-analysis\\src\\main\\java\\com\\example\\test\\file2.c");
        TreeEditDistance treeEdit = new TreeEditDistance();

        System.out.println(oldTree.toStringTree());
        System.out.println(oldTree.getChild(0).getText());
        System.out.println(newTree.getChild(0).getText());

        TreeNode convertedOldTree = TreeUtils.convert(oldTree);
        TreeNode convertedNewTree = TreeUtils.convert(newTree);

        System.out.println("converted Old Tree: " + convertedOldTree);
        System.out.println("Converted New Tree: " + convertedNewTree);

        System.out.println("distance: " + treeEdit.compare(
                convertedOldTree,
                convertedNewTree
        ));
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

    public static void cleanUp() throws IOException {
        File dir = new File(localPath);
        if (dir.exists()) {
            FileUtils.deleteDirectory(dir);
        }
    }
}