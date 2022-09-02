package edu.mit.compilers.registerallocation;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import edu.mit.compilers.asm.X64Register;
import edu.mit.compilers.codegen.InstructionList;
import edu.mit.compilers.codegen.codes.Instruction;
import edu.mit.compilers.codegen.codes.Method;
import edu.mit.compilers.codegen.names.Value;
import edu.mit.compilers.utils.ProgramIr;

public class RegisterAllocation {
    public final Map<Method, Map<Value, X64Register>> variableToRegisterMap = new HashMap<>();

    public final Map<Method, Map<Instruction, Set<X64Register>>> methodToLiveRegistersInfo = new HashMap<>();

    private final InstructionList unModified;

    private Map<Instruction, Set<X64Register>> computeInstructionToLiveRegistersMap(ProgramIr programIr, List<LiveInterval> liveIntervals, Map<Value, X64Register> registerMap) {
        var instructionToLiveRegistersMap = new HashMap<Instruction, Set<X64Register>>();
        if (!liveIntervals.isEmpty()) {
            var instructionList = liveIntervals.get(0).instructionList();
            IntStream.range(0, instructionList.size())
                    .forEach(indexOfInstruction -> instructionToLiveRegistersMap.put(instructionList.get(indexOfInstruction),
                            getLiveRegistersAtPoint(programIr, liveIntervals, indexOfInstruction, registerMap)));
        }
        return instructionToLiveRegistersMap;
    }

    private Set<X64Register> getLiveRegistersAtPoint(ProgramIr programIr, Collection<LiveInterval> liveIntervals, int index, Map<Value, X64Register> registerMap) {
        return liveIntervals.stream()
                            .filter(liveInterval -> liveInterval.startPoint() <= index && liveInterval.endPoint() >= index)
                            .map(LiveInterval::variable)
                            .filter(name -> !programIr.getGlobals().contains(name))
                            .map(registerMap::get)
                            .collect(Collectors.toUnmodifiableSet());
    }

    private void computeMethodToLiveRegistersInfo(ProgramIr programIr, Map<Method, List<LiveInterval>> methodToLiveIntervalsMap) {
        methodToLiveIntervalsMap.forEach(((methodBegin, liveIntervals) ->
                methodToLiveRegistersInfo.put(
                        methodBegin,
                        computeInstructionToLiveRegistersMap(programIr, liveIntervals,
                                variableToRegisterMap.get(methodBegin))
                )));
    }

    public InstructionList getUnModified() {
        return unModified;
    }

    public RegisterAllocation(ProgramIr programIr) {
        this.unModified = programIr.mergeProgram();
        var liveIntervalsUtil = new LiveIntervals(programIr);
        var linearScan = new LinearScan(List.of(X64Register.regsToAllocate), liveIntervalsUtil.methodToLiveIntervalsMap);
        linearScan.allocate();
        variableToRegisterMap.putAll(linearScan.getVariableToRegisterMapping());
        computeMethodToLiveRegistersInfo(programIr, liveIntervalsUtil.methodToLiveIntervalsMap);
    }

    public Map<Method, Map<Value, X64Register>> getVariableToRegisterMap() {
        return variableToRegisterMap;
    }

    public Map<Method, Map<Instruction, Set<X64Register>>> getMethodToLiveRegistersInfo() {
        return methodToLiveRegistersInfo;
    }

}
