package edu.mit.compilers.registerallocation;

import static com.google.common.base.Preconditions.checkNotNull;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import edu.mit.compilers.cfg.BasicBlock;
import edu.mit.compilers.codegen.InstructionList;
import edu.mit.compilers.codegen.TraceScheduler;
import edu.mit.compilers.codegen.codes.GetAddress;
import edu.mit.compilers.codegen.codes.HasOperand;
import edu.mit.compilers.codegen.codes.Instruction;
import edu.mit.compilers.codegen.codes.Method;
import edu.mit.compilers.codegen.codes.StoreInstruction;
import edu.mit.compilers.codegen.names.IrGlobal;
import edu.mit.compilers.codegen.names.IrGlobalArray;
import edu.mit.compilers.codegen.names.IrValue;
import edu.mit.compilers.codegen.names.IrAssignableValue;
import edu.mit.compilers.codegen.names.IrMemoryAddress;
import edu.mit.compilers.dataflow.analyses.LiveVariableAnalysis;
import edu.mit.compilers.utils.ProgramIr;
import edu.mit.compilers.utils.TarjanSCC;
import edu.mit.compilers.utils.Utils;

public class LiveIntervalsUtil {
    /**
     * Maps each unique instruction in the program to a set of variables live at that point
     */
    public final Map<Instruction, Set<IrValue>> instructionToLiveVariablesMap = new HashMap<>();
    /**
     * Maps each method to a list of its live intervals
     */
    public final Map<Method, List<LiveInterval>> methodToLiveIntervalsMap = new HashMap<>();
    final private ProgramIr programIr;
    /**
     * Value to live intervals
     */

    private final Map<Method, Map<IrValue, LiveInterval>> methodToMappingOfValuesToLiveIntervals = new HashMap<>();
    private final Map<Instruction, Set<IrValue>> refCache = new ConcurrentHashMap<>();
    private final Map<Instruction, Set<IrValue>> defCache = new ConcurrentHashMap<>();

    public LiveIntervalsUtil(ProgramIr programIr) {
        this.programIr = programIr;
        computeLivenessInformation();
        computeLiveIntervals();
        validateLiveIntervals();
    }

    public LiveIntervalsUtil(@NotNull Method method, @NotNull ProgramIr programIr) {
        this.programIr = programIr;
        computeMethodLivenessInformation(method);
        var liveIntervals = computeMethodLiveIntervals(method);
        validateMethodLiveIntervals(liveIntervals);
        methodToLiveIntervalsMap.put(method, liveIntervals);
    }

    private static int findFirstDefSlot(InstructionList instructionList, IrValue variable) {
        int indexOfInstruction = 0;
        for (Instruction instruction : instructionList) {
            if (instruction.getAllValues()
                           .contains(variable)) break;
            indexOfInstruction++;
        }
        return indexOfInstruction;
    }

    public static boolean liveIntervalsInterfere(LiveInterval a, LiveInterval b) {
        var overlap = Math.abs(Math.min(a.startPoint(), b.endPoint() - 1) - Math.max(a.startPoint(), b.endPoint() - 1));
        return overlap > 0;
    }

    public static <T> HashSet<T> difference(Set<T> first, Set<T> second) {
        var firstCopy = new HashSet<>(first);
        firstCopy.removeAll(second);
        return firstCopy;
    }

    public static <T> HashSet<T> union(Set<T> first, Set<T> second) {
        var firstCopy = new HashSet<>(first);
        firstCopy.addAll(second);
        return firstCopy;
    }

    @NotNull private Map<IrValue, LiveInterval> fixArrayBaseRegisters(@NotNull Map<IrValue, LiveInterval> valueLiveIntervalMap) {
        var memoryRegisters = valueLiveIntervalMap.values()
                                                  .stream()
                                                  .filter(liveInterval -> liveInterval.irAssignableValue() instanceof IrMemoryAddress)
                                                  .collect(Collectors.groupingBy(liveInterval -> ((IrMemoryAddress) liveInterval.irAssignableValue()).getBaseAddress()));
        for (var baseAddress : memoryRegisters.keySet()) {
            var newEndpoint = memoryRegisters.get(baseAddress)
                                             .stream()
                                             .mapToInt(LiveInterval::endPoint)
                                             .max()
                                             .orElseThrow(() -> new IllegalStateException(memoryRegisters.get(baseAddress)
                                                                                                         .toString()));
            if (baseAddress instanceof IrGlobalArray) {
                valueLiveIntervalMap.put(baseAddress,
                    valueLiveIntervalMap.get(baseAddress)
                                        .updateEndpoint(newEndpoint));
            }
        }
        return valueLiveIntervalMap;
    }

    @NotNull
    public List<Instruction> getAllInstructionsInLiveIntervalOf(@NotNull IrValue irValue, @NotNull Method method) {
        var liveInterval = methodToMappingOfValuesToLiveIntervals.get(method)
                                                                 .get(irValue);
        checkNotNull(liveInterval);
        var instructionList = liveInterval.instructionList();
        return IntStream.range(liveInterval.startPoint(), liveInterval.endPoint())
                        .mapToObj(instructionList::get)
                        .toList();
    }

    @NotNull
    public Collection<LiveInterval> getLiveIntervals(@NotNull Method method) {
        return methodToLiveIntervalsMap.get(method);
    }

    @NotNull
    public Map<IrValue, LiveInterval> getValueToLiveIntervalMappingForMethod(@NotNull Method method) {
        return methodToMappingOfValuesToLiveIntervals.get(method);
    }

    private void validateMethodLiveIntervals(@NotNull Collection<LiveInterval> liveIntervals) {
        checkStartComesBeforeEnd(liveIntervals);
    }

    private void validateLiveIntervals() {
        programIr.getMethods()
                 .forEach(method -> validateMethodLiveIntervals(getLiveIntervals(method)));
    }

    private boolean isLiveAtPoint(Method method, IrValue irValue, int index) {
        var liveInterval = methodToMappingOfValuesToLiveIntervals.get(method)
                                                                 .get(irValue);
        return liveInterval.startPoint() <= index && index < liveInterval.endPoint();
    }

    private void computeMethodLivenessInformation(Method method) {
        var basicBlocks = TarjanSCC.getReversePostOrder(method.entryBlock);
        basicBlocks.forEach(basicBlock -> computeLiveOutForSetBasicBlock(basicBlock, new LiveVariableAnalysis(method.entryBlock)));
    }

    private Set<IrValue> ref(Instruction instruction) {
        if (instruction instanceof HasOperand hasOperand)
            return new HashSet<>(hasOperand.getAllRegisterAllocatableValues());
        return Collections.emptySet();
    }

    private Set<IrValue> def(Instruction instruction) {
        if (instruction instanceof GetAddress getAddress)
            return Set.of(getAddress.getBaseAddress(), getAddress.getDestination());
        if (instruction instanceof StoreInstruction storeInstruction) return Set.of(storeInstruction.getDestination());
        return Collections.emptySet();
    }

    private void computeLiveOutForSetBasicBlock(BasicBlock basicBlock, LiveVariableAnalysis liveVariableAnalysis) {
        var liveOut = liveVariableAnalysis.liveOut(basicBlock);

        basicBlock.getInstructionList()
                  .parallelStream()
                  .forEach(instruction -> {
                      defCache.put(instruction, def(instruction));
                      refCache.put(instruction, ref(instruction));
                  });

        var outLive = new HashSet<>(liveOut);

        // values which are live outside this block and therefore should be conserved
        var outGlobal = difference(outLive, difference(outLive, basicBlock.getStoreInstructions()
                                                                          .stream()
                                                                          .map(StoreInstruction::getDestination)
                                                                          .collect(Collectors.toUnmodifiableSet())));
        for (var instruction : basicBlock.getInstructionListReversed()) {
            outLive = union(union(difference(outLive, defCache.get(instruction)), refCache.get(instruction)), outGlobal);
            instructionToLiveVariablesMap.put(instruction, outLive);
        }
    }

    private void computeLivenessInformation() {
        for (Method method : programIr.getMethods()) {
            computeMethodLivenessInformation(method);
        }
    }

    private List<LiveInterval> computeMethodLiveIntervals(Method method) {
        var instructionList = TraceScheduler.flattenIr(method);
        var allVariables = Utils.getLAllRegisterAllocatableValuesInInstructionList(instructionList);

        var varToLiveInterval = new HashMap<IrValue, LiveInterval>();
        for (var variable : allVariables) {
            var defSlot = findFirstDefSlot(instructionList, variable);
            var lastUseSlot = findLastUseSlot(instructionList, variable);
            // handles the case when a irAssignableValue is defined but never used in the program
            if (lastUseSlot == 0) lastUseSlot = defSlot + 1;
            var liveInterval = new LiveInterval(variable, defSlot, lastUseSlot, instructionList, method);
            varToLiveInterval.put(variable, liveInterval);
        }
        methodToMappingOfValuesToLiveIntervals.put(method, fixArrayBaseRegisters(varToLiveInterval));
        return new ArrayList<>(methodToMappingOfValuesToLiveIntervals.get(method).values());
    }

    private void computeLiveIntervals() {
        for (var method : programIr.getMethods()) {
            methodToLiveIntervalsMap.put(method, computeMethodLiveIntervals(method));
        }
    }

    private int findLastUseSlot(InstructionList instructionList, IrValue irValue) {
        int loc = instructionList.size() - 1;
        if (irValue instanceof IrMemoryAddress || irValue instanceof IrGlobal) {
            for (; loc >= 0; loc--) {
                var instruction = instructionList.get(loc);
                if (instruction.getAllRegisterAllocatableValues()
                               .contains((IrAssignableValue) irValue)) return loc + 1;
            }
            throw new IllegalStateException();
        } else {
            for (; loc >= 0; loc--) {
                var instruction = instructionList.get(loc);
                if (instructionToLiveVariablesMap.getOrDefault(instruction, Collections.emptySet())
                                                 .contains(irValue)) break;
            }
            return loc + 1;
        }
    }

    private void checkStartComesBeforeEnd(@NotNull Collection<LiveInterval> liveIntervals) {
        for (var liveInterval : liveIntervals) {
            if (liveInterval.startPoint() > liveInterval.endPoint()) {
                throw new IllegalStateException(liveInterval.toString());
            }
        }
    }

    public void prettyPrintLiveIntervals(Method method) {
        var liveIntervals = getLiveIntervals(method);
        if (liveIntervals.isEmpty()) return;
        var instructionList = liveIntervals.stream()
                                           .findAny()
                                           .orElseThrow()
                                           .instructionList();
        var allVariables = liveIntervals.stream()
                                        .map(LiveInterval::irAssignableValue)
                                        .distinct()
                                        .toList();
        Map<IrValue, Integer> spaces = new HashMap<>();
        int spaceCount = 1;
        for (var variable : allVariables) {
            spaces.put(variable, spaceCount);
            System.out.print(variable);
            System.out.print(" | ");
            spaceCount = (variable.toString()
                                  .length() + 2);
        }
        System.out.println();
        int index = 0;
        for (var instruction : instructionList) {
            for (var variable : allVariables) {
                System.out.print(" ".repeat(spaces.get(variable)));
                if (isLiveAtPoint(method, variable, index)) {
                    System.out.print(Utils.coloredPrint("\u2588", Utils.ANSIColorConstants.ANSI_GREEN_BOLD));
                } else {
                    System.out.print(" ");
                }
            }
            System.out.format("    %3d:  ", index);
            System.out.println(instruction.syntaxHighlightedToString());
            index += 1;
        }
    }
}
