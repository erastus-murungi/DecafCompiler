package edu.mit.compilers.cfg;

import edu.mit.compilers.ast.AST;
import edu.mit.compilers.symbolTable.SymbolTable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public abstract class BasicBlock {
    private final ArrayList<BasicBlock> predecessors;

    public ArrayList<AST> lines;

    public void addPredecessor(BasicBlock predecessor) {
        predecessors.add(predecessor);
    }

    public void addPredecessors(Collection<BasicBlock> predecessors) {
        this.predecessors.addAll(predecessors);
    }

    public void removePredecessor(BasicBlock predecessor) {
        predecessors.remove(predecessor);
    }

    public boolean isRoot() {
        return predecessors.size() == 0;
    }

    public boolean doesNotContainPredecessor(BasicBlock predecessor) {
        return !predecessors.contains(predecessor);
    }

    public ArrayList<BasicBlock> getPredecessors() {
        return predecessors;
    }

    public abstract List<BasicBlock> getSuccessors();

    public AST lastASTLine() {
        return lines.get(lines.size() - 1);
    }

    public BasicBlock() {
        predecessors = new ArrayList<>();
        lines = new ArrayList<>();
    }

    public abstract <T> T  accept(BasicBlockVisitor<T> visitor, SymbolTable symbolTable);

    public String getLabel() {
        return lines.stream().map(AST::getSourceCode).collect(Collectors.joining("\n"));
    }

    @Override
    public String toString() {
        return getLabel();
    }
}
