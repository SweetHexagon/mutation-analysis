package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.*;
import gumtree.spoon.diff.operations.InsertOperation;
import gumtree.spoon.diff.operations.MoveOperation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.declaration.CtElement;

import java.util.List;

public class ABSPattern implements ChangeClassifier.MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        CtUnaryOperator<?> insertedUo = null;
        CtVariableRead<?>    movedVar   = null;

        for (Operation op : ops) {
            if (op instanceof InsertOperation ins) {
                CtElement node = ins.getNode();
                if (node instanceof CtUnaryOperator<?> uo
                        && uo.getKind() == UnaryOperatorKind.NEG) {
                    insertedUo = uo;
                }
            }
            else if (op instanceof MoveOperation mv) {
                CtElement node = mv.getNode();
                if (node instanceof CtVariableRead<?> vr) {
                    movedVar = vr;
                }
            }
        }

        if (insertedUo == null || movedVar == null) {
            return false;
        }

        CtExpression<?> operand = insertedUo.getOperand();
        return operand.equals(movedVar) ;
    }

    @Override
    public String description() {
        return "Mutation \"negate value\" (unary minus inserted)";
    }
}

