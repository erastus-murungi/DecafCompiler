package edu.mit.compilers.ast;

import edu.mit.compilers.grammar.TokenPosition;
import edu.mit.compilers.ir.Visitor;
import edu.mit.compilers.symbolTable.SymbolTable;

public class CharLiteral extends IntLiteral {
    public CharLiteral(TokenPosition tokenPosition, String literal) {
        super(tokenPosition, literal);
    }

    @Override
    public Long convertToLong() {
        return (long) literal.charAt(1);
    }

    @Override
    public <T> T accept(Visitor<T> visitor, SymbolTable curSymbolTable) {
        return visitor.visit(this, curSymbolTable);
    }
}
