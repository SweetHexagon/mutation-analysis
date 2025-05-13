package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.*;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;

import java.util.ArrayList;
import java.util.List;

public class ReturnValuesPattern implements ChangeClassifier.MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        // 1) “Real” UpdateOperations on literals (if your diff ever emits them)
        List<Operation> literalUpdates = new ArrayList<>();

        // 2) delete+insert pairing for literal rewrites (handles both plain literal changes
        //    and delete-literal + insert-unary(NEG(literal)) scenarios)
        List<DeleteOperation> deletedLiterals = new ArrayList<>();
        InsertOperation        insertedNode   = null;

        // 3) The other RETURN_VALS patterns you already had
        InsertOperation nullInsert        = null;
        MoveOperation    ctorOrInvocation = null;

        for (Operation op : ops) {
            // ——— A: a true UpdateOperation on a literal?
            if (op instanceof UpdateOperation upd) {
                CtElement src = upd.getSrcNode();
                CtElement dst = upd.getDstNode();
                if (src instanceof CtLiteral<?> oldLit
                        && dst instanceof CtLiteral<?> newLit
                        && isWithinReturn(src)
                        && isReturnValueChanged(oldLit.getValue(), newLit.getValue())) {
                    literalUpdates.add(op);
                }
            }

            // ——— B1: literal deleted inside a return?
            if (op instanceof DeleteOperation del) {
                CtElement node = del.getNode();
                if (node instanceof CtLiteral<?> && isWithinReturn(node)) {
                    deletedLiterals.add(del);
                }
            }

            // ——— B2: insertion of either
            //        • a new literal, or
            //        • a new NEG‐unary (which itself wraps the new literal)
            //     all inside a return
            if (op instanceof InsertOperation ins) {
                CtElement n = ins.getNode();
                if ((n instanceof CtLiteral<?> || n instanceof CtUnaryOperator<?>)
                        && isWithinReturn(n)) {
                    insertedNode = ins;
                }
                // your existing “null insert” check
                if (n instanceof CtLiteral<?> lit
                        && lit.getValue() == null
                        && isWithinReturn(lit)) {
                    nullInsert = ins;
                }
            }

            // ——— C: a moved ctor or invocation inside a return
            if (op instanceof MoveOperation mv) {
                CtElement n = mv.getNode();
                if ((n instanceof CtConstructorCall<?> || n instanceof CtInvocation<?>)
                        && isWithinReturn(n)) {
                    ctorOrInvocation = mv;
                }
            }
        }

        // ——— If we saw at least one literal‐delete + one literal-or-unary-insert under the same return,
        //     treat it as the single “return value changed” mutation:
        if (!deletedLiterals.isEmpty() && insertedNode != null) {
            List<Operation> combo = new ArrayList<>();
            combo.addAll(deletedLiterals);
            combo.add(insertedNode);
            return combo;
        }

        // ——— Otherwise, if we got “real” UpdateOperations, use those:
        if (!literalUpdates.isEmpty()) {
            return literalUpdates;
        }

        // ——— Otherwise, fall back to your null+move logic:
        if (nullInsert != null && ctorOrInvocation != null) {
            return List.of(nullInsert, ctorOrInvocation);
        }

        // ——— No match
        return List.of();
    }

    private boolean isWithinReturn(CtElement e) {
        // walk _all_ ancestors until we find a CtReturn or run out
        for (CtElement cur = e; cur != null; cur = cur.getParent()) {
            if (cur instanceof CtReturn<?>) {
                return true;
            }
        }
        return false;
    }



    private boolean isReturnValueChanged(Object oldVal, Object newVal) {
        if (oldVal == null || newVal == null) return false;
        if (oldVal instanceof Boolean o && newVal instanceof Boolean n) {
            return !o.equals(n);
        }
        if (oldVal instanceof Number oNum && newVal instanceof Number nNum) {
            return Double.compare(oNum.doubleValue(), nNum.doubleValue()) != 0;
        }
        return true;
    }

    @Override
    public String description() {
        return "Mutation ‘RETURN_VALS’ – mutates return values (flips booleans, replaces with 0, -x, or null, etc.)";
    }
}
