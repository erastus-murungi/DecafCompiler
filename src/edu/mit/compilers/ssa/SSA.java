package edu.mit.compilers.ssa;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import edu.mit.compilers.cfg.BasicBlock;
import edu.mit.compilers.codegen.codes.CopyInstruction;
import edu.mit.compilers.codegen.codes.HasOperand;
import edu.mit.compilers.codegen.codes.Instruction;
import edu.mit.compilers.codegen.codes.Method;
import edu.mit.compilers.codegen.codes.StoreInstruction;
import edu.mit.compilers.codegen.names.IrValue;
import edu.mit.compilers.codegen.names.IrRegister;
import edu.mit.compilers.dataflow.analyses.LiveVariableAnalysis;
import edu.mit.compilers.dataflow.dominator.DominatorTree;
import edu.mit.compilers.registerallocation.Coalesce;
import edu.mit.compilers.utils.CLI;
import edu.mit.compilers.utils.Pair;
import edu.mit.compilers.utils.ProgramIr;
import edu.mit.compilers.utils.TarjanSCC;
import edu.mit.compilers.utils.Utils;

public class SSA {
    private SSA() {
    }

    public static void construct(Method method) {
        var entryBlock = method.entryBlock;
        var basicBlocks = TarjanSCC.getReversePostOrder(entryBlock);
        var dominatorTree = new DominatorTree(entryBlock);
        placePhiFunctions(entryBlock, basicBlocks, dominatorTree);
        renameVariables(method, dominatorTree, basicBlocks);
        verify(basicBlocks);
        if (CLI.debug)
            Utils.printSsaCfg(List.of(method), "ssa_before_" + method.methodName());
    }

    private static void renameMethodArgs(Method method) {
        for (var V: method.getParameterNames()) {
            V.renameForSsa(0);
        }
    }

    public static void deconstruct(Method method, ProgramIr programIr) {
        if (CLI.debug)
            Utils.printSsaCfg(List.of(method), "ssa_after_opt_" + method.methodName());

        var entryBlock = method.entryBlock;
        var basicBlocks = TarjanSCC.getReversePostOrder(entryBlock);
        var immediateDominator = new DominatorTree(entryBlock);
        verify(basicBlocks);
        deconstructSsa(entryBlock, basicBlocks, immediateDominator);
        Coalesce.doIt(method, programIr);
        if (CLI.debug)
            Utils.printSsaCfg(List.of(method), "ssa_after_" + method.methodName());
    }

