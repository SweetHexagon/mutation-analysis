package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.*;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.List;

public class EmptyReturnsPattern implements ChangeClassifier.MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        List<Operation> matched = new ArrayList<>();

        for (Operation op : ops) {
            // ——————— Existing UpdateOperations ———————
            if (op instanceof UpdateOperation upd) {
                CtElement src = upd.getSrcNode();
                CtElement dst = upd.getDstNode();

                // 1) literal-to-literal empty defaults
                if (src instanceof CtLiteral<?> litSrc
                        && dst instanceof CtLiteral<?> litDst
                        && isEmptyLiteral(litSrc.getValue(), litDst.getValue())) {
                    matched.add(upd);

                    // 2) direct invocations of Optional.empty, Collections.emptyList/emptySet, wrapper.valueOf(0)
                } else if (dst instanceof CtInvocation<?> inv
                        && isEmptyInvocation(inv)) {
                    matched.add(upd);

                    // 3) UPDATE of the TYPE_ACCESS from Arrays → Collections when method is emptyList/emptySet
                } else if (src instanceof CtTypeAccess<?> taSrc
                        && dst instanceof CtTypeAccess<?> taDst
                        && "java.util.Arrays".equals(taSrc.getAccessedType().getQualifiedName())
                        && "java.util.Collections".equals(taDst.getAccessedType().getQualifiedName())) {

                    CtElement parent = dst.getParent();
                    if (parent instanceof CtInvocation<?> inv2) {
                        String m = inv2.getExecutable().getSimpleName();
                        if ("emptyList".equals(m) || "emptySet".equals(m)) {
                            matched.add(upd);
                        }
                    }
                }
            }

            // ——————— INSERT of new empty invocations ———————
            if (op instanceof InsertOperation ins) {
                CtElement node = ins.getNode();
                if (node instanceof CtInvocation<?> inv
                        && isEmptyInvocation(inv)) {
                    matched.add(ins);
                }
            }

            // ——————— DELETE of old non-empty HashSet<>(Arrays.asList(...)) ———————
            if (op instanceof DeleteOperation del) {
                CtElement node = del.getNode();
                if (node instanceof CtConstructorCall<?> ctor
                        && "java.util.HashSet".equals(ctor.getType().getQualifiedName())
                        && ctor.getArguments().size() == 1
                        && ctor.getArguments().get(0) instanceof CtInvocation<?> asList
                        && "asList".equals(asList.getExecutable().getSimpleName())
                        && "java.util.Arrays".equals(
                        asList.getExecutable().getDeclaringType().getQualifiedName()
                )) {
                    matched.add(del);
                }
            }

            // ——————— MOVE of the String type-argument when you swapped in emptySet() ———————
            if (op instanceof MoveOperation mov) {
                CtElement src = mov.getSrcNode();
                CtElement dst = mov.getDstNode();
                if (src instanceof CtTypeReference<?> trSrc
                        && dst instanceof CtTypeReference<?> trDst
                        && "java.lang.String".equals(trSrc.getQualifiedName())
                        && "java.lang.String".equals(trDst.getQualifiedName())) {
                    matched.add(mov);
                }
            }
        }

        return matched;
    }

    private boolean isEmptyLiteral(Object oldVal, Object newVal) {
        if (oldVal instanceof String && "".equals(newVal)) {
            return true;
        }
        if (oldVal instanceof Number && newVal instanceof Number) {
            return ((Number) newVal).doubleValue() == 0.0;
        }
        if (oldVal instanceof Character && newVal instanceof Character) {
            return ((Character) newVal).charValue() == '\u0000';
        }
        return false;
    }

    private boolean isEmptyInvocation(CtInvocation<?> inv) {
        CtExecutableReference<?> exec = inv.getExecutable();
        String method = exec.getSimpleName();
        CtElement target = inv.getTarget();
        String qual = null;
        if (target instanceof CtTypeReference<?> tr) {
            qual = tr.getQualifiedName();
        } else if (target instanceof CtTypeAccess<?> ta) {
            qual = ta.getAccessedType().getQualifiedName();
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
        // Wrapper.valueOf(0)/valueOf('\u0000')
        if ("valueOf".equals(method)
                && qual != null
                && qual.startsWith("java.lang.")) {
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
