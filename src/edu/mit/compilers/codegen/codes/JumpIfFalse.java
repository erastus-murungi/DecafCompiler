package edu.mit.compilers.codegen.codes;

import edu.mit.compilers.ast.AST;
import edu.mit.compilers.codegen.ThreeAddressCodeVisitor;
import edu.mit.compilers.codegen.names.AbstractName;

import java.util.List;

public class JumpIfFalse extends ThreeAddressCode {
    public final AbstractName condition;
    public final Label trueLabel;

    public JumpIfFalse(AST source, AbstractName condition, Label trueLabel, String comment) {
        super(source, comment);
        this.condition = condition;
        this.trueLabel = trueLabel;
    }

    @Override
    public String toString() {
        return String.format("%s%s %s %s %s %s %s", DOUBLE_INDENT, "IfFalse", condition, "GoTo", trueLabel.label, DOUBLE_INDENT + "<<<<", getComment().isPresent() ? getComment().get() : "");
    }

    @Override
    public <T, E> T accept(ThreeAddressCodeVisitor<T, E> visitor, E extra) {
        return visitor.visit(this, extra);
    }

    @Override
    public List<AbstractName> getNames() {
        return List.of(condition);
    }

}