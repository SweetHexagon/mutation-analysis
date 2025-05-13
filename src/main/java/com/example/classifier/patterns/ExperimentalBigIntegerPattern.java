package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.Operation;
import gumtree.spoon.diff.operations.UpdateOperation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.List;

public class ExperimentalBigIntegerPattern implements ChangeClassifier.MutationPattern {

    private static final String BIGINT = "java.math.BigInteger";


    private boolean matchesUpdateSwap(List<Operation> ops) {
        return ops.stream()
                .filter(UpdateOperation.class::isInstance)
                .map(UpdateOperation.class::cast)
                .anyMatch(upd -> upd.getSrcNode() instanceof CtInvocation<?> srcInv
                        && upd.getDstNode() instanceof CtInvocation<?> dstInv
                        && isBigIntegerInvocationSwap(srcInv, dstInv));
    }

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        List<Operation> matched = new ArrayList<>();
        for (Operation op : ops) {
            if (op instanceof UpdateOperation upd
                    && upd.getSrcNode() instanceof CtInvocation<?> srcInv
                    && upd.getDstNode() instanceof CtInvocation<?> dstInv
                    && isBigIntegerInvocationSwap(srcInv, dstInv)) {
                matched.add(op);
            }
        }
        return matched;
    }

    /**
     * True if both invocations:
     *  1) share the same target expression (the "x" in x.foo(y))
     *  2) are both declared on java.math.BigInteger
     *  3) take the same arguments (and those args are also BigInteger)
     *  4) have different simpleNames (i.e. foo → bar)
     */
    private boolean isBigIntegerInvocationSwap(CtInvocation<?> src, CtInvocation<?> dst) {
        // 1) same target
        CtExpression<?> t1 = src.getTarget(), t2 = dst.getTarget();
        if (t1 == null || t2 == null || !t1.equals(t2)) {
            return false;
        }
        // 2) target type is BigInteger
        CtTypeReference<?> tt = t1.getType();
        if (tt == null || !BIGINT.equals(tt.getQualifiedName())) {
            return false;
        }
        // 3) same arg list, each arg is BigInteger
        List<CtExpression<?>> a1 = src.getArguments(), a2 = dst.getArguments();
        if (a1.size() != a2.size()) {
            return false;
        }
        for (int i = 0; i < a1.size(); i++) {
            CtExpression<?> e1 = a1.get(i), e2 = a2.get(i);
            if (!e1.equals(e2)) {
                return false;
            }
            CtTypeReference<?> et = e1.getType();
            if (et == null || !BIGINT.equals(et.getQualifiedName())) {
                return false;
            }
        }
        // 4) method names differ, and both declared on BigInteger
        CtExecutableReference<?> x1 = src.getExecutable(), x2 = dst.getExecutable();
        if (x1 == null || x2 == null
                || x1.getSimpleName().equals(x2.getSimpleName())) {
            return false;
        }
        CtTypeReference<?> d1 = x1.getDeclaringType(), d2 = x2.getDeclaringType();
        return d1 != null && d2 != null
                && BIGINT.equals(d1.getQualifiedName())
                && BIGINT.equals(d2.getQualifiedName());
    }

    @Override
    public String description() {
        return "Mutation ‘EXPERIMENTAL_BIG_INTEGER’ – swaps one BigInteger instance‐method for another";
    }
}
