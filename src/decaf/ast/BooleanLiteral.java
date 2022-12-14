package decaf.ast;

import decaf.codegen.CodegenAstVisitor;
import decaf.codegen.names.IrAssignable;
import decaf.codegen.names.IrAssignable;
import decaf.grammar.DecafScanner;
import decaf.grammar.TokenPosition;
import decaf.ir.AstVisitor;
import decaf.symboltable.SymbolTable;

public class BooleanLiteral extends IntLiteral {
    public BooleanLiteral(TokenPosition tokenPosition, @DecafScanner.BooleanLiteral String literal) {
        super(tokenPosition, literal);
    }

    @Override
    public Long convertToLong() {
        if (Boolean.parseBoolean(literal.translateEscapes())) {
            return 1L;
        } else {
            return 0L;
        }
    }

    @Override
    public String toString() {
        return "BooleanLiteral{" + "literal='" + literal + '\'' + '}';
    }

    @Override
    public <T> T accept(AstVisitor<T> ASTVisitor, SymbolTable curSymbolTable) {
        return ASTVisitor.visit(this, curSymbolTable);
    }

    public <T> T accept(CodegenAstVisitor<T> codegenAstVisitor, IrAssignable resultLocation) {
        return codegenAstVisitor.visit(this, resultLocation);
    }

    @Override
    public Type getType() {
        return Type.Bool;
    }

}
