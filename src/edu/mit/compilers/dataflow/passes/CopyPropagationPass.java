package edu.mit.compilers.dataflow.passes;

import edu.mit.compilers.cfg.BasicBlock;
import edu.mit.compilers.codegen.ThreeAddressCodeList;
import edu.mit.compilers.codegen.codes.*;
import edu.mit.compilers.codegen.names.AbstractName;
import edu.mit.compilers.codegen.names.ArrayName;
import edu.mit.compilers.dataflow.analyses.AvailableCopies;
import edu.mit.compilers.dataflow.operand.BinaryOperand;
import edu.mit.compilers.dataflow.operand.Operand;
import edu.mit.compilers.dataflow.operand.UnmodifiedOperand;

import java.util.*;

public class CopyPropagationPass extends OptimizationPass {
    public CopyPropagationPass(Set<AbstractName> globalVariables, BasicBlock entryBlock) {
        super(globalVariables, entryBlock);
    }

    private static void propagateCopy(HasOperand hasOperand, HashMap<AbstractName, Operand> copies) {
        boolean converged = true;
        while (converged) {
            // we have to do this in a while loop because of how copy replacements propagate
            // for instance, lets imagine we have a = k
            // it possible that our copies map has y `replaces` k, and x `replaces` y and $0 `replaces` x
            // we want to eventually propagate so that a = $0
            converged = false;
            for (var toBeReplaced : hasOperand.getOperandNamesNoArray()) {
                if (copies.get(toBeReplaced) instanceof UnmodifiedOperand) {
                    var replacer = ((UnmodifiedOperand) copies.get(toBeReplaced)).abstractName;
                    hasOperand.replace(toBeReplaced, replacer);
                    // it is not enough to set `converged` to true if we have found our replacer
                    // we do this extra check, i.e the check : "!replacerName.equals(abstractName)"
                    // because our copies map sometimes contains entries like "x `replaces` x"
                    // without this check, we sometimes enter an infinite loop
                    if (!replacer.equals(toBeReplaced)) {
                        converged = true;
                    }
                }
            }
        }
    }

    private static ThreeAddressCode maybeReplaceWithBinaryOperand(HashMap<AbstractName, Operand> copies,
                                                  ThreeAddressCode threeAddressCode) {
        if (threeAddressCode instanceof Assign) {
            var assign = (Assign) threeAddressCode;
            var replacer = copies.get(assign.operand);
            if (replacer instanceof BinaryOperand) {
                return replacer.fromOperand(assign.dst);
            }
        }
        return threeAddressCode;
    }

    private boolean performLocalCopyPropagation(BasicBlock basicBlock, HashMap<AbstractName, Operand> copies) {
        // we need a reference tac list to know whether any changes occurred
        final var tacList = basicBlock.codes();
        final var newTacList = basicBlock.threeAddressCodeList;

        newTacList.getCodes()
                .clear();

        for (ThreeAddressCode threeAddressCode : tacList) {
            // we only perform copy propagation on instructions with variables
            if (threeAddressCode instanceof HasOperand) {
                propagateCopy((HasOperand) threeAddressCode, copies);
                threeAddressCode = maybeReplaceWithBinaryOperand(copies, threeAddressCode);
            }

            if (threeAddressCode instanceof HasResult) {
                var hasResult = (HasResult) threeAddressCode;
                var resultLocation = hasResult.getResultLocation();
                if (!(resultLocation instanceof ArrayName)) {
                    for (var assignableName : new ArrayList<>(copies.keySet())) {
                        var replacer = copies.get(assignableName);
                        if (assignableName.equals(resultLocation) ||
                                ((replacer instanceof UnmodifiedOperand) && ((UnmodifiedOperand) replacer).abstractName.equals(resultLocation))) {
                            // delete all assignments that are invalidated by this current instruction
                            // if it is an assignment
                            copies.remove(assignableName);
                        }
                    }
                    // insert this new pair into copies
                        var operand = hasResult.getComputationNoArrayNoGlobals(globalVariables);
                        operand.ifPresent(value -> copies.put(resultLocation, value));
                }
            }
            newTacList.addCode(threeAddressCode);
        }
        return newTacList
                .getCodes()
                .equals(tacList);
    }

    public void performGlobalCopyPropagation() {
        var availableCopiesIn = new AvailableCopies(entryBlock).availableCopies;

        // remove all copy instructions which involve global variables
        availableCopiesIn.forEach(((basicBlock, assigns) -> new ArrayList<>(assigns
                .keySet())
                .forEach(resultLocation -> {
                    if (globalVariables.contains(resultLocation)) {
                        assigns.remove(resultLocation);
                    }
                })));

        // perform copy propagation until no more changes are observed
        var notConverged = true;
        while (notConverged) {
            notConverged = false;
            for (BasicBlock basicBlock : basicBlocks) {
                if (!performLocalCopyPropagation(basicBlock, availableCopiesIn.get(basicBlock))) {
                    notConverged = true;
                }
            }
        }
    }

    @Override
    public boolean run() {
        final var oldCodes = entryBlock.codes();
        performGlobalCopyPropagation();
        return oldCodes.equals(entryBlock.threeAddressCodeList.getCodes());
    }
}