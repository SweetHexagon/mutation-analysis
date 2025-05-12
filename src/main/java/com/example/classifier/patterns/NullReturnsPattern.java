package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.*;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;

import java.util.List;

public class NullReturnsPattern implements ChangeClassifier.MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        CtExpression<?> deletedExpr = null;
        CtLiteral<?> insertedLiteral = null;

        for (Operation op : ops) {
            if (op instanceof DeleteOperation del) {
                CtElement node = del.getNode();
                if (node instanceof CtExpression<?> expr && !(expr instanceof CtLiteral<?> lit && lit.getValue() == null)) {
                    deletedExpr = expr;
                }
            }

            if (op instanceof InsertOperation ins) {
                CtElement node = ins.getNode();
                if (node instanceof CtLiteral<?> literal && literal.getValue() == null) {
                    insertedLiteral = literal;
                }
            }
        }

        return deletedExpr != null && insertedLiteral != null;
    }

    @Override
    public String description() {
        return "Mutation ‘NULL_RETURNS’ – replaces any non-null return value with null";
    }
}
