package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.InsertOperation;
import gumtree.spoon.diff.operations.MoveOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.declaration.CtElement;

import java.util.ArrayList;
import java.util.List;

public class ABSPattern implements ChangeClassifier.MutationPattern {
    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        CtUnaryOperator<?> insertedUo = null;
        CtVariableRead<?> movedVar   = null;
        List<Operation> matched      = new ArrayList<>();

        for (Operation op : ops) {
            if (op instanceof InsertOperation ins
                    && ins.getNode() instanceof CtUnaryOperator<?> uo
                    && uo.getKind() == UnaryOperatorKind.NEG) {
                insertedUo = uo;
                matched.add(op);
            }
            else if (op instanceof MoveOperation mv
                    && mv.getNode() instanceof CtVariableRead<?>) {
                movedVar = (CtVariableRead<?>) mv.getNode();
                matched.add(op);
            }
        }

        if (insertedUo != null
                && movedVar != null
                && insertedUo.getOperand().equals(movedVar)) {
            return matched;
        }
        return List.of();
    }

    @Override
    public String description() {
        return "Mutation \"negate value\" (unary minus inserted)";
    }
}

