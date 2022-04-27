package edu.mit.compilers.dataflow.analyses;

import edu.mit.compilers.cfg.BasicBlock;
import edu.mit.compilers.codegen.codes.HasOperand;
import edu.mit.compilers.codegen.codes.HasResult;
import edu.mit.compilers.codegen.codes.ThreeAddressCode;
import edu.mit.compilers.codegen.names.ArrayName;
import edu.mit.compilers.dataflow.Direction;
import edu.mit.compilers.dataflow.analyses.DataFlowAnalysis;
import edu.mit.compilers.dataflow.usedef.Def;
import edu.mit.compilers.dataflow.usedef.Use;
import edu.mit.compilers.dataflow.usedef.UseDef;

import java.util.*;

public class LiveVariableAnalysis extends DataFlowAnalysis<UseDef> {
    public LiveVariableAnalysis(BasicBlock basicBlock) {
        super(basicBlock);
    }

    @Override
    public void computeUniversalSetsOfValues() {
    }

    @Override
    public Set<UseDef> transferFunction(UseDef domainElement) {
        return null;
    }

    @Override
    public Direction direction() {
        return Direction.BACKWARDS;
    }

    @Override
    public void initializeWorkSets() {
        out = new HashMap<>();
        in = new HashMap<>();

        for (BasicBlock basicBlock : basicBlocks) {
            out.put(basicBlock, new HashSet<>()); // all copies are available at initialization time
            in.put(basicBlock, new HashSet<>());
        }
        in.put(exitBlock, use(exitBlock));
    }

    @Override
    public void runWorkList() {
        var workList = new ArrayDeque<>(basicBlocks);
        workList.remove(entryBlock);

        while (!workList.isEmpty()) {
            final BasicBlock B = workList.pop();
            var oldIn = in(B);

            // OUT[B] = intersect OUT[s] for all s in successors
            out.put(B, meet(B));

            // IN[B] = USE[B] ∪ (IN[B] - DEF[B])
            in.put(B, union(use(B), difference(out(B), def(B))));

            if (!in(B).equals(oldIn)) {
                workList.addAll(B.getPredecessors());
            }
        }
    }

    // an instruction makes a variable "live" it references it
    private Set<UseDef> use(BasicBlock basicBlock) {
        var useSet = new HashSet<UseDef>();
        final var tacReversed = basicBlock.codes();
        Collections.reverse(tacReversed);
        for (ThreeAddressCode tac : tacReversed) {
            if (tac instanceof HasOperand) {
                var hasOperand = (HasOperand) tac;
                hasOperand.getOperandNamesNoArrayNoConstants().forEach(abstractName -> useSet.add(new Use(abstractName, tac)));
            }
        }
        return useSet;
    }

    private Set<UseDef> def(BasicBlock basicBlock) {
        var defSet = new HashSet<UseDef>();
        final var tacReversed = basicBlock.codes();
        Collections.reverse(tacReversed);
        for (ThreeAddressCode tac : tacReversed) {
            if (tac instanceof HasResult) {
                var hasResult = (HasResult) tac;
                if (!(hasResult.getResultLocation() instanceof ArrayName)) {
                    defSet.add(new Def(hasResult));
                }
            }
        }
        return defSet;
    }


    @Override
    public Set<UseDef> meet(BasicBlock basicBlock) {
        var outSet = new HashSet<UseDef>();
        for (BasicBlock successor : basicBlock.getSuccessors()) {
            outSet.addAll(in(successor));
        }
        return outSet;
    }
}