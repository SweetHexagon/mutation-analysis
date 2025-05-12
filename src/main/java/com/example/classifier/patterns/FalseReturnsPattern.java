package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.UpdateOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.CtElement;

import java.util.List;
import java.util.Objects;

public class FalseReturnsPattern implements ChangeClassifier.MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        return ops.stream()
                .filter(op -> op instanceof UpdateOperation)
                .map(UpdateOperation.class::cast)
                .anyMatch(this::isTrueReturnFlippedToFalse);
    }

    private boolean isTrueReturnFlippedToFalse(UpdateOperation upd) {
        // src must be a boolean-literal "true"
        if (!(upd.getSrcNode() instanceof CtLiteral<?> srcLit)) {
            return false;
        }
        Object oldVal = srcLit.getValue();
        if (!(oldVal instanceof Boolean) || !Boolean.TRUE.equals(oldVal)) {
            return false;
        }

        // dst must be a boolean-literal "false"
        if (!(upd.getDstNode() instanceof CtLiteral<?> dstLit)) {
            return false;
        }
        Object newVal = dstLit.getValue();
        if (!(newVal instanceof Boolean) || !Boolean.FALSE.equals(newVal)) {
            return false;
        }

        // and that literal must sit inside a return-statement
        CtReturn<?> rtn = dstLit.getParent(CtReturn.class);
        return rtn != null;
    }

    @Override
    public String description() {
        return "Mutation ‘FALSE_RETURNS’ – replaced a boolean return value with false";
    }
}
