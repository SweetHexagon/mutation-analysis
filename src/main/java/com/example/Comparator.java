package com.example;

import com.github.gumtreediff.actions.EditScript;
import com.github.gumtreediff.actions.EditScriptGenerator;
import com.github.gumtreediff.actions.SimplifiedChawatheScriptGenerator;
import com.github.gumtreediff.actions.model.Action;
import com.github.gumtreediff.client.Run;
import com.github.gumtreediff.gen.TreeGenerators;
import com.github.gumtreediff.matchers.MappingStore;
import com.github.gumtreediff.matchers.Matcher;
import com.github.gumtreediff.matchers.Matchers;
import com.github.gumtreediff.tree.Tree;
import java.io.IOException;

import org.antlr.v4.runtime.tree.ParseTree;
import com.example.dto.FileResult;
import org.eclipse.jgit.revwalk.RevCommit;
import java.util.HashMap;

public class Comparator {

    public static FileResult compareFileInTwoCommits(String localPath, RevCommit oldCommit, RevCommit newCommit, String fileName) {
        return compareFileInTwoCommits(localPath, oldCommit, newCommit, fileName,false);
    }

    public static FileResult compareFileInTwoCommits(String localPath, RevCommit oldCommit, RevCommit newCommit, String fileName, boolean debug) {
        TreeEditDistance treeEdit = new TreeEditDistance();
        HashMap<Metrics, Integer> metrics = new HashMap<>();

        String oldPath = GitUtils.extractFileAtCommit(localPath, oldCommit, fileName);
        String newPath = GitUtils.extractFileAtCommit(localPath, newCommit, fileName);

        if (oldPath == null || newPath == null) {
            System.out.println("Couldn't extract both versions for: " + fileName);
            return null;
        }

        ParseTree oldTree = ASTParser.parseCFile(oldPath);
        ParseTree newTree = ASTParser.parseCFile(newPath);

        TreeNode convertedOldTree = TreeUtils.convert(oldTree);
        TreeNode convertedNewTree = TreeUtils.convert(newTree);


        TreeEditDistance.TEDResult tedResult = treeEdit.compare(convertedOldTree, convertedNewTree);

        EditOperation highestEditOperation = getHighestEditOperation(tedResult);

        metrics.put(Metrics.TED, tedResult.cost);

        /*try {
            Run.initGenerators();
            Tree src = TreeGenerators.getInstance().getTree(oldPath).getRoot(); // retrieves and applies the default parser for the file
            Tree dst = TreeGenerators.getInstance().getTree(newPath).getRoot(); // retrieves and applies the default parser for the file
            Matcher defaultMatcher = Matchers.getInstance().getMatcher(); // retrieves the default matcher
            MappingStore mappings = defaultMatcher.match(src, dst); // computes the mappings between the trees
            EditScriptGenerator editScriptGenerator = new SimplifiedChawatheScriptGenerator(); // instantiates the simplified Chawathe script generator
            EditScript actions = editScriptGenerator.computeActions(mappings); // computes the edit script

            System.out.println("GumTreeDiff edit script for " + fileName + ":");
            for (Action action : actions) {
                Tree node = action.getNode();
                System.out.println(node.getParent().toTreeString());
                System.out.printf(
                        "[%s] %s -> '%s'\n",
                        action.getClass().getSimpleName(),
                        node.getType().name,
                        node.toString()
                );
                System.out.println();


            }
            System.out.println();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }*/

        FileResult fileResult = FileResult.builder()
                .name(fileName)
                .changedTreeFragment(highestEditOperation.toNode.parseTreeOriginalNode.getParent().getText())
                .originalTreeFragment(highestEditOperation.fromNode.parseTreeOriginalNode.getParent().getText())
                .editOperations(tedResult.operations)
                .metrics(metrics)
                .build();

        if (debug){
            System.out.println("converted Old Tree: " + convertedOldTree);
            System.out.println("Converted New Tree: " + convertedNewTree);
            System.out.println("Operations: " + tedResult.operations);
            System.out.println("Highest node: " + highestEditOperation);
        }

        return fileResult;
    }

    static EditOperation getHighestEditOperation(TreeEditDistance.TEDResult tedResult) {
        EditOperation highestEditOperation = tedResult.operations.getLast();

        for (var operation : tedResult.operations) {
            if (operation.toNode.depth < highestEditOperation.toNode.depth) {
                highestEditOperation = operation;
            }
        }

        return highestEditOperation;
    }

}
