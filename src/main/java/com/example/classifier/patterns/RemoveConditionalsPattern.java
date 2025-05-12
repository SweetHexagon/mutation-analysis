package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.*;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;

import java.util.List;

public class RemoveConditionalsPattern implements ChangeClassifier.MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        boolean deletedCondition = false;
        boolean insertedLiteral = false;

        for (Operation op : ops) {
            if (op instanceof DeleteOperation del && del.getNode() instanceof CtBinaryOperator<?>) {
                deletedCondition = true;
            }

            if (op instanceof InsertOperation ins && ins.getNode() instanceof CtLiteral<?> lit) {
                if (lit.getValue() instanceof Boolean) {
                    insertedLiteral = true;
                }
            }
        }

        return deletedCondition && insertedLiteral;
    }

    @Override
    public String description() {
        return "Mutation ‘REMOVE_CONDITIONALS’ – replaces conditional (e.g., a == b) with boolean literal (true/false)";
    }
}
