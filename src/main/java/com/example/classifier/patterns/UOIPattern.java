package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.*;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.declaration.CtElement;

import java.util.ArrayList;
import java.util.List;

public class UOIPattern implements ChangeClassifier.MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        CtVariableRead<?> deletedVar     = null;
        CtUnaryOperator<?> insertedUnary = null;
        DeleteOperation delOp            = null;
        InsertOperation insOp            = null;

        for (Operation op : ops) {
            if (deletedVar == null && op instanceof DeleteOperation del
                    && del.getNode() instanceof CtVariableRead<?> vr) {
                deletedVar = vr;
                delOp = del;
            }
            else if (insertedUnary == null && op instanceof InsertOperation ins
                    && ins.getNode() instanceof CtUnaryOperator<?> unary
                    && isUoiKind(unary.getKind())) {
                insertedUnary = unary;
                insOp = ins;
            }
            if (deletedVar != null && insertedUnary != null) {
                break;
            }
        }

        // Confirm we found both parts and that the unary wraps the deleted variable
        if (deletedVar != null
                && insertedUnary != null
                && insertedUnary.getOperand() != null
                && insertedUnary.getOperand().toString().equals(deletedVar.toString())) {
            List<Operation> matched = new ArrayList<>();
            matched.add(delOp);
            matched.add(insOp);
            return matched;
        }

        return List.of();
    }

    private boolean isUoiKind(UnaryOperatorKind kind) {
        return kind == UnaryOperatorKind.PREINC
                || kind == UnaryOperatorKind.POSTINC
                || kind == UnaryOperatorKind.PREDEC
                || kind == UnaryOperatorKind.POSTDEC;
    }

    @Override
    public String description() {
        return "Mutation ‘UOI’ – inserts unary increment/decrement operator (e.g., ++i, i--, etc.) on variables";
    }
}