    private static Set<BasicBlock> getBasicBlocksModifyingVariable(IrRegister V, List<BasicBlock> basicBlocks) {
        return basicBlocks.stream()
                .filter(basicBlock -> basicBlock.getStoreInstructions()
                        .stream()
                        .map(StoreInstruction::getDestination)
                        .anyMatch(abstractName -> abstractName.equals(V)))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<IrRegister> getStoreLocations(BasicBlock X) {
        return X.getStoreInstructions()
                .stream()
                .map(StoreInstruction::getDestination)
                .filter(lValue -> lValue instanceof IrRegister)
                .map(lValue -> (IrRegister) lValue)
                .map(IrRegister::copy)
                .collect(Collectors.toSet());
    }

    private static void initialize(Set<IrRegister> allVariables, Map<IrRegister, Stack<Integer>> stacks, Map<IrRegister, Integer> counters) {
        allVariables.stream()
                .map(IrValue::copy)
                .map(name -> (IrRegister) name)
                .forEach(
                        a -> {
                            counters.put(a, 0);
                            stacks.put(a, new Stack<>());
                            stacks.get(a)
                                    .add(0);
                        });
    }

    private static void genName(IrRegister V, Map<IrRegister, Integer> counters, Map<IrRegister, Stack<Integer>> stacks) {
        var i = counters.get(V);
        var copyV = (IrRegister) V.copy();
        V.renameForSsa(i);
        stacks.get(copyV)
                .push(i);
        counters.put(copyV, i + 1);
    }

    private static void rename(BasicBlock X, DominatorTree dominatorTree, Map<IrRegister, Integer> counters, Map<IrRegister, Stack<Integer>> stacks) {
        // rename all phi nodes
        final var stores = getStoreLocations(X);

        for (Phi phi : X.getPhiFunctions()) {
            genName((IrRegister) phi.getDestination(), counters, stacks);
        }

        for (Instruction instruction : X.getInstructionList()) {
            if (instruction instanceof Phi)
                continue;
            if (instruction instanceof HasOperand hasOperand) {
                var Vs = hasOperand.getOperandVirtualRegisters();
                for (var V : Vs) {
                    if (stacks.get(V) == null)
                        System.out.println();
                    V.renameForSsa(stacks.get(V)
                            .peek());
                }
            }
            if (instruction instanceof StoreInstruction) {
                var V = ((StoreInstruction) instruction).getDestination();
                if (V instanceof IrRegister irRegister) {
                    genName(irRegister, counters, stacks);
                }
            }
        }

        for (var Y : X.getSuccessors()) {
            for (Phi phi : Y.getPhiFunctions()) {
                var V = phi.getVariableForB(X);
                if (V instanceof IrRegister irRegister) {
                    if (stacks.get(V)
                              .isEmpty())
                        continue;
                    irRegister.renameForSsa(stacks.get(V)
                                                  .peek());
                }
            }
        }

        for (var C : dominatorTree.getChildren(X)) {
            rename(C, dominatorTree, counters, stacks);
        }

        for (var store : stores) {
            stacks.get(store)
                    .pop();
        }
    }

    private static void initializeForSsaDestruction(List<BasicBlock> basicBlocks, HashMap<IrRegister, Stack<IrRegister>> stacks) {
        Utils.getAllVirtualRegistersInBasicBlocks(basicBlocks).stream()
                .map(IrRegister::copy)
                .forEach(a -> {
                            stacks.put(a, new Stack<>());
                            stacks.get(a)
                                    .add(a.copy());
                        }
                );
    }

    private static void addPhiNodeForVatY(IrRegister V, BasicBlock Y, Collection<BasicBlock> basicBlocksModifyingV) {
        int nCopiesOfV = (int) Y.getPredecessors()
                .stream()
                .filter(basicBlocksModifyingV::contains)
                .count();
        if (nCopiesOfV > 1) {
            var blockToVariable = new HashMap<BasicBlock, IrValue>();
            for (var P : Y.getPredecessors()) {
                blockToVariable.put(P, V.copy());
            }
            Y.getInstructionList()
                    .add(0, new Phi(V.copy(), blockToVariable));
        }
    }

    /**
     * Places phi functions to create a pruned SSA form
     *
     * @param entryBlock the first basic block of the function
     */
    private static void placePhiFunctions(BasicBlock entryBlock, List<BasicBlock> basicBlocks, DominatorTree dominatorTree) {
        var liveVariableAnalysis = new LiveVariableAnalysis(entryBlock);

        var allVariables = Utils.getAllVirtualRegistersInBasicBlocks(basicBlocks);
        for (var V : allVariables) {
            var hasAlready = new HashSet<BasicBlock>();
            var everOnWorkList = new HashSet<BasicBlock>();
            var workList = new ArrayDeque<BasicBlock>();

            var basicBlocksModifyingV = getBasicBlocksModifyingVariable(V, basicBlocks);
            for (var basicBlock : basicBlocksModifyingV) {
                everOnWorkList.add(basicBlock);
                workList.add(basicBlock);
            }
            while (!workList.isEmpty()) {
                var X = workList.pop();
                for (var Y : dominatorTree.getDominanceFrontier(X)) {
                    if (!hasAlready.contains(Y)) {
                        // we only insert a phi node for irAssignableValue V if is live on entry to X
                        if (liveVariableAnalysis.liveIn(X)
                                .contains(V)) {
                            addPhiNodeForVatY(V, Y, basicBlocksModifyingV);
                        }
                        hasAlready.add(Y);
                        if (!everOnWorkList.contains(Y)) {
                            everOnWorkList.add(Y);
                            workList.add(Y);
                        }
                    }
                }
            }
        }
    }

    private static void renameVariables(Method method, DominatorTree dominatorTree, List<BasicBlock> basicBlocks) {
        var stacks = new HashMap<IrRegister, Stack<Integer>>();
        var counters = new HashMap<IrRegister, Integer>();
        initialize(Utils.getAllVirtualRegistersInBasicBlocks(basicBlocks), stacks, counters);
        rename(method.entryBlock, dominatorTree, counters, stacks);
        renameMethodArgs(method);
    }

    private static void verify(List<BasicBlock> basicBlocks) {
        var seen = new HashSet<IrRegister>();
        for (var B : basicBlocks) {
            for (var store : B.getStoreInstructions()) {
                if (seen.contains(store.getDestination())) {
                    throw new IllegalStateException(store.getDestination() + " store redefined");
                } else {
                    if (store.getDestination() instanceof IrRegister irRegister) {
                        seen.add(irRegister);
                    }
                }
                if (store instanceof Phi) {
                    var s = store.getOperandValues()
                            .size();
                    if (s < 1) {
                        throw new IllegalStateException(store.syntaxHighlightedToString() + " has " + s + " operands");
                    }
                }
            }
        }
    }

    public static void verify(Method method) {
        var basicBlocks = TarjanSCC.getReversePostOrder(method.entryBlock);
        SSA.verify(basicBlocks);
    }

    private static void deconstructSsa(BasicBlock entryBlock, List<BasicBlock> basicBlocks, DominatorTree dominatorTree) {
        var stacks = new HashMap<IrRegister, Stack<IrRegister>>();
        initializeForSsaDestruction(basicBlocks, stacks);
        insertCopies(entryBlock, dominatorTree, new LiveVariableAnalysis(entryBlock), stacks);
        removePhiNodes(basicBlocks);
    }

    private static void removePhiNodes(List<BasicBlock> basicBlocks) {
        for (BasicBlock basicBlock : basicBlocks) {
            basicBlock.getInstructionList()
                    .reset(
                            basicBlock.getInstructionList()
                                    .stream()
                                    .filter(instruction -> !(instruction instanceof Phi))
                                    .collect(Collectors.toList())
                    );
        }
    }

    private static void insertCopies(BasicBlock basicBlock, DominatorTree dominatorTree, LiveVariableAnalysis liveVariableAnalysis, Map<IrRegister, Stack<IrRegister>> stacks) {
        var pushed = new ArrayList<IrRegister>();

        for (Instruction instruction : basicBlock.getInstructionList()) {
            if (instruction instanceof Phi)
                continue;
            if (instruction instanceof HasOperand) {
                var Vs = ((HasOperand) instruction).getOperandVirtualRegisters();
                for (var V : Vs) {
                    if (instruction instanceof StoreInstruction storeInstruction) {
                        if (storeInstruction.getDestination().equals(stacks.get(V).peek()))
                            continue;
                    }
                    V.renameForSsa(stacks.get(V)
                            .peek());
                }
            }
        }

        scheduleCopies(basicBlock, liveVariableAnalysis.liveOut(basicBlock), stacks, pushed);
        for (var child : dominatorTree.getChildren(basicBlock)) {
            insertCopies(child, dominatorTree, liveVariableAnalysis, stacks);
        }

        for (var name : pushed) {
            stacks.get(name)
                    .pop();
        }
        pushed.clear();
    }


    private static void scheduleCopies(BasicBlock basicBlock, Set<IrValue> liveOut, Map<IrRegister, Stack<IrRegister>> stacks, ArrayList<IrRegister> pushed) {
        /* Pass One: Initialize the data structures */
        Stack<Pair<IrValue, IrRegister>> copySet = new Stack<>();
        Stack<Pair<IrValue, IrRegister>> workList = new Stack<>();
        Map<IrValue, IrValue> map = new HashMap<>();
        Set<IrValue> usedByAnother = new HashSet<>();
        Map<IrValue, BasicBlock> phiDstToOwnerBasicBlockMapping = new HashMap<>();

        for (var successor : basicBlock.getSuccessors()) {
            for (var phi : successor.getPhiFunctions()) {
                var src = phi.getVariableForB(basicBlock);
                var dst = phi.getDestination();
                if (dst instanceof IrRegister dstVirtual) {
                    copySet.add(new Pair<>(src, dstVirtual));
                    map.put(src, src);
                    map.put(dst, dst);
                    usedByAnother.add(src);
                    phiDstToOwnerBasicBlockMapping.put(dst, successor);
                }
            }
        }


        /* Pass Two: Set up the workList of initial copies */
        for (var srcDest : new ArrayList<>(copySet)) {
            var dst = srcDest.second();
            if (!usedByAnother.contains(dst)) {
                workList.add(srcDest);
                copySet.remove(srcDest);
            }
        }

        /* Pass Three: Iterate over the workList, inserting copies */
        while (!workList.isEmpty() || !copySet.isEmpty()) {
            while (!workList.isEmpty()) {
                var srcDest = workList.pop();
                var src = srcDest.first();
                var dst = srcDest.second();
                if (liveOut.contains(dst) && !src.equals(dst)) {
                    var temp = (IrRegister) IrRegister.gen(dst.getType());
                    var copyInstruction = CopyInstruction.noAstConstructor(temp, dst.copy());
                    var dstOwner = Objects.requireNonNull(phiDstToOwnerBasicBlockMapping.get(dst), "dest " + dst + " does not have a source basic block");
                    dstOwner.getInstructionList()
                            .add(0, copyInstruction);
                    stacks.get(dst)
                            .push(temp);
                    stacks.put(temp, new Stack<>());
                    stacks.get(temp).add(temp.copy());
                    pushed.add(dst);
                }
                var copyInstruction = CopyInstruction.noAstConstructor(dst, map.get(src));
                basicBlock.addInstructionToTail(copyInstruction);
                map.put(src, dst);

                var subWorkList = getPairsWhoseDestinationEquals(src, copySet);
                workList.addAll(subWorkList);
                subWorkList.forEach(copySet::remove);
            }
            if (!copySet.isEmpty()) {
                var srcDest = copySet.pop();
                var dst = srcDest.second();
                var temp = IrRegister.gen(dst.getType());
                var copyInstruction = CopyInstruction.noAstConstructor(dst.copy(), temp);
                basicBlock.addInstructionToTail(copyInstruction);
                map.put(dst, temp);
                workList.add(srcDest);
            }
        }
    }

    private static List<Pair<IrValue, IrRegister>> getPairsWhoseDestinationEquals(IrValue src, Collection<Pair<IrValue, IrRegister>> pairs) {
        var results = new ArrayList<Pair<IrValue, IrRegister>>();
        for (var pair : pairs) {
            var dst = pair.second();
            if (dst.equals(src))
                results.add(pair);
        }
        return results;
    }

}
