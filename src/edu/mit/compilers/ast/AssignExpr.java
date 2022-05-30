package edu.mit.compilers.ast;

import edu.mit.compilers.codegen.CodegenAstVisitor;
import edu.mit.compilers.codegen.names.AssignableName;
import edu.mit.compilers.grammar.TokenPosition;

public abstract class AssignExpr extends AST {
    public TokenPosition tokenPosition;
    public Expression expression;

    @Override
    public Type getType() {
        return expression.getType();
    }

    public abstract String getOperator();

    public AssignExpr(TokenPosition tokenPosition, Expression expression) {
        this.tokenPosition = tokenPosition;
        this.expression = expression;
    }

    public abstract <T> T accept(CodegenAstVisitor<T> codegenAstVisitor, AssignableName resultLocation);

}
