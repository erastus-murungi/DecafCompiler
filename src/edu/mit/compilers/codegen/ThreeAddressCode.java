package edu.mit.compilers.codegen;

import edu.mit.compilers.ast.AST;
import edu.mit.compilers.symbolTable.SymbolTable;

import java.util.Optional;

public abstract class ThreeAddressCode {
    AST source;
    private String comment;
    public static final String INDENT = "    ";
    public static final String DOUBLE_INDENT = INDENT + INDENT;

    public ThreeAddressCode(AST source) {
        this.source = source;
    }

    public ThreeAddressCode(AST source, String comment) {
        this.comment = comment;
        this.source = source;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Optional<String> getComment() {
        return Optional.ofNullable(comment);
    }

    public abstract <T, E> T accept(ThreeAddressCodeVisitor<T, E> visitor, SymbolTable currentSymbolTable, E extra);
}
