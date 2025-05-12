package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.MoveOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.BinaryOperatorKind;

import java.util.List;

public class AODPattern implements ChangeClassifier.MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        CtBinaryOperator<?> deletedBin = null;
        CtVariableRead<?>    movedVar   = null;

        for (Operation op : ops) {
            // 1) Detect deletion of a binary operator a + b
            if (op instanceof DeleteOperation del) {
                if (del.getNode() instanceof CtBinaryOperator<?> bin
                        && bin.getKind() == BinaryOperatorKind.PLUS) {
                    deletedBin = bin;
                }
            }
            // 2) Detect moving of one of the variable reads (a or b)
            else if (op instanceof MoveOperation mv) {
                if (mv.getNode() instanceof CtVariableRead<?> vr) {
                    movedVar = vr;
                }
            }
        }

        // If we didn't both delete a binary and move a variable, it's not this pattern
        if (deletedBin == null || movedVar == null) {
            return false;
        }

        // 3) Check that the moved variable was one of the operands of the deleted binary operator
        CtExpression<?> left  = deletedBin.getLeftHandOperand();
        CtExpression<?> right = deletedBin.getRightHandOperand();
        return movedVar.equals(left) || movedVar.equals(right);
    }

    @Override
    public String description() {
        return "Mutation \"replace binary with operand\" (replaced `a + b` with a single operand)";
    }
}
