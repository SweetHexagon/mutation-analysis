package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.operations.UpdateOperation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;

import java.util.ArrayList;
import java.util.List;

public class SimpleLiteralChangePattern implements ChangeClassifier.MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        List<Operation> matched = new ArrayList<>();
        for (Operation op : ops) {
            if (!(op instanceof UpdateOperation update)) {
                continue;
            }
            CtElement src = update.getSrcNode();
            CtElement dst = update.getDstNode();

            if (src instanceof CtLiteral<?> oldLit && dst instanceof CtLiteral<?> newLit) {
                Object oldVal = oldLit.getValue();
                Object newVal = newLit.getValue();
                if (isSimpleValue(oldVal)
                        && isSimpleValue(newVal)
                        && !valuesEqual(oldVal, newVal)) {
                    matched.add(update);
                }
            }
        }
        return matched;
    }

    private boolean isSimpleValue(Object value) {
        return value instanceof Number
                || value instanceof Character
                || value instanceof Boolean
                || value instanceof String;
    }

    private boolean valuesEqual(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.equals(b);
    }

    @Override
    public String description() {
        return "Mutation ‘LITERAL_CHANGE’ – replaces one simple literal value (String, int, boolean, char, etc.) with another";
    }
}
