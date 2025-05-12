package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.InsertOperation;
import gumtree.spoon.diff.operations.MoveOperation;
import gumtree.spoon.diff.operations.UpdateOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtElement;

import java.util.List;

public class ExperimentalNakedReceiverPattern implements ChangeClassifier.MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        return matchesUpdate(ops)
                || matchesDeleteInsertOrMove(ops);
    }

    /** catches a single UpdateOperation:  call(x) → x  */
    private boolean matchesUpdate(List<Operation> ops) {
        for (Operation op : ops) {
            if (op instanceof UpdateOperation upd) {
                CtElement src = upd.getSrcNode();
                CtElement dst = upd.getDstNode();
                if (src instanceof CtInvocation<?> inv
                        && dst instanceof CtExpression<?> expr) {
                    // if the new node’s source‐string equals the invocation’s receiver
                    CtExpression<?> target = inv.getTarget();
                    if (target != null
                            && expr.toString().equals(target.toString())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * catches the split‐case where the CtInvocation is deleted
     * and its receiver is either inserted or moved back into place
     */
    private boolean matchesDeleteInsertOrMove(List<Operation> ops) {
        CtInvocation<?> deletedInv = null;
        CtExpression<?>  insertedExpr = null;

        for (Operation op : ops) {
            if (op instanceof DeleteOperation del
                    && del.getNode() instanceof CtInvocation<?> inv) {
                deletedInv = inv;
            }
            else if (op instanceof InsertOperation ins
                    && ins.getNode() instanceof CtExpression<?> expr) {
                insertedExpr = expr;
            }
            else if (op instanceof MoveOperation mv
                    && mv.getNode() instanceof CtExpression<?> expr) {
                insertedExpr = expr;
            }
            if (deletedInv != null && insertedExpr != null) {
                break;
            }
        }

        if (deletedInv != null && insertedExpr != null) {
            CtExpression<?> target = deletedInv.getTarget();
            if (target != null
                    && insertedExpr.toString().equals(target.toString())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String description() {
        return "Mutation ‘EXPERIMENTAL_NAKED_RECEIVER’ – replaces a method call with its naked receiver";
    }
}
