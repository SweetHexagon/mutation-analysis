package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.*;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;

import java.util.List;

public class ReturnValuesPattern implements ChangeClassifier.MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        boolean replacedWithNull = false;
        boolean constructorMoved = false;
        boolean literalUpdated = false;
        boolean insertedUnaryNeg = false;

        for (Operation op : ops) {
            if (op instanceof UpdateOperation update) {
                CtElement src = update.getSrcNode();
                CtElement dst = update.getDstNode();
                if (src instanceof CtLiteral<?> oldLit && dst instanceof CtLiteral<?> newLit &&
                        isWithinReturn(src) && isReturnValueChanged(oldLit.getValue(), newLit.getValue())) {
                    literalUpdated = true;
                }
            }

            if (op instanceof InsertOperation ins) {
                CtElement node = ins.getNode();

                if (node instanceof CtLiteral<?> lit && lit.getValue() == null && isWithinReturn(lit)) {
                    replacedWithNull = true;
                }

                if (node instanceof CtUnaryOperator<?> unary && unary.getKind() == UnaryOperatorKind.NEG && isWithinReturn(unary)) {
                    insertedUnaryNeg = true;
                }
            }

            if (op instanceof MoveOperation move) {
                if (move.getNode() instanceof CtConstructorCall<?> || move.getNode() instanceof CtInvocation<?>) {
                    constructorMoved = true;
                }
            }
        }

        return literalUpdated || (replacedWithNull && constructorMoved) || insertedUnaryNeg;
    }

    private boolean isReturnValueChanged(Object oldVal, Object newVal) {
        if (oldVal == null || newVal == null) return false;
        if (oldVal instanceof Boolean o && newVal instanceof Boolean n) return !o.equals(n);
        if (oldVal instanceof Number oNum && newVal instanceof Number nNum) return oNum.doubleValue() != nNum.doubleValue();
        return true;
    }

    private boolean isWithinReturn(CtElement e) {
        return e.getParent() instanceof CtReturn<?>;
    }

    @Override
    public String description() {
        return "Mutation ‘RETURN_VALS’ – mutates return values (flips booleans, replaces with 0, -x, or null, etc.)";
    }
}
