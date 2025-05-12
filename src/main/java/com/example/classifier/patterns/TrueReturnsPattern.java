package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.*;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.code.CtReturn;

import java.util.List;

public class TrueReturnsPattern implements ChangeClassifier.MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        for (Operation op : ops) {
            if (op instanceof UpdateOperation update) {
                CtElement src = update.getSrcNode();
                CtElement dst = update.getDstNode();

                if (isFalseLiteral(src) && isTrueLiteral(dst) &&
                        isInReturnContext(src, dst)) {
                    return true;
                }
            }
            if (op instanceof DeleteOperation del && isFalseLiteral(del.getNode())) {
                for (Operation other : ops) {
                    if (other instanceof InsertOperation ins &&
                            isTrueLiteral(ins.getNode()) &&
                            isInReturnContext(del.getNode(), ins.getNode())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isFalseLiteral(CtElement element) {
        return element instanceof CtLiteral<?> lit &&
                Boolean.FALSE.equals(lit.getValue());
    }

    private boolean isTrueLiteral(CtElement element) {
        return element instanceof CtLiteral<?> lit &&
                Boolean.TRUE.equals(lit.getValue());
    }

    private boolean isInReturnContext(CtElement... elements) {
        for (CtElement e : elements) {
            if (!(e.getParent() instanceof CtReturn<?>)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String description() {
        return "Mutation ‘TRUE_RETURNS’ – replaces boolean return values with true";
    }
}
