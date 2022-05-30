package edu.mit.compilers.ast;

import edu.mit.compilers.codegen.CodegenAstVisitor;
import edu.mit.compilers.codegen.names.AssignableName;
import edu.mit.compilers.grammar.DecafScanner;
import edu.mit.compilers.grammar.TokenPosition;
import edu.mit.compilers.ir.Visitor;
import edu.mit.compilers.symbolTable.SymbolTable;
import edu.mit.compilers.utils.Utils;

public class BooleanLiteral extends IntLiteral {
    public BooleanLiteral(TokenPosition tokenPosition, @DecafScanner.BooleanLiteral String literal) {
        super(tokenPosition, literal);
    }

    @Override
    public Long convertToLong() {
        if (Boolean.parseBoolean(Utils.translateEscapes(literal))) {
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
    public <T> T accept(Visitor<T> visitor, SymbolTable curSymbolTable) {
        return visitor.visit(this, curSymbolTable);
    }

    public <T> T accept(CodegenAstVisitor<T> codegenAstVisitor, AssignableName resultLocation) {
        return codegenAstVisitor.visit(this, resultLocation);
    }

    @Override
    public Type getType() {
        return Type.Bool;
    }

}
