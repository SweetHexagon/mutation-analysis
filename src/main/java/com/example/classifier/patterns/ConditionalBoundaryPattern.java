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
    public List<Operation> matchingOperations(List<Operation> ops) {
        List<Operation> matched = new ArrayList<>();
        for (Operation op : ops) {
            if (!(op instanceof UpdateOperation upd)) {
                continue;
            }
            CtElement src = upd.getSrcNode();
            CtElement dst = upd.getDstNode();
            if (src instanceof CtBinaryOperator<?> binSrc
                    && dst instanceof CtBinaryOperator<?> binDst) {
                BinaryOperatorKind from = binSrc.getKind();
                BinaryOperatorKind to   = binDst.getKind();
                if (CB_MAP.containsKey(from) && CB_MAP.get(from).contains(to)) {
                    matched.add(upd);
                }
            }
        }
        return matched;
    }

    @Override
    public String description() {
        return "Mutation ‘Conditional Boundary’ (swapped <↔<= or >↔>=)";
    }
}
