package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.InsertOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtLiteral;

import java.util.ArrayList;
import java.util.List;

public class RemoveConditionalsPattern implements ChangeClassifier.MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        List<Operation> matched = new ArrayList<>();
        DeleteOperation delOp = null;
        InsertOperation insOp = null;

        for (Operation op : ops) {
            if (delOp == null && op instanceof DeleteOperation del
                    && del.getNode() instanceof CtBinaryOperator<?>) {
                delOp = del;
                matched.add(delOp);
            }
            if (insOp == null && op instanceof InsertOperation ins
                    && ins.getNode() instanceof CtLiteral<?> lit
                    && lit.getValue() instanceof Boolean) {
                insOp = ins;
                matched.add(insOp);
            }
            if (delOp != null && insOp != null) {
                break;
            }
        }

        if (delOp != null && insOp != null) {
            return matched;
        }
        return List.of();
    }

    @Override
    public String description() {
        return "Mutation ‘REMOVE_CONDITIONALS’ – replaces conditional (e.g., a == b) with boolean literal (true/false)";
    }
}
