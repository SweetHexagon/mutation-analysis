package com.example.classifier;

import com.example.classifier.patterns.*;
import gumtree.spoon.diff.operations.Operation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChangeClassifier {

    public interface MutationPattern {
        default boolean matches(List<Operation> ops) {
            return !matchingOperations(ops).isEmpty();
        }
        List<Operation> matchingOperations(List<Operation> ops);

        String description();
    }

    private static final Map<MutationKind, MutationPattern> PATTERNS = Map.ofEntries(
            Map.entry(MutationKind.ABS,                      new ABSPattern()),
            Map.entry(MutationKind.AOD,                      new AODPattern()),
            Map.entry(MutationKind.AOR,                      new AORPattern()),
            Map.entry(MutationKind.CONDITIONAL_BOUNDARY,     new ConditionalBoundaryPattern()),
            Map.entry(MutationKind.CONSTRUCTOR_CALLS,        new ConstructorCallsPattern()),
            Map.entry(MutationKind.CRCR,                     new CRCRPattern()),
            Map.entry(MutationKind.EMPTY_RETURNS,            new EmptyReturnsPattern()),
            Map.entry(MutationKind.EXPERIMENTAL_ARGUMENT_PROPAGATION,
                    new ExperimentalArgumentPropagationPattern()),
            Map.entry(MutationKind.EXPERIMENTAL_BIG_INTEGER, new ExperimentalBigIntegerPattern()),
            Map.entry(MutationKind.EXPERIMENTAL_MEMBER_VARIABLE,
                    new ExperimentalMemberVariablePattern()),
            Map.entry(MutationKind.EXPERIMENTAL_NAKED_RECEIVER,
                    new ExperimentalNakedReceiverPattern()),
            Map.entry(MutationKind.EXPERIMENTAL_SWITCH,      new ExperimentalSwitchPattern()),
            Map.entry(MutationKind.FALSE_RETURNS,            new FalseReturnsPattern()),
            Map.entry(MutationKind.INCREMENTS,               new IncrementsPattern()),
            Map.entry(MutationKind.INLINE_CONSTANT,          new InlineConstantPattern()),
            Map.entry(MutationKind.INVERT_NEGATIVES,         new InvertNegativesPattern()),
            Map.entry(MutationKind.MATH,                     new MathPattern()),
            Map.entry(MutationKind.NEGATE_CONDITIONALS,      new NegateConditionalsPattern()),
            Map.entry(MutationKind.NON_VOID_METHOD_CALL,     new NonVoidMethodCallPattern()),
            Map.entry(MutationKind.NULL_RETURNS,             new NullReturnsPattern()),
            Map.entry(MutationKind.OBBN,                     new OBBNPattern()),
            Map.entry(MutationKind.PRIMITIVE_RETURNS,        new PrimitiveReturnsPattern()),
            Map.entry(MutationKind.REMOVE_CONDITIONALS,      new RemoveConditionalsPattern()),
            Map.entry(MutationKind.REMOVE_INCREMENTS,        new RemoveIncrementsPattern()),
            Map.entry(MutationKind.RETURN_VALUES,            new ReturnValuesPattern()),
            Map.entry(MutationKind.ROR,                      new RORPattern()),
            Map.entry(MutationKind.TRUE_RETURNS,             new TrueReturnsPattern()),
            Map.entry(MutationKind.UOIP,                     new UOIPattern()),
            Map.entry(MutationKind.VOID_METHOD_CALL_REMOVAL, new VoidMethodCallRemovalPattern())
    );

    /** Expose the enum→pattern map so you can do entrySet() on it */
    public Map<MutationKind, MutationPattern> getRegisteredPatterns() {
        return PATTERNS;
    }

    /** Old helper for just descriptions */
    public static List<String> classify(List<Operation> ops) {
        return PATTERNS.values().stream()
                .filter(p -> p.matches(ops))
                .map(MutationPattern::description)
                .collect(Collectors.toList());
    }
}
