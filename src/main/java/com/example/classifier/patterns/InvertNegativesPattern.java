package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.*;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.declaration.CtElement;

import java.util.List;

public class InvertNegativesPattern implements ChangeClassifier.MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        CtUnaryOperator<?> deletedUnary = null;
        CtVariableRead<?>  movedVar = null;

        for (Operation op : ops) {
            if (op instanceof DeleteOperation del) {
                CtElement node = del.getNode();
                if (node instanceof CtUnaryOperator<?> unary
                        && unary.getKind() == UnaryOperatorKind.NEG
                        && unary.getOperand() instanceof CtVariableRead) {
                    deletedUnary = unary;
                }
            } else if (op instanceof MoveOperation mov) {
                CtElement node = mov.getNode();
                if (node instanceof CtVariableRead<?> vr) {
                    movedVar = vr;
                }
            }
        }

        if (deletedUnary == null || movedVar == null) {
            return false;
        }

        CtExpression<?> operand = deletedUnary.getOperand();
        return operand.equals(movedVar);
    }

    @Override
    public String description() {
        return "Mutation ‘INVERT_NEGS’ – removes unary negation from a variable (e.g., -x → x), with verified operand match";
    }
}
