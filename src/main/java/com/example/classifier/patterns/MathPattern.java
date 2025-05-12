package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.operations.UpdateOperation;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.declaration.CtElement;

import java.util.*;

public class MathPattern implements ChangeClassifier.MutationPattern {

    private static final Map<BinaryOperatorKind, BinaryOperatorKind> mutationPairs = Map.ofEntries(
            Map.entry(BinaryOperatorKind.PLUS, BinaryOperatorKind.MINUS),
            Map.entry(BinaryOperatorKind.MINUS, BinaryOperatorKind.PLUS),
            Map.entry(BinaryOperatorKind.MUL, BinaryOperatorKind.DIV),
            Map.entry(BinaryOperatorKind.DIV, BinaryOperatorKind.MUL),
            Map.entry(BinaryOperatorKind.MOD, BinaryOperatorKind.MUL),
            Map.entry(BinaryOperatorKind.BITAND, BinaryOperatorKind.BITOR),
            Map.entry(BinaryOperatorKind.BITOR, BinaryOperatorKind.BITAND),
            Map.entry(BinaryOperatorKind.BITXOR, BinaryOperatorKind.BITAND),
            Map.entry(BinaryOperatorKind.SL, BinaryOperatorKind.SR),
            Map.entry(BinaryOperatorKind.SR, BinaryOperatorKind.SL),
            Map.entry(BinaryOperatorKind.USR, BinaryOperatorKind.SL)
    );


    @Override
    public boolean matches(List<Operation> ops) {
        for (Operation op : ops) {
            if (!(op instanceof UpdateOperation update)) continue;

            CtElement src = update.getSrcNode();
            CtElement dst = update.getDstNode();

            if (src instanceof CtBinaryOperator<?> oldOp && dst instanceof CtBinaryOperator<?> newOp) {
                BinaryOperatorKind oldKind = oldOp.getKind();
                BinaryOperatorKind newKind = newOp.getKind();

                if (mutationPairs.containsKey(oldKind) && mutationPairs.get(oldKind) == newKind) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public String description() {
        return "Mutation ‘MATH’ – replaces binary arithmetic or bitwise operations according to predefined rules (e.g. + → -)";
    }
}
