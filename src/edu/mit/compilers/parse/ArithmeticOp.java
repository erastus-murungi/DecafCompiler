package edu.mit.compilers.parse;

import edu.mit.compilers.grammar.DecafScanner;
import edu.mit.compilers.grammar.TokenPosition;

import static edu.mit.compilers.grammar.DecafScanner.*;

public class ArithmeticOp extends BinOp {
    public ArithmeticOp(TokenPosition tokenPosition, @ArithmeticOperator String op) {
        super(tokenPosition, op);
    }

    @Override
    public String opRep() {
        return switch (op) {
            case DecafScanner.PLUS -> "Add";
            case DecafScanner.MINUS -> "Sub";
            case DecafScanner.MULTIPLY -> "Multiply";
            case DecafScanner.DIVIDE -> "Divide";
            case DecafScanner.MOD -> "Mod";
            default -> throw new IllegalArgumentException("please register a display string for: " + op);
        };
    }
}
