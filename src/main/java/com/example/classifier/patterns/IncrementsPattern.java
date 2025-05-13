package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.operations.UpdateOperation;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.code.UnaryOperatorKind;

import java.util.ArrayList;
import java.util.List;

public class IncrementsPattern implements ChangeClassifier.MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        List<Operation> matched = new ArrayList<>();
        for (Operation op : ops) {
            if (!(op instanceof UpdateOperation update)) {
                continue;
            }

            CtElement src = update.getSrcNode();
            CtElement dst = update.getDstNode();

            // 1. Strong case: actual unary operator flip
            if (src instanceof CtUnaryOperator<?> srcU
                    && dst instanceof CtUnaryOperator<?> dstU) {
                UnaryOperatorKind k1 = srcU.getKind();
                UnaryOperatorKind k2 = dstU.getKind();
                if (isIncDec(k1) && isIncDec(k2) && k1 != k2) {
                    matched.add(update);
                    continue;
                }
            }

            // 2. Fallback: match text forms i++ ↔ i--
            String srcStr = src.toString();
            String dstStr = dst.toString();
            if ((srcStr.contains("++") && dstStr.contains("--")) ||
                    (srcStr.contains("--") && dstStr.contains("++"))) {
                matched.add(update);
            }
        }
        return matched;
    }

    private boolean isIncDec(UnaryOperatorKind kind) {
        return kind == UnaryOperatorKind.POSTINC
                || kind == UnaryOperatorKind.PREINC
                || kind == UnaryOperatorKind.POSTDEC
                || kind == UnaryOperatorKind.PREDEC;
    }

    @Override
    public String description() {
        return "Mutation ‘INCREMENT_DECREMENT’ – detects i++ <-> i--";
    }
}
