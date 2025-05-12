

package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.operations.UpdateOperation;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.declaration.CtElement;

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
    public boolean matches(List<Operation> ops) {
        for (Operation op : ops) {
            if (!(op instanceof UpdateOperation update)) continue;

            CtElement src = update.getSrcNode();
            CtElement dst = update.getDstNode();

            if (src instanceof CtBinaryOperator<?> oldOp && dst instanceof CtBinaryOperator<?> newOp) {
                BinaryOperatorKind oldKind = oldOp.getKind();
                BinaryOperatorKind newKind = newOp.getKind();

                // Check that both old and new operators are ROR types and the operator changed
                if (ROR_OPERATORS.contains(oldKind) &&
                        ROR_OPERATORS.contains(newKind) &&
                        !oldKind.equals(newKind)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String description() {
        return "Mutation ‘ROR’ – replaces relational operators (e.g., < with <=, == with !=)";
    }
}

