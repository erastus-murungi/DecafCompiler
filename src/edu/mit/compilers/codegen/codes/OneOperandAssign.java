package edu.mit.compilers.codegen.codes;

import edu.mit.compilers.ast.AST;
import edu.mit.compilers.codegen.ThreeAddressCodeVisitor;
import edu.mit.compilers.codegen.names.AbstractName;
import edu.mit.compilers.codegen.names.AssignableName;
import edu.mit.compilers.symbolTable.SymbolTable;

import java.util.Collections;
import java.util.List;

public class OneOperandAssign extends AbstractAssignment {
    AbstractName operand;
    String operator;

    public OneOperandAssign(AST source, AssignableName result, AbstractName operand, String operator) {
        super(result, source);
        this.operand = operand;
        this.operator = operator;
    }

    @Override
    public String toString() {
        return String.format("%s%s = %s %s", DOUBLE_INDENT, dst, operator, operand);
    }

    @Override
    public <T, E> T accept(ThreeAddressCodeVisitor<T, E> visitor, SymbolTable currentSymbolTable, E extra) {
        return visitor.visit(this, currentSymbolTable, extra);
    }

    @Override
    public List<AbstractName> getNames() {
        return List.of(dst, operand);
    }
}
