package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.UnaryOperatorKind;

import java.util.ArrayList;
import java.util.List;

public class RemoveIncrementsPattern implements ChangeClassifier.MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        List<Operation> matched = new ArrayList<>();
        for (Operation op : ops) {
            if (op instanceof DeleteOperation del) {
                if (del.getNode() instanceof CtUnaryOperator<?> unary) {
                    UnaryOperatorKind kind = unary.getKind();
                    if (kind == UnaryOperatorKind.PREINC
                            || kind == UnaryOperatorKind.POSTINC
                            || kind == UnaryOperatorKind.PREDEC
                            || kind == UnaryOperatorKind.POSTDEC) {
                        matched.add(del);
                    }
                }
            }
        }
        return matched;
    }

    @Override
    public String description() {
        return "Mutation ‘REMOVE_INCREMENTS’ – removes increment/decrement operations like ++i, i++, --i, i--";
    }
}
