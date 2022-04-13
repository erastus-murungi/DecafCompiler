package edu.mit.compilers.cfg;

import edu.mit.compilers.symbolTable.SymbolTable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class NOP extends BasicBlockBranchLess {
    String nopLabel;
    public NOP() {
        super(null);
    }

    public NOP(String label) {
        super(null);
        this.nopLabel = label;
    }

    public Optional<String> getNopLabel() {
        return Optional.ofNullable(nopLabel);
    }

    public<T> T accept(BasicBlockVisitor<T> visitor, SymbolTable symbolTable) {
        return visitor.visit(this, symbolTable);
    }

    @Override
    public List<BasicBlock> getSuccessors() {
        if (autoChild == null)
            return Collections.emptyList();
        return List.of(autoChild);
    }

    @Override
    public String getLabel() {
        return String.format("NOP{%s}", getNopLabel().orElse(""));
    }
}
