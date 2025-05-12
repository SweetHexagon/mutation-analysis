package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier.MutationPattern;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.InsertOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;

import java.util.List;

public class ConstructorCallsPattern implements MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        CtConstructorCall<?> deletedCtor = null;
        CtLiteral<?>        insertedNull = null;

        for (Operation op : ops) {
            if (op instanceof DeleteOperation del) {
                CtElement node = del.getNode();
                if (node instanceof CtConstructorCall<?>) {
                    deletedCtor = (CtConstructorCall<?>) node;
                }
            }
            if (op instanceof InsertOperation ins) {
                CtElement node = ins.getNode();
                if (node instanceof CtLiteral<?> lit
                        && lit.getValue() == null) {
                    insertedNull = lit;
                }
            }
        }

        return deletedCtor != null && insertedNull != null;
    }

    @Override
    public String description() {
        return "Mutation ‘Constructor Call → null’ (replaced `new X(...)` with `null`)";
    }
}
