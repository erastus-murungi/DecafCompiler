package edu.mit.compilers.codegen;

import edu.mit.compilers.ast.AST;
import edu.mit.compilers.symbolTable.SymbolTable;

public class CopyInstruction extends AbstractAssignment {
    String src;

    public CopyInstruction(String src, String dst, AST source) {
        super(dst, source);
        this.src = src;
    }

    public CopyInstruction(String src, String dst, AST source, String comment) {
        super(dst, source, comment);
        this.src = src;
    }

    public String toString() {
        if (getComment().isPresent())
            return String.format("%s%s = %s%s%s", DOUBLE_INDENT, dst, src, DOUBLE_INDENT, " <<<< " + getComment().get());
        return String.format("%s%s = %s", DOUBLE_INDENT, dst, src);
    }

    @Override
    public <T, E> T accept(ThreeAddressCodeVisitor<T, E> visitor, SymbolTable currentSymbolTable, E extra) {
        return visitor.visit(this, currentSymbolTable, extra);
    }
}
