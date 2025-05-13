package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.operations.UpdateOperation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtReturn;

import java.util.ArrayList;
import java.util.List;

public class FalseReturnsPattern implements ChangeClassifier.MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        List<Operation> matched = new ArrayList<>();
        for (Operation op : ops) {
            if (op instanceof UpdateOperation upd && isTrueReturnFlippedToFalse(upd)) {
                matched.add(upd);
            }
        }
        return matched;
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
        return dstLit.getParent(CtReturn.class) != null;
    }

    @Override
    public String description() {
        return "Mutation ‘FALSE_RETURNS’ – replaced a boolean return value with false";
    }
}
