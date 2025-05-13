package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.InsertOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;

import java.util.ArrayList;
import java.util.List;

public class NonVoidMethodCallPattern implements ChangeClassifier.MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        List<Operation> matched = new ArrayList<>();
        DeleteOperation delOp = null;
        InsertOperation insOp = null;

        // 1) find the deleted method call
        for (Operation op : ops) {
            if (op instanceof DeleteOperation del
                    && del.getNode() instanceof CtInvocation<?>) {
                delOp = del;
                matched.add(delOp);
                break;
            }
        }
        if (delOp == null) {
            return List.of();
        }

        // 2) find the inserted default-value literal
        for (Operation op : ops) {
            if (op instanceof InsertOperation ins
                    && ins.getNode() instanceof CtLiteral<?> lit
                    && isJavaDefaultValue(lit.getValue())) {
                insOp = ins;
                matched.add(insOp);
                break;
            }
        }

        // only return if both parts were found
        return (insOp != null) ? matched : List.of();
    }

    private boolean isJavaDefaultValue(Object value) {
        return value == null
                || value.equals(0)
                || value.equals(0.0)
                || value.equals(0.0f)
                || value.equals(false)
                || value.equals('\u0000');
    }

    @Override
    public String description() {
        return "Mutation ‘NON_VOID_METHOD_CALLS’ – replaces method call with Java default value for its return type";
    }
}
