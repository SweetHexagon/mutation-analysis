package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.operations.UpdateOperation;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.declaration.CtElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NegateConditionalsPattern implements ChangeClassifier.MutationPattern {

    private static final Map<BinaryOperatorKind, BinaryOperatorKind> conditionalNegations = Map.ofEntries(
            Map.entry(BinaryOperatorKind.EQ, BinaryOperatorKind.NE),
            Map.entry(BinaryOperatorKind.NE, BinaryOperatorKind.EQ),
            Map.entry(BinaryOperatorKind.LE, BinaryOperatorKind.GT),
            Map.entry(BinaryOperatorKind.GE, BinaryOperatorKind.LT),
            Map.entry(BinaryOperatorKind.LT, BinaryOperatorKind.GE),
            Map.entry(BinaryOperatorKind.GT, BinaryOperatorKind.LE)
    );

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        List<Operation> matched = new ArrayList<>();
        for (Operation op : ops) {
            if (op instanceof UpdateOperation update) {
                CtElement before = update.getSrcNode();
                CtElement after  = update.getDstNode();
                if (before instanceof CtBinaryOperator<?> oldOp
                        && after  instanceof CtBinaryOperator<?> newOp) {
                    BinaryOperatorKind oldKind = oldOp.getKind();
                    BinaryOperatorKind newKind = newOp.getKind();
                    if (conditionalNegations.containsKey(oldKind)
                            && conditionalNegations.get(oldKind) == newKind) {
                        matched.add(update);
                    }
                }
            }
        }
        return matched;
    }

    @Override
    public String description() {
        return "Mutation ‘NEGATE_CONDITIONALS’ – negates comparison operators (== ↔ !=, < ↔ >=, etc.)";
    }
}
