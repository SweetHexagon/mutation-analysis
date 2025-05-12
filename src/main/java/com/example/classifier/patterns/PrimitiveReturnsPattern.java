package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.*;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;

import java.util.List;

public class PrimitiveReturnsPattern implements ChangeClassifier.MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        for (Operation op : ops) {
            if (!(op instanceof UpdateOperation update)) continue;

            CtElement src = update.getSrcNode();
            CtElement dst = update.getDstNode();

            if (src instanceof CtLiteral<?> oldLit && dst instanceof CtLiteral<?> newLit) {
                Object oldVal = oldLit.getValue();
                Object newVal = newLit.getValue();

                if (isPrimitive(oldVal) && isZeroValue(newVal) && !isZeroValue(oldVal)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isPrimitive(Object value) {
        return value instanceof Number || value instanceof Character;
    }

    private boolean isZeroValue(Object value) {
        if (value == null) return false;
        if (value instanceof Number num) {
            return num.doubleValue() == 0.0;
        }
        if (value instanceof Character c) {
            return c == '\u0000';
        }
        return false;
    }

    @Override
    public String description() {
        return "Mutation ‘PRIMITIVE_RETURNS’ – replaces primitive return values (int, char, double, etc.) with 0 or equivalent default";
    }
}
