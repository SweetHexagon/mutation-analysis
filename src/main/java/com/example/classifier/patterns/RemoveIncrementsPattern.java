package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.declaration.CtElement;

import java.util.List;

public class RemoveIncrementsPattern implements ChangeClassifier.MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        for (Operation op : ops) {
            if (op instanceof DeleteOperation del) {
                CtElement node = del.getNode();

                if (node instanceof CtUnaryOperator<?> unary) {
                    UnaryOperatorKind kind = unary.getKind();
                    if (kind == UnaryOperatorKind.PREINC || kind == UnaryOperatorKind.POSTINC ||
                            kind == UnaryOperatorKind.PREDEC || kind == UnaryOperatorKind.POSTDEC) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public String description() {
        return "Mutation ‘REMOVE_INCREMENTS’ – removes increment/decrement operations like ++i, i++, --i, i--";
    }
}
