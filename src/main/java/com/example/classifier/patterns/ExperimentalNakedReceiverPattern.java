package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.*;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtElement;

import java.util.ArrayList;
import java.util.List;

public class ExperimentalNakedReceiverPattern implements ChangeClassifier.MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        List<Operation> matched = new ArrayList<>();

        // Case 1: single UpdateOperation (call(x) → x)
        for (Operation op : ops) {
            if (op instanceof UpdateOperation upd) {
                CtElement src = upd.getSrcNode();
                CtElement dst = upd.getDstNode();
                if (src instanceof CtInvocation<?> inv
                        && dst instanceof CtExpression<?> expr) {
                    CtExpression<?> target = inv.getTarget();
                    if (target != null && expr.toString().equals(target.toString())) {
                        matched.add(upd);
                        return matched;
                    }
                }
            }
        }

        // Case 2: delete the invocation, then insert or move its receiver
        DeleteOperation delOp = null;
        Operation insOrMvOp = null;
        for (Operation op : ops) {
            if (delOp == null && op instanceof DeleteOperation del
                    && del.getNode() instanceof CtInvocation<?>) {
                delOp = del;
                matched.add(delOp);
            } else if (delOp != null
                    && (op instanceof InsertOperation || op instanceof MoveOperation)) {
                CtElement node = (op instanceof InsertOperation ins
                        ? ins.getNode()
                        : ((MoveOperation) op).getNode());
                if (node instanceof CtExpression<?> expr) {
                    insOrMvOp = op;
                    matched.add(insOrMvOp);
                    break;
                }
            }
        }

        // Verify that the inserted/moved expression matches the deleted invocation's receiver
        if (delOp != null && insOrMvOp != null) {
            CtInvocation<?> deletedInv = (CtInvocation<?>) delOp.getNode();
            CtExpression<?> insertedExpr = (CtExpression<?>)
                    (insOrMvOp instanceof InsertOperation
                            ? ((InsertOperation) insOrMvOp).getNode()
                            : ((MoveOperation) insOrMvOp).getNode());
            CtExpression<?> target = deletedInv.getTarget();
            if (target != null && insertedExpr.toString().equals(target.toString())) {
                return matched;
            }
        }

        return List.of();
    }

    @Override
    public String description() {
        return "Mutation ‘EXPERIMENTAL_NAKED_RECEIVER’ – replaces a method call with its naked receiver";
    }
}
