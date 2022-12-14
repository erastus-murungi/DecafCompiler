package decaf.ast;

import java.util.List;

import decaf.codegen.CodegenAstVisitor;
import decaf.codegen.names.IrAssignable;
import decaf.common.Pair;
import decaf.ir.AstVisitor;
import decaf.symboltable.SymbolTable;

public class UnaryOpExpression extends Expression implements HasExpression {
    public Expression operand;
    private final UnaryOperator unaryOperator;

    public UnaryOpExpression(UnaryOperator unaryOperator, Expression operand) {
        super(unaryOperator.tokenPosition);
        this.unaryOperator = unaryOperator;
        this.operand = operand;
    }

    public UnaryOperator getUnaryOperator() {
        return unaryOperator;
    }

    @Override
    public List<Pair<String, AST>> getChildren() {
        return List.of(new Pair<>("op", unaryOperator), new Pair<>("operand", operand));
    }

    @Override
    public boolean isTerminal() {
        return false;
    }

    @Override
    public String toString() {
        return "UnaryOpExpression{" + "op=" + unaryOperator + ", operand=" + operand + '}';
    }

    @Override
    public String getSourceCode() {
        return String.format("%s(%s)", unaryOperator.getSourceCode(), operand.getSourceCode());
    }

    @Override
    public <T> T accept(AstVisitor<T> ASTVisitor, SymbolTable curSymbolTable) {
        return ASTVisitor.visit(this, curSymbolTable);
    }

    public <T> T accept(CodegenAstVisitor<T> codegenAstVisitor, IrAssignable resultLocation) {
        return codegenAstVisitor.visit(this, resultLocation);
    }

    @Override
    public List<Expression> getExpression() {
        return List.of(operand);
    }

    @Override
    public void compareAndSwapExpression(Expression oldExpr, Expression newExpr) {
        if (operand == oldExpr)
            operand = newExpr;
    }
}
