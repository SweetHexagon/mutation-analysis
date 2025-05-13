package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.InsertOperation;
import gumtree.spoon.diff.operations.MoveOperation;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.operations.UpdateOperation;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtElement;

import java.util.ArrayList;
import java.util.List;

public class OBBNPattern implements ChangeClassifier.MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        List<Operation> matched = new ArrayList<>();

        // Case A: bitwise flip (& ↔ |)
        for (Operation op : ops) {
            if (op instanceof UpdateOperation upd) {
                CtElement src = upd.getSrcNode();
                CtElement dst = upd.getDstNode();
                if (src instanceof CtBinaryOperator<?> oldOp
                        && dst instanceof CtBinaryOperator<?> newOp
                        && isBitwiseFlip(oldOp.getKind(), newOp.getKind())) {
                    matched.add(upd);
                    return matched;
                }
            }
        }

        // Case B/C: replace with one operand
        CtBinaryOperator<?> deletedBinOp = null;
        DeleteOperation delOp = null;
        for (Operation op : ops) {
            if (op instanceof DeleteOperation del
                    && del.getNode() instanceof CtBinaryOperator<?> binOp) {
                deletedBinOp = binOp;
                delOp = del;
                matched.add(delOp);
                break;
            }
        }
        if (deletedBinOp == null) {
            return List.of();
        }

        for (Operation op : ops) {
            if ((op instanceof InsertOperation || op instanceof MoveOperation)
                    && op.getNode() instanceof CtExpression<?> expr) {
                CtExpression<?> left  = deletedBinOp.getLeftHandOperand();
                CtExpression<?> right = deletedBinOp.getRightHandOperand();
                if (expr.equals(left) || expr.equals(right)) {
                    matched.add(op);
                    return matched;
                }
            }
        }

        return List.of();
    }

    private boolean isBitwiseFlip(BinaryOperatorKind oldKind, BinaryOperatorKind newKind) {
        return (oldKind == BinaryOperatorKind.BITAND && newKind == BinaryOperatorKind.BITOR)
                || (oldKind == BinaryOperatorKind.BITOR  && newKind == BinaryOperatorKind.BITAND);
    }

    @Override
    public String description() {
        return "Mutation ‘OBBN’ – replaces bitwise operator (&, |) with flipped operator or single operand";
    }
}
