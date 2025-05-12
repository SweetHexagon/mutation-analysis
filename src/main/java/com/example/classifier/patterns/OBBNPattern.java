package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.*;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtElement;

import java.util.List;

public class OBBNPattern implements ChangeClassifier.MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        CtBinaryOperator<?> deletedBinOp = null;
        CtExpression<?> insertedExpr = null;

        for (Operation op : ops) {
            // Case A: Flip bitwise operator (& ↔ |)
            if (op instanceof UpdateOperation update) {
                CtElement src = update.getSrcNode();
                CtElement dst = update.getDstNode();
                if (src instanceof CtBinaryOperator<?> oldOp && dst instanceof CtBinaryOperator<?> newOp) {
                    BinaryOperatorKind oldKind = oldOp.getKind();
                    BinaryOperatorKind newKind = newOp.getKind();
                    if (isBitwiseFlip(oldKind, newKind)) return true; // OBBN1
                }
            }

            // Case B/C: Replace with one operand (OBBN2/OBBN3)
            if (op instanceof DeleteOperation del && del.getNode() instanceof CtBinaryOperator<?> binOp) {
                deletedBinOp = binOp;
            }

            if ((op instanceof InsertOperation || op instanceof MoveOperation) && op.getNode() instanceof CtExpression<?> expr) {
                insertedExpr = (CtExpression<?>) op.getNode();
            }
        }

        if (deletedBinOp != null && insertedExpr != null) {
            CtExpression<?> left = deletedBinOp.getLeftHandOperand();
            CtExpression<?> right = deletedBinOp.getRightHandOperand();

            // Match OBBN2 or OBBN3
            return insertedExpr.equals(left) || insertedExpr.equals(right);
        }

        return false;
    }

    private boolean isBitwiseFlip(BinaryOperatorKind oldKind, BinaryOperatorKind newKind) {
        return (oldKind == BinaryOperatorKind.BITAND && newKind == BinaryOperatorKind.BITOR) ||
                (oldKind == BinaryOperatorKind.BITOR && newKind == BinaryOperatorKind.BITAND);
    }

    @Override
    public String description() {
        return "Mutation ‘OBBN’ – replaces bitwise operator (&, |) with flipped operator or single operand";
    }
}
