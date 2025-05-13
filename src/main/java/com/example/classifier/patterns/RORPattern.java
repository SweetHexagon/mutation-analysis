package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.operations.UpdateOperation;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.declaration.CtElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class RORPattern implements ChangeClassifier.MutationPattern {

    // Valid relational operator kinds involved in ROR mutations
    private static final Set<BinaryOperatorKind> ROR_OPERATORS = Set.of(
            BinaryOperatorKind.LT,    // <
            BinaryOperatorKind.LE,    // <=
            BinaryOperatorKind.GT,    // >
            BinaryOperatorKind.GE,    // >=
            BinaryOperatorKind.EQ,    // ==
            BinaryOperatorKind.NE     // !=
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
            if (src instanceof CtBinaryOperator<?> oldOp
                    && dst instanceof CtBinaryOperator<?> newOp) {
                BinaryOperatorKind oldKind = oldOp.getKind();
                BinaryOperatorKind newKind = newOp.getKind();
                if (ROR_OPERATORS.contains(oldKind)
                        && ROR_OPERATORS.contains(newKind)
                        && !oldKind.equals(newKind)) {
                    matched.add(upd);
                }
            }
        }
        return matched;
    }

    @Override
    public String description() {
        return "Mutation ‘ROR’ – replaces relational operators (e.g., < with <=, == with !=)";
    }
}
