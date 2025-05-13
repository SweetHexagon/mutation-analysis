package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.MoveOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.declaration.CtElement;

import java.util.ArrayList;
import java.util.List;

public class InvertNegativesPattern implements ChangeClassifier.MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        CtUnaryOperator<?> deletedUnary = null;
        CtVariableRead<?>  movedVar     = null;
        DeleteOperation    delOp        = null;
        MoveOperation      mvOp         = null;
        List<Operation>    matched      = new ArrayList<>();

        // find the deleted negation and the moved variable
        for (Operation op : ops) {
            if (op instanceof DeleteOperation del
                    && del.getNode() instanceof CtUnaryOperator<?> unary
                    && unary.getKind() == UnaryOperatorKind.NEG
                    && unary.getOperand() instanceof CtVariableRead<?>) {
                deletedUnary = unary;
                delOp = del;
                matched.add(del);
            } else if (op instanceof MoveOperation mov
                    && mov.getNode() instanceof CtVariableRead<?> vr) {
                movedVar = vr;
                mvOp = mov;
                matched.add(mov);
            }
        }

        // verify both parts and that the moved variable is the operand of the deleted negation
        if (deletedUnary != null && movedVar != null) {
            CtExpression<?> operand = deletedUnary.getOperand();
            if (operand.equals(movedVar)) {
                return matched;
            }
        }

        return List.of();
    }

    @Override
    public String description() {
        return "Mutation ‘INVERT_NEGS’ – removes unary negation from a variable (e.g., -x → x), with verified operand match";
    }
}
