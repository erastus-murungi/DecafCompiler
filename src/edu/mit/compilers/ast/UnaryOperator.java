package edu.mit.compilers.ast;

import edu.mit.compilers.grammar.DecafScanner;
import edu.mit.compilers.grammar.TokenPosition;
import edu.mit.compilers.ir.Visitor;
import edu.mit.compilers.symbolTable.SymbolTable;

public class UnaryOperator extends Operator {
    public UnaryOperator(TokenPosition tokenPosition, @DecafScanner.UnaryOperator String op) {
        super(tokenPosition, op);
    }

    @Override
    public String opRep() {
        return switch (op) {
            case DecafScanner.MINUS -> "Neg";
            case DecafScanner.NOT -> "Not";
            default -> throw new IllegalArgumentException("please register unary operator: " + op);
        };
    }

    @Override
    public <T> T accept(Visitor<T> visitor, SymbolTable curSymbolTable) {
        return null;
    }
}