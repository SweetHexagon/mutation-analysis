package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.*;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.declaration.CtElement;

import java.util.List;

public class UOIPattern implements ChangeClassifier.MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        CtVariableRead<?> deletedVar = null;
        CtUnaryOperator<?> insertedUnary = null;

        for (Operation op : ops) {
            if (op instanceof DeleteOperation del && del.getNode() instanceof CtVariableRead<?> vr) {
                deletedVar = vr;
            }

            if (op instanceof InsertOperation ins && ins.getNode() instanceof CtUnaryOperator<?> unary) {
                UnaryOperatorKind kind = unary.getKind();
                if (isUoiKind(kind)) {
                    insertedUnary = unary;
                }
            }
        }

        // Confirm the inserted unary operator wraps the deleted variable
        return deletedVar != null &&
                insertedUnary != null &&
                insertedUnary.getOperand() != null &&
                insertedUnary.getOperand().toString().equals(deletedVar.toString());
    }

    private boolean isUoiKind(UnaryOperatorKind kind) {
        return kind == UnaryOperatorKind.PREINC ||
                kind == UnaryOperatorKind.POSTINC ||
                kind == UnaryOperatorKind.PREDEC ||
                kind == UnaryOperatorKind.POSTDEC;
    }

    @Override
    public String description() {
        return "Mutation ‘UOI’ – inserts unary increment/decrement operator (e.g., ++i, i--, etc.) on variables";
    }
}
