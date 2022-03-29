package edu.mit.compilers.codegen;

import edu.mit.compilers.ast.AST;
import edu.mit.compilers.symbolTable.SymbolTable;

public class JumpIfFalse extends ThreeAddressCode {
    public final String condition;
    public final Label trueLabel;

    public JumpIfFalse(AST source, String condition, Label trueLabel, String comment) {
        super(source, comment);
        this.condition = condition;
        this.trueLabel = trueLabel;
    }

    @Override
    public String toString() {
        return String.format("%s%s %s %s %s %s %s", DOUBLE_INDENT, "IfFalse", condition, "GoTo", trueLabel.label, DOUBLE_INDENT + "<<<<", getComment().isPresent() ? getComment().get() : "");
    }

    @Override
    public <T, E> T accept(ThreeAddressCodeVisitor<T, E> visitor, SymbolTable currentSymbolTable, E extra) {
        return visitor.visit(this, currentSymbolTable, extra);
    }
}
