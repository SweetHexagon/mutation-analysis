package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.operations.UpdateOperation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;

import java.util.List;

public class InlineConstantPattern implements ChangeClassifier.MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        for (Operation op : ops) {
            if (!(op instanceof UpdateOperation update)) continue;

            CtElement src = update.getSrcNode();
            CtElement dst = update.getDstNode();

            if (src instanceof CtLiteral<?> before && dst instanceof CtLiteral<?> after) {
                Object oldVal = before.getValue();
                Object newVal = after.getValue();

                if (oldVal instanceof Boolean && newVal instanceof Boolean) {
                    if (!oldVal.equals(newVal)) return true;
                }

                if (isNumeric(oldVal) && isNumeric(newVal)) {
                    double oldNum = ((Number) oldVal).doubleValue();
                    double newNum = ((Number) newVal).doubleValue();

                    // This can be tuned; we use approximate equality to allow 42 → 0, 3.14 → 0.0, etc.
                    if (oldNum != newNum) return true;
                }
            }
        }

        return false;
    }

    private boolean isNumeric(Object o) {
        return o instanceof Byte || o instanceof Short || o instanceof Integer ||
                o instanceof Long || o instanceof Float || o instanceof Double;
    }

    @Override
    public String description() {
        return "Mutation ‘INLINE_CONSTS’ – modifies literal constants (e.g., boolean flip, numeric constant change)";
    }
}
