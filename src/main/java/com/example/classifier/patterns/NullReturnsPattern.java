package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.InsertOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;

import java.util.ArrayList;
import java.util.List;

public class NullReturnsPattern implements ChangeClassifier.MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        DeleteOperation delOp = null;
        InsertOperation insOp = null;
        List<Operation> matched = new ArrayList<>();

        for (Operation op : ops) {
            if (delOp == null && op instanceof DeleteOperation del) {
                CtElement node = del.getNode();
                if (node instanceof CtExpression<?> expr
                        && !(expr instanceof CtLiteral<?> lit && lit.getValue() == null)) {
                    delOp = del;
                    matched.add(delOp);
                }
            }
            if (insOp == null && op instanceof InsertOperation ins) {
                CtElement node = ins.getNode();
                if (node instanceof CtLiteral<?> literal
                        && literal.getValue() == null) {
                    insOp = ins;
                    matched.add(insOp);
                }
            }
            if (delOp != null && insOp != null) {
                break;
            }
        }

        return (delOp != null && insOp != null) ? matched : List.of();
    }

    @Override
    public String description() {
        return "Mutation ‘NULL_RETURNS’ – replaces any non-null return value with null";
    }
}
