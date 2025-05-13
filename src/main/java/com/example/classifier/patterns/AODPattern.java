package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.MoveOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.BinaryOperatorKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AODPattern implements ChangeClassifier.MutationPattern {

    /**
     * All arithmetic operators that AOD covers.
     */
    private static final Set<BinaryOperatorKind> ARITHMETIC_OPS = Set.of(
            BinaryOperatorKind.PLUS,
            BinaryOperatorKind.MINUS,
            BinaryOperatorKind.MUL,
            BinaryOperatorKind.DIV,
            BinaryOperatorKind.MOD
    );

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        // 1) Gather all DeleteOperations that remove an arithmetic binary operator
        List<DeleteOperation> deletes = new ArrayList<>();
        // 2) Gather all MoveOperations that move a variable read
        List<MoveOperation>   moves   = new ArrayList<>();

        for (Operation op : ops) {
            if (op instanceof DeleteOperation del
                    && del.getNode() instanceof CtBinaryOperator<?> bin
                    && ARITHMETIC_OPS.contains(bin.getKind())) {
                deletes.add(del);
            }
            else if (op instanceof MoveOperation mv
                    && mv.getNode() instanceof CtVariableRead<?>) {
                moves.add(mv);
            }
        }

        // 3) For each deleted arithmetic operator, see which operand was moved
        List<Operation> matched = new ArrayList<>();
        for (DeleteOperation del : deletes) {
            CtBinaryOperator<?> bin = (CtBinaryOperator<?>) del.getNode();
            CtExpression<?> left  = bin.getLeftHandOperand();
            CtExpression<?> right = bin.getRightHandOperand();

            for (MoveOperation mv : moves) {
                CtVariableRead<?> vr = (CtVariableRead<?>) mv.getNode();
                if (vr.equals(left) || vr.equals(right)) {
                    // record the delete + this move
                    matched.add(del);
                    matched.add(mv);
                }
            }
        }

        return matched;
    }

    @Override
    public String description() {
        return "Mutation ‘AOD’ – replaces `a op b` (any arithmetic op) with one of its operands";
    }
}
