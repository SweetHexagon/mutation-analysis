package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.*;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;

import java.util.List;

public class NonVoidMethodCallPattern implements ChangeClassifier.MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        CtInvocation<?> deletedCall = null;
        CtLiteral<?> insertedLiteral = null;

        for (Operation op : ops) {
            if (op instanceof DeleteOperation del) {
                if (del.getNode() instanceof CtInvocation<?> invocation) {
                    deletedCall = invocation;
                }
            } else if (op instanceof InsertOperation ins) {
                if (ins.getNode() instanceof CtLiteral<?> literal) {
                    insertedLiteral = literal;
                }
            }
        }

        if (deletedCall == null || insertedLiteral == null) {
            return false;
        }

        // Optional: confirm the literal value is a known Java default
        Object defaultValue = insertedLiteral.getValue();
        return isJavaDefaultValue(defaultValue);
    }

    private boolean isJavaDefaultValue(Object value) {
        return value == null || value.equals(0)
                || value.equals(0.0) || value.equals(0.0f)
                || value.equals(false)
                || value.equals('\u0000');
    }

    @Override
    public String description() {
        return "Mutation ‘NON_VOID_METHOD_CALLS’ – replaces method call with Java default value for its return type";
    }
}
