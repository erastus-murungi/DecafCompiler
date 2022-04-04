package edu.mit.compilers.codegen.codes;

import edu.mit.compilers.ast.AST;
import edu.mit.compilers.codegen.ThreeAddressCodeVisitor;
import edu.mit.compilers.codegen.names.AbstractName;
import edu.mit.compilers.codegen.names.AssignableName;

import java.util.Collections;
import java.util.List;

public class ArrayBoundsCheck extends ThreeAddressCode{
    public AssignableName location;
    public Label indexIsLessThanArraySize;
    public Label indexIsLTEZero;
    public Long arraySize;

    public ArrayBoundsCheck(AST source, String comment, Long arraySize, AssignableName location, Label indexIsLessThanArraySize, Label indexIsLTEZero) {
        super(source, comment);
        this.arraySize = arraySize;
        this.location = location;
        this.indexIsLessThanArraySize = indexIsLessThanArraySize;
        this.indexIsLTEZero = indexIsLTEZero;
    }

    @Override
    public <T, E> T accept(ThreeAddressCodeVisitor<T, E> visitor, E extra) {
        return visitor.visit(this, extra);
    }

    @Override
    public List<AbstractName> getNames() {
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return String.format("%sCheckBounds(%s)", DOUBLE_INDENT, location);
    }
}