package com.example.classifier.patterns;

import com.example.classifier.ChangeClassifier;
import gumtree.spoon.diff.operations.*;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.UnaryOperatorKind;
import spoon.reflect.declaration.CtElement;

import java.util.ArrayList;
import java.util.List;

public class CRCRPattern implements ChangeClassifier.MutationPattern {

    @Override
    public List<Operation> matchingOperations(List<Operation> ops) {
        // 1) simple update of one numeric literal to another
        List<Operation> simpleMatches = new ArrayList<>();
        for (Operation op : ops) {
            if (op instanceof UpdateOperation upd) {
                CtElement src = upd.getSrcNode();
                CtElement dst = upd.getDstNode();
                if (src instanceof CtLiteral<?> litSrc
                        && dst instanceof CtLiteral<?> litDst
                        && litSrc.getValue() instanceof Number
                        && litDst.getValue() instanceof Number) {
                    long oldVal = ((Number) litSrc.getValue()).longValue();
                    long newVal = ((Number) litDst.getValue()).longValue();
                    if (newVal == 1
                            || newVal == 0
                            || newVal == oldVal + 1
                            || newVal == oldVal - 1) {
                        simpleMatches.add(upd);
                    }
                }
            }
        }
        if (!simpleMatches.isEmpty()) {
            return simpleMatches;
        }

        // 2) negation pattern: delete literal, insert NEG, move literal
        Long deletedValue = null;
        DeleteOperation delOp = null;
        for (Operation op : ops) {
            if (op instanceof DeleteOperation del
                    && del.getNode() instanceof CtLiteral<?> lit
                    && lit.getValue() instanceof Number) {
                deletedValue = ((Number) lit.getValue()).longValue();
                delOp = del;
                break;
            }
        }

        CtUnaryOperator<?> insertedUo = null;
        InsertOperation insOp = null;
        for (Operation op : ops) {
            if (op instanceof InsertOperation ins
                    && ins.getNode() instanceof CtUnaryOperator<?> uo
                    && uo.getKind() == UnaryOperatorKind.NEG) {
                insertedUo = uo;
                insOp = ins;
                break;
            }
        }
        if (insertedUo == null) {
            return List.of();
        }

        CtLiteral<?> movedLit = null;
        MoveOperation mvOp = null;
        for (Operation op : ops) {
            if (op instanceof MoveOperation mv
                    && mv.getNode() instanceof CtLiteral<?> lit
                    && lit.getValue() instanceof Number) {
                long val = ((Number) lit.getValue()).longValue();
                if (deletedValue == null || val == deletedValue) {
                    movedLit = lit;
                    mvOp = mv;
                    break;
                }
            }
        }

        List<Operation> negMatches = new ArrayList<>();
        // CRCR4: c → -c
        if (movedLit != null && insertedUo.getOperand().equals(movedLit)) {
            negMatches.add(insOp);
            negMatches.add(mvOp);
            if (delOp != null) negMatches.add(delOp);
            return negMatches;
        }
        // CRCR3: pure delete + insert of NEG(1)
        if (deletedValue != null && movedLit == null) {
            CtElement opnd = insertedUo.getOperand();
            if (opnd instanceof CtLiteral<?> litOp
                    && litOp.getValue() instanceof Number
                    && ((Number) litOp.getValue()).longValue() == 1) {
                negMatches.add(insOp);
                if (delOp != null) negMatches.add(delOp);
                return negMatches;
            }
        }

        return List.of();
    }

    @Override
    public String description() {
        return "Mutation ‘CRCR’ (constant replaced by one of CRCR1–CRCR6, including –c split into insert+move)";
    }
}
