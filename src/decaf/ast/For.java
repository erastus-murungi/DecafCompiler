package decaf.ast;

import java.util.List;

import decaf.grammar.DecafScanner;
import decaf.grammar.TokenPosition;
import decaf.codegen.CodegenAstVisitor;
import decaf.codegen.names.IrAssignable;
import decaf.ir.AstVisitor;
import decaf.symboltable.SymbolTable;
import decaf.common.Pair;
import decaf.common.Utils;

public class For extends Statement implements HasExpression {
    public final Initialization initialization;
    public final Assignment update;
    public final Block block;
    public Expression terminatingCondition;

    public For(
        TokenPosition tokenPosition,
        Initialization initialization,
        Expression terminatingCondition,
        Assignment update,
        Block block) {
        super(tokenPosition);
        this.initialization = initialization;
        this.terminatingCondition = terminatingCondition;
        this.update = update;
        this.block = block;
    }

    @Override
    public List<Pair<String, AST>> getChildren() {
        return List.of(
                new Pair<>("initialization", initialization),
                new Pair<>("terminatingCondition", terminatingCondition),
                new Pair<>("update", update),
                new Pair<>("block", block));
    }

    @Override
    public String toString() {
        return "For{" +
                "initialization=" + initialization +
                ", terminatingCondition=" + terminatingCondition +
                ", update=" + update +
                ", block=" + block +
                '}';
    }

    @Override
    public String getSourceCode() {
        return String.format("%s (%s; %s; %s) {\n    %s\n    }",
                             DecafScanner.RESERVED_FOR, initialization.getSourceCode(), terminatingCondition.getSourceCode(), update.getSourceCode(), Utils.indentBlock(block));
    }

    @Override
    public boolean isTerminal() {
        return false;
    }

    @Override
    public <T> T accept(AstVisitor<T> ASTVisitor, SymbolTable curSymbolTable) {
        return ASTVisitor.visit(this, curSymbolTable);
    }


    @Override
    public List<Expression> getExpression() {
        return List.of(terminatingCondition);
    }

    @Override
    public void compareAndSwapExpression(Expression oldExpr, Expression newExpr) {
        if (terminatingCondition == oldExpr)
            terminatingCondition = newExpr;
    }

    public <T> T accept(CodegenAstVisitor<T> codegenAstVisitor, IrAssignable resultLocation) {
        return null;
    }
}
