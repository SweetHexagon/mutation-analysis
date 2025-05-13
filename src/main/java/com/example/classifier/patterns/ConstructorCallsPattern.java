package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier.MutationPattern;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.InsertOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;

import java.util.ArrayList;
import java.util.List;

public class ConstructorCallsPattern implements MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        CtConstructorCall<?> deletedCtor = null;
        CtLiteral<?> insertedNull = null;
        DeleteOperation delOp = null;
        InsertOperation insOp = null;
        List<Operation> matched = new ArrayList<>();

        for (Operation op : ops) {
            if (deletedCtor == null
                    && op instanceof DeleteOperation del
                    && del.getNode() instanceof CtConstructorCall<?>) {
                deletedCtor = (CtConstructorCall<?>) del.getNode();
                delOp = del;
                matched.add(delOp);
            }
            if (insertedNull == null
                    && op instanceof InsertOperation ins
                    && ins.getNode() instanceof CtLiteral<?> lit
                    && lit.getValue() == null) {
                insertedNull = lit;
                insOp = ins;
                matched.add(insOp);
            }
            if (deletedCtor != null && insertedNull != null) {
                break;
            }
        }

        if (deletedCtor != null && insertedNull != null) {
            return matched;
        }
        return List.of();
    }

    @Override
    public String description() {
        return "Mutation ‘Constructor Call → null’ (replaced `new X(...)` with `null`)";
    }
}
