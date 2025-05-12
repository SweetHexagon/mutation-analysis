package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.operations.UpdateOperation;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.declaration.CtElement;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class AORPattern implements ChangeClassifier.MutationPattern {

    /**
     * For each arithmetic operator, the set of allowed replacements.
     * Applies only to: PLUS, MINUS, MUL, DIV, MOD
     */
    private static final Map<BinaryOperatorKind, Set<BinaryOperatorKind>> AOR_MAP = Map.of(
            BinaryOperatorKind.PLUS, Set.of(BinaryOperatorKind.MINUS,
                    BinaryOperatorKind.MUL,
                    BinaryOperatorKind.DIV,
                    BinaryOperatorKind.MOD),
            BinaryOperatorKind.MINUS, Set.of(BinaryOperatorKind.PLUS,
                    BinaryOperatorKind.MUL,
                    BinaryOperatorKind.DIV,
                    BinaryOperatorKind.MOD),
            BinaryOperatorKind.MUL, Set.of(BinaryOperatorKind.PLUS,
                    BinaryOperatorKind.MINUS,
                    BinaryOperatorKind.DIV,
                    BinaryOperatorKind.MOD),
            BinaryOperatorKind.DIV, Set.of(BinaryOperatorKind.PLUS,
                    BinaryOperatorKind.MINUS,
                    BinaryOperatorKind.MUL,
                    BinaryOperatorKind.MOD),
            BinaryOperatorKind.MOD, Set.of(BinaryOperatorKind.PLUS,
                    BinaryOperatorKind.MINUS,
                    BinaryOperatorKind.MUL,
                    BinaryOperatorKind.DIV)
    );

    @Override
    public boolean matches(List<Operation> ops) {
        for (Operation op : ops) {
            if (op instanceof UpdateOperation upd) {
                CtElement src = upd.getSrcNode();
                CtElement dst = upd.getDstNode();

                if (src instanceof CtBinaryOperator<?> binSrc
                        && dst instanceof CtBinaryOperator<?> binDst) {

                    BinaryOperatorKind from = binSrc.getKind();
                    BinaryOperatorKind to = binDst.getKind();

                    if (AOR_MAP.containsKey(from) && AOR_MAP.get(from).contains(to)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public String description() {
        return "Mutation ‘AOR’ (arithmetic operator replaced according to AOR rules)";
    }
}
