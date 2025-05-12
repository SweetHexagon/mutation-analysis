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

public class ConditionalBoundaryPattern implements ChangeClassifier.MutationPattern {

    /**
     * Defines valid boundary‐operator swaps:
     *   LT   ( "<" )  ↔ LE   ("<=")
     *   GT   ( ">" )  ↔ GE   (">=")
     */
    private static final Map<BinaryOperatorKind, Set<BinaryOperatorKind>> CB_MAP = Map.of(
            BinaryOperatorKind.LT, Set.of(BinaryOperatorKind.LE),
            BinaryOperatorKind.LE, Set.of(BinaryOperatorKind.LT),
            BinaryOperatorKind.GT, Set.of(BinaryOperatorKind.GE),
            BinaryOperatorKind.GE, Set.of(BinaryOperatorKind.GT)
    );

    @Override
    public boolean matches(List<Operation> ops) {
        for (Operation op : ops) {
            if (!(op instanceof UpdateOperation upd)) {
                continue;
            }
            CtElement src = upd.getSrcNode();
            CtElement dst = upd.getDstNode();

            // Look only at binary comparisons
            if (src instanceof CtBinaryOperator<?> binSrc
                    && dst instanceof CtBinaryOperator<?> binDst) {

                BinaryOperatorKind from = binSrc.getKind();
                BinaryOperatorKind to   = binDst.getKind();

                // If we have a mapping from `from` → `to`, it's a boundary mutation
                if (CB_MAP.containsKey(from) && CB_MAP.get(from).contains(to)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String description() {
        return "Mutation ‘Conditional Boundary’ (swapped <↔<= or >↔>=)";
    }
}
