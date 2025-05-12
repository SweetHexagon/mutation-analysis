package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.DeleteOperation;
import gumtree.spoon.diff.operations.InsertOperation;
import gumtree.spoon.diff.operations.MoveOperation;
import gumtree.spoon.diff.operations.UpdateOperation;
import gumtree.spoon.diff.operations.Operation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.declaration.CtElement;
import java.util.List;

public class CRCRPattern implements ChangeClassifier.MutationPattern {

    @Override
    public boolean matches(List<Operation> ops) {
        return matchesSimpleUpdate(ops)
                || matchesNegationPattern(ops);
    }

    private boolean matchesSimpleUpdate(List<Operation> ops) {
        return ops.stream()
                .filter(UpdateOperation.class::isInstance)
                .map(UpdateOperation.class::cast)
                .anyMatch(upd -> {
                    CtElement src = upd.getSrcNode();
                    CtElement dst = upd.getDstNode();
                    if (src instanceof CtLiteral<?> litSrc && dst instanceof CtLiteral<?> litDst
                            && litSrc.getValue() instanceof Number && litDst.getValue() instanceof Number) {
                        long oldVal = ((Number) litSrc.getValue()).longValue();
                        long newVal = ((Number) litDst.getValue()).longValue();
                        return newVal == 1       // CRCR1
                                || newVal == 0       // CRCR2
                                || newVal == oldVal + 1 // CRCR5
                                || newVal == oldVal - 1; // CRCR6
                    }
                    return false;
                });
    }

    private boolean matchesNegationPattern(List<Operation> ops) {
        // find optional deleted literal value
        Long deletedValue = ops.stream()
                .filter(DeleteOperation.class::isInstance)
                .map(DeleteOperation.class::cast)
                .map(DeleteOperation::getNode)
                .filter(n -> n instanceof CtLiteral<?> && ((CtLiteral<?>)n).getValue() instanceof Number)
                .map(n -> ((Number)((CtLiteral<?>)n).getValue()).longValue())
                .findFirst()
                .orElse(null);

        // find inserted NEG operator
        CtUnaryOperator<?> insertedUo = ops.stream()
                .filter(InsertOperation.class::isInstance)
                .map(InsertOperation.class::cast)
                .map(InsertOperation::getNode)
                .filter(n -> n instanceof CtUnaryOperator<?> uo && uo.getKind() == UnaryOperatorKind.NEG)
                .map(CtUnaryOperator.class::cast)
                .findFirst()
                .orElse(null);
        if (insertedUo == null) {
            return false;
        }

        // find moved literal matching deletedValue or any if none
        CtLiteral<?> movedLit = ops.stream()
                .filter(MoveOperation.class::isInstance)
                .map(MoveOperation.class::cast)
                .map(MoveOperation::getNode)
                .filter(n -> n instanceof CtLiteral<?> && ((CtLiteral<?>)n).getValue() instanceof Number)
                .map(CtLiteral.class::cast)
                .filter(lit -> deletedValue == null
                        || ((Number)lit.getValue()).longValue() == deletedValue)
                .findFirst()
                .orElse(null);

        // CRCR4: c → -c
        if (movedLit != null && insertedUo.getOperand().equals(movedLit)) {
            return true;
        }

        // CRCR3: pure delete + insert of NEG(1)
        if (deletedValue != null && movedLit == null) {
            CtElement opnd = insertedUo.getOperand();
            if (opnd instanceof CtLiteral<?> litOp
                    && litOp.getValue() instanceof Number
                    && ((Number)litOp.getValue()).longValue() == 1) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String description() {
        return "Mutation ‘CRCR’ (constant replaced by one of CRCR1–CRCR6, including –c split into insert+move)";
    }
}
