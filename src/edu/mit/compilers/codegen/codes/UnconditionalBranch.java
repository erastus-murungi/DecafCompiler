package edu.mit.compilers.codegen.codes;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import edu.mit.compilers.cfg.BasicBlock;
import edu.mit.compilers.codegen.InstructionVisitor;
import edu.mit.compilers.codegen.names.Value;
import edu.mit.compilers.utils.Utils;

public class UnconditionalBranch extends Instruction {
    public final BasicBlock target;

    public UnconditionalBranch(BasicBlock target) {
        super(null);
        this.target = target;
    }

    public BasicBlock getTarget() {
        return target;
    }

    @Override
    public <T, E> T accept(InstructionVisitor<T, E> visitor, E extra) {
        return visitor.visit(this, extra);
    }

    @Override
    public List<Value> getAllValues() {
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return String.format("%s%s %s", DOUBLE_INDENT, "goto", getTarget().getInstructionList().getLabel());
    }

    @Override
    public String syntaxHighlightedToString() {
        var goTo = Utils.coloredPrint("goto", Utils.ANSIColorConstants.ANSI_GREEN_BOLD);
        return String.format("%s%s %s", DOUBLE_INDENT, goTo, getTarget().getInstructionList().getLabel());
    }

    @Override
    public Instruction copy() {
        return new UnconditionalBranch(getTarget());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnconditionalBranch that = (UnconditionalBranch) o;
        return Objects.equals(getTarget().getInstructionList().getLabel(), that.getTarget().getInstructionList().getLabel());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTarget().getInstructionList().getLabel());
    }
}