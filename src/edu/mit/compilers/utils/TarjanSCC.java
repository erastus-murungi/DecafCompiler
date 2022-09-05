package edu.mit.compilers.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;

import edu.mit.compilers.cfg.BasicBlock;

public class TarjanSCC {

    public static List<BasicBlock> getReversePostOrder(BasicBlock entryPoint) {
        List<List<BasicBlock>> stronglyConnectedComponents = findStronglyConnectedComponents(entryPoint);
        Collections.reverse(stronglyConnectedComponents);
        return stronglyConnectedComponents
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    public static void correctPredecessors(BasicBlock block) {
        List<BasicBlock> basicBlocks = allNodes(block);

        for (BasicBlock basicBlock : basicBlocks)
            basicBlock.getPredecessors().clear();

        for (BasicBlock basicBlock : basicBlocks) {
            for (BasicBlock successor : basicBlock.getSuccessors()) {
                successor.addPredecessor(basicBlock);
            }
        }
    }

    public static List<BasicBlock> allNodes(BasicBlock entryPoint) {
        HashSet<BasicBlock> seen = new HashSet<>();
        Stack<BasicBlock> toExplore = new Stack<>();
        toExplore.add(entryPoint);
        while (!toExplore.isEmpty()) {
            BasicBlock block = toExplore.pop();
            if (seen.contains(block))
                continue;
            seen.add(block);
            toExplore.addAll(block.getSuccessors());
        }
        return new ArrayList<>(seen);
    }

    public static List<List<BasicBlock>> findStronglyConnectedComponents(BasicBlock entryPoint) {
        var basicBlocks = allNodes(entryPoint);
        correctPredecessors(entryPoint);
        return tarjan(basicBlocks);
    }

    private static List<List<BasicBlock>> tarjan(List<BasicBlock> blocks) {
        HashMap<BasicBlock, Integer> blockToLowLinkValue = new HashMap<>();
        HashMap<BasicBlock, Integer> blockToIndex = new HashMap<>();
        HashMap<BasicBlock, Boolean> blockOnStack = new HashMap<>();
        List<List<BasicBlock>> stronglyConnectedComponents = new ArrayList<>();

        var index = 0;
        var blocksToExplore = new Stack<BasicBlock>();
        for (BasicBlock block : blocks) {
            if (!blockToIndex.containsKey(block)) {
                index = strongConnect(block, blockToIndex, blockOnStack, blockToLowLinkValue, blocksToExplore, stronglyConnectedComponents, index);
            }
        }
        return stronglyConnectedComponents;

    }

    private static Integer strongConnect(BasicBlock block,
                                         HashMap<BasicBlock, Integer> blockToIndex,
                                         HashMap<BasicBlock, Boolean> blockOnStack,
                                         HashMap<BasicBlock, Integer> blockToLowLinkValue,
                                         Stack<BasicBlock> blocksToExplore,
                                         List<List<BasicBlock>> strongConnectedComponentsList,
                                         Integer index) {
        blockToIndex.put(block, index);
        blockToLowLinkValue.put(block, index);
        index = index + 1;
        blocksToExplore.push(block);
        blockOnStack.put(block, true);

        for (BasicBlock successor : block.getSuccessors()) {
            if (!blockToIndex.containsKey(successor)) {
                index = strongConnect(successor, blockToIndex, blockOnStack, blockToLowLinkValue, blocksToExplore, strongConnectedComponentsList, index);
                blockToLowLinkValue.put(block, Math.min(blockToLowLinkValue.get(block), blockToLowLinkValue.get(successor)));
            } else if (blockOnStack.get(successor)) {
                blockToLowLinkValue.put(block, Math.min(blockToLowLinkValue.get(block), blockToIndex.get(successor)));
            }
        }
        if (blockToLowLinkValue
                .get(block)
                .equals(blockToIndex.get(block))) {
            var stronglyConnectedComponent = new ArrayList<BasicBlock>();
            BasicBlock toAdd;
            do {
                toAdd = blocksToExplore.pop();
                blockOnStack.put(toAdd, false);
                stronglyConnectedComponent.add(toAdd);
            } while (!block.equals(toAdd));
            strongConnectedComponentsList.add(stronglyConnectedComponent);
        }
        return index;
    }
}
