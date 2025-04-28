package com.example.util;

import com.example.MappedNode;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class TreeUtils {

    /**
     * Wraps our TreeNode into a MappedNode for APTED.
     */
    public static MappedNode convertToApted(com.github.javaparser.ast.Node jpNode, MappedNode parent) {
        if (jpNode == null) return null;

        String label = getNodeInfo(jpNode);

        if (jpNode.getChildNodes().isEmpty()) {
            label += label + ":" + jpNode.toString();
        }

        MappedNode node = new MappedNode(label, jpNode, parent);
        for (com.github.javaparser.ast.Node child : jpNode.getChildNodes()) {
            MappedNode childNode = convertToApted(child, node);
            if (childNode != null) {
                node.addChild(childNode);
            }
        }
        return node;
    }

    /**
     * Placeholder for an empty AST.
     */
    public static MappedNode emptyMappedPlaceholder() {
        return new MappedNode("EMPTY", null, null);
    }

    public static String getNodeInfo(com.github.javaparser.ast.Node node) {
        String label = node.getClass().getSimpleName();

        if (node instanceof com.github.javaparser.ast.expr.BinaryExpr binaryExpr) {
            label += " (operator: " + binaryExpr.getOperator().asString() + ")";
        } else if (node instanceof com.github.javaparser.ast.expr.UnaryExpr unaryExpr) {
            label += " (operator: " + unaryExpr.getOperator().asString() + ")";
        } else if (node instanceof com.github.javaparser.ast.expr.AssignExpr assignExpr) {
            label += " (assign-op: " + assignExpr.getOperator().asString() + ")";
        } else if (node instanceof com.github.javaparser.ast.expr.SimpleName simpleName) {
            label += " (name: " + simpleName.getIdentifier() + ")";
        } else if (node instanceof com.github.javaparser.ast.expr.Name name) {
            label += " (qualified-name: " + name.asString() + ")";
        } else if (node instanceof com.github.javaparser.ast.body.MethodDeclaration methodDecl) {
            label += " (method: " + methodDecl.getNameAsString() + ")";
        } else if (node instanceof com.github.javaparser.ast.body.VariableDeclarator varDecl) {
            label += " (var: " + varDecl.getNameAsString() + ")";
        } else if (node instanceof com.github.javaparser.ast.body.FieldDeclaration) {
            label += " (field)";
        } else if (node instanceof com.github.javaparser.ast.body.ClassOrInterfaceDeclaration clazz) {
            label += clazz.isInterface() ? " (interface: " : " (class: ";
            label += clazz.getNameAsString() + ")";
        } else if (node instanceof com.github.javaparser.ast.body.EnumDeclaration enumDecl) {
            label += " (enum: " + enumDecl.getNameAsString() + ")";
        } else if (node instanceof com.github.javaparser.ast.body.ConstructorDeclaration ctorDecl) {
            label += " (constructor: " + ctorDecl.getNameAsString() + ")";
        } else if (node instanceof com.github.javaparser.ast.stmt.IfStmt) {
            label += " (if-statement)";
        } else if (node instanceof com.github.javaparser.ast.stmt.ForStmt) {
            label += " (for-loop)";
        } else if (node instanceof com.github.javaparser.ast.stmt.ForEachStmt) {
            label += " (for-each-loop)";
        } else if (node instanceof com.github.javaparser.ast.stmt.WhileStmt) {
            label += " (while-loop)";
        } else if (node instanceof com.github.javaparser.ast.stmt.DoStmt) {
            label += " (do-while-loop)";
        } else if (node instanceof com.github.javaparser.ast.stmt.SwitchStmt) {
            label += " (switch-statement)";
        } else if (node instanceof com.github.javaparser.ast.stmt.SwitchEntry) {
            label += " (switch-entry)";
        } else if (node instanceof com.github.javaparser.ast.stmt.TryStmt) {
            label += " (try-catch)";
        } else if (node instanceof com.github.javaparser.ast.stmt.CatchClause) {
            label += " (catch-block)";
        } else if (node instanceof com.github.javaparser.ast.stmt.ThrowStmt) {
            label += " (throw-statement)";
        } else if (node instanceof com.github.javaparser.ast.stmt.ReturnStmt) {
            label += " (return-statement)";
        } else if (node instanceof com.github.javaparser.ast.stmt.BlockStmt) {
            label += " (block)";
        } else if (node instanceof com.github.javaparser.ast.stmt.ExpressionStmt) {
            label += " (expression-statement)";
        } else if (node instanceof com.github.javaparser.ast.expr.MethodCallExpr callExpr) {
            label += " (method-call: " + callExpr.getNameAsString() + ")";
        } else if (node instanceof com.github.javaparser.ast.expr.ObjectCreationExpr objectCreationExpr) {
            label += " (new-object: " + objectCreationExpr.getType().asString() + ")";
        } else if (node instanceof com.github.javaparser.ast.expr.LambdaExpr) {
            label += " (lambda-expression)";
        } else if (node instanceof com.github.javaparser.ast.expr.FieldAccessExpr fieldAccessExpr) {
            label += " (field-access: " + fieldAccessExpr.getNameAsString() + ")";
        } else if (node instanceof com.github.javaparser.ast.expr.ArrayAccessExpr) {
            label += " (array-access)";
        } else if (node instanceof com.github.javaparser.ast.expr.ArrayCreationExpr) {
            label += " (array-creation)";
        } else if (node instanceof com.github.javaparser.ast.expr.ArrayInitializerExpr) {
            label += " (array-initializer)";
        } else if (node instanceof com.github.javaparser.ast.expr.CastExpr castExpr) {
            label += " (cast: " + castExpr.getType().asString() + ")";
        } else if (node instanceof com.github.javaparser.ast.expr.ThisExpr) {
            label += " (this)";
        } else if (node instanceof com.github.javaparser.ast.expr.SuperExpr) {
            label += " (super)";
        } else if (node instanceof com.github.javaparser.ast.expr.InstanceOfExpr) {
            label += " (instanceof)";
        } else if (node instanceof com.github.javaparser.ast.expr.ConditionalExpr) {
            label += " (ternary-conditional)";
        } else if (node instanceof com.github.javaparser.ast.expr.EnclosedExpr) {
            label += " (parentheses)";
        } else if (node instanceof com.github.javaparser.ast.expr.StringLiteralExpr stringLiteralExpr) {
            label += " (string: \"" + stringLiteralExpr.getValue() + "\")";
        } else if (node instanceof com.github.javaparser.ast.expr.IntegerLiteralExpr intLiteralExpr) {
            label += " (int: " + intLiteralExpr.getValue() + ")";
        } else if (node instanceof com.github.javaparser.ast.expr.BooleanLiteralExpr boolLiteralExpr) {
            label += " (boolean: " + boolLiteralExpr.getValue() + ")";
        } else if (node instanceof com.github.javaparser.ast.expr.CharLiteralExpr charLiteralExpr) {
            label += " (char: '" + charLiteralExpr.getValue() + "')";
        } else if (node instanceof com.github.javaparser.ast.expr.DoubleLiteralExpr doubleLiteralExpr) {
            label += " (double: " + doubleLiteralExpr.getValue() + ")";
        } else if (node instanceof com.github.javaparser.ast.expr.LongLiteralExpr longLiteralExpr) {
            label += " (long: " + longLiteralExpr.getValue() + ")";
        } else if (node instanceof com.github.javaparser.ast.expr.NullLiteralExpr) {
            label += " (null)";
        } else if (node instanceof com.github.javaparser.ast.type.PrimitiveType primitiveType) {
            label += " (primitive-type: " + primitiveType.asString() + ")";
        } else if (node instanceof com.github.javaparser.ast.type.ClassOrInterfaceType classOrInterfaceType) {
            label += " (type: " + classOrInterfaceType.asString() + ")";
        } else if (node instanceof com.github.javaparser.ast.type.ArrayType) {
            label += " (array-type)";
        } else if (node instanceof com.github.javaparser.ast.type.VoidType) {
            label += " (void)";
        } else if (node instanceof com.github.javaparser.ast.type.UnknownType) {
            label += " (unknown-type)";
        } else if (node instanceof com.github.javaparser.ast.PackageDeclaration packageDeclaration) {
            label += " (package: " + packageDeclaration.getNameAsString() + ")";
        } else if (node instanceof com.github.javaparser.ast.ImportDeclaration importDeclaration) {
            label += " (import: " + importDeclaration.getNameAsString() + ")";
        } else if (node instanceof com.github.javaparser.ast.body.Parameter parameter) {
            label += " (parameter: " + parameter.getNameAsString() + ")";
        }

        return label;
    }

}
