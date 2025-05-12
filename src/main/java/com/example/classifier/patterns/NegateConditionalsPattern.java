package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.operations.UpdateOperation;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.declaration.CtElement;

import java.util.*;

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
    public boolean matches(List<Operation> ops) {
        for (Operation op : ops) {
            if (!(op instanceof UpdateOperation update)) continue;

            CtElement before = update.getSrcNode();
            CtElement after = update.getDstNode();

            if (before instanceof CtBinaryOperator<?> oldOp && after instanceof CtBinaryOperator<?> newOp) {
                BinaryOperatorKind oldKind = oldOp.getKind();
                BinaryOperatorKind newKind = newOp.getKind();

                if (conditionalNegations.containsKey(oldKind)
                        && conditionalNegations.get(oldKind) == newKind) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public String description() {
        return "Mutation ‘NEGATE_CONDITIONALS’ – negates comparison operators (== ↔ !=, < ↔ >=, etc.)";
    }
}
