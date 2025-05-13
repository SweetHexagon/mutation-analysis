package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.operations.UpdateOperation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;

import java.util.ArrayList;
import java.util.List;

public class InlineConstantPattern implements ChangeClassifier.MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        List<Operation> matched = new ArrayList<>();
        for (Operation op : ops) {
            if (!(op instanceof UpdateOperation update)) {
                continue;
            }

            CtElement src = update.getSrcNode();
            CtElement dst = update.getDstNode();

            if (src instanceof CtLiteral<?> before && dst instanceof CtLiteral<?> after) {
                Object oldVal = before.getValue();
                Object newVal = after.getValue();

                // boolean flip
                if (oldVal instanceof Boolean && newVal instanceof Boolean) {
                    if (!oldVal.equals(newVal)) {
                        matched.add(update);
                        continue;
                    }
                }

                // numeric constant change
                if (isNumeric(oldVal) && isNumeric(newVal)) {
                    double oldNum = ((Number) oldVal).doubleValue();
                    double newNum = ((Number) newVal).doubleValue();
                    if (Double.compare(oldNum, newNum) != 0) {
                        matched.add(update);
                    }
                }
            }
        }
        return matched;
    }

    private boolean isNumeric(Object o) {
        return o instanceof Byte
                || o instanceof Short
                || o instanceof Integer
                || o instanceof Long
                || o instanceof Float
                || o instanceof Double;
    }

    @Override
    public String description() {
        return "Mutation ‘INLINE_CONSTS’ – modifies literal constants (e.g., boolean flip, numeric constant change)";
    }
}
