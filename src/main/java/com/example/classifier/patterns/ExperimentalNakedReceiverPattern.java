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

        // Case 1: Single UpdateOperation (e.g., call(x) → x)
        for (Operation op : ops) {
            if (op instanceof UpdateOperation upd) {
                CtElement src = upd.getSrcNode();
                CtElement dst = upd.getDstNode();
                if (src instanceof CtInvocation<?> inv && dst instanceof CtExpression<?> expr) {
                    CtExpression<?> target = inv.getTarget();
                    if (target != null && target.equals(expr)) {
                        matched.add(upd);
                        return matched;
                    }
                }
            }
        }

        // Case 2: Delete Invocation + Move/Insert Receiver
        CtInvocation<?> deletedInvocation = null;
        CtExpression<?> movedOrInsertedExpr = null;
        Operation moveOrInsertOp = null;
        Operation deleteInvOp = null;

        // Find deleted invocation
        for (Operation op : ops) {
            if (op instanceof DeleteOperation del && del.getNode() instanceof CtInvocation<?> inv) {
                deletedInvocation = inv;
                deleteInvOp = del;
                break;
            }
        }

        if (deletedInvocation != null) {
            // Find moved or inserted expression matching the receiver
            for (Operation op : ops) {
                if (op instanceof InsertOperation || op instanceof MoveOperation) {
                    CtElement node = (op instanceof InsertOperation ins)
                            ? ins.getNode()
                            : ((MoveOperation) op).getNode();

                    if (node instanceof CtExpression<?> expr) {
                        movedOrInsertedExpr = expr;
                        moveOrInsertOp = op;
                        break;
                    }
                }
            }

            // Compare deleted invocation's target with inserted/moved expression
            if (movedOrInsertedExpr != null) {
                CtExpression<?> target = deletedInvocation.getTarget();
                if (target != null && target.equals(movedOrInsertedExpr)) {
                    matched.add(deleteInvOp);
                    matched.add(moveOrInsertOp);
                    return matched;
                }
            }
        }

        return List.of();
    }

    @Override
    public String description() {
        return "Mutation ‘EXPERIMENTAL_NAKED_RECEIVER’ – replaces a method call with its naked receiver";
    }
}