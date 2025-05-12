package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.InsertOperation;
import gumtree.spoon.diff.operations.UpdateOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;

public class EmptyReturnsPattern implements ChangeClassifier.MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        // Handle UpdateOperation for literals and invocations
        for (Operation op : ops) {
            if (op instanceof UpdateOperation upd) {
                CtElement src = upd.getSrcNode();
                CtElement dst = upd.getDstNode();
                if (src instanceof CtLiteral<?> litSrc && dst instanceof CtLiteral<?> litDst) {
                    if (isEmptyLiteral(litSrc.getValue(), litDst.getValue())) {
                        return true;
                    }
                }
                if (dst instanceof CtInvocation<?> inv) {
                    if (isEmptyInvocation(inv)) {
                        return true;
                    }
                }
            }
        }

        // Handle InsertOperation for invocations
        for (Operation op : ops) {
            if (op instanceof InsertOperation ins) {
                CtElement node = ins.getNode();
                if (node instanceof CtInvocation<?> inv) {
                    if (isEmptyInvocation(inv)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Checks for empty defaults via literal-to-literal updates.
     */
    private boolean isEmptyLiteral(Object oldVal, Object newVal) {
        // String -> ""
        if (oldVal instanceof String && "".equals(newVal)) {
            return true;
        }
        // Numeric wrappers -> zero
        if (oldVal instanceof Number && newVal instanceof Number) {
            if (((Number) newVal).doubleValue() == 0.0) {
                return true;
            }
        }
        // Character -> '\u0000'
        if (oldVal instanceof Character && newVal instanceof Character) {
            if (((Character) newVal).charValue() == '\u0000') {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks for invocations of Optional.empty(), Collections.emptyList()/emptySet(),
     * or wrapper.valueOf(0)/valueOf('\u0000').
     */
    private boolean isEmptyInvocation(CtInvocation<?> inv) {
        CtExecutableReference<?> exec = inv.getExecutable();
        String method = exec.getSimpleName();
        CtElement target = inv.getTarget();
        String qual = null;
        if (target instanceof CtTypeReference<?>) {
            qual = ((CtTypeReference<?>) target).getQualifiedName();
        } else if (target instanceof CtTypeAccess<?>) {
            qual = ((CtTypeAccess<?>) target).getAccessedType().getQualifiedName();
        }

        // Optional.empty()
        if ("empty".equals(method) && "java.util.Optional".equals(qual)) {
            return true;
        }
        // Collections.emptyList()/emptySet()
        if (("emptyList".equals(method) || "emptySet".equals(method))
                && "java.util.Collections".equals(qual)) {
            return true;
        }
        // Wrapper.valueOf(...) with zero or '\u0000'
        if ("valueOf".equals(method) && qual != null && qual.startsWith("java.lang.")) {
            // arguments may include cast expressions, but CtLiteral still holds the literal
            return inv.getArguments().stream()
                    .filter(a -> a instanceof CtLiteral<?>)
                    .map(a -> ((CtLiteral<?>) a).getValue())
                    .anyMatch(val ->
                            (val instanceof Number && ((Number) val).doubleValue() == 0.0)
                                    || (val instanceof Character && ((Character) val).charValue() == '\u0000')
                    );
        }

        return false;
    }

    @Override
    public String description() {
        return "Mutation ‘EMPTY_RETURNS’ (empty defaults: \"\"/0/'\\u0000'/Optional.empty()/Collections.emptyList()/emptySet()/Wrapper.valueOf(0))";
    }
}
