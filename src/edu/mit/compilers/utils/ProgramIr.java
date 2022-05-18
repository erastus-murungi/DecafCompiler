package edu.mit.compilers.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import edu.mit.compilers.ast.MethodDefinition;
import edu.mit.compilers.codegen.InstructionList;
import edu.mit.compilers.codegen.TraceScheduler;
import edu.mit.compilers.codegen.codes.GlobalAllocation;
import edu.mit.compilers.codegen.codes.Instruction;
import edu.mit.compilers.codegen.codes.MethodBegin;
import edu.mit.compilers.codegen.names.AbstractName;
import edu.mit.compilers.codegen.names.AssignableName;
import edu.mit.compilers.codegen.names.MemoryAddressName;

public class ProgramIr {
    public InstructionList headerInstructions;
    public List<MethodBegin> methodBeginList;
    Set<AbstractName> globals;

    public ProgramIr(InstructionList headerInstructions, List<MethodBegin> methodBeginList) {
        this.headerInstructions = headerInstructions;
        this.methodBeginList = methodBeginList;
    }

    public int getSizeOfHeaderInstructions() {
        return headerInstructions.size();
    }

    public InstructionList mergeProgram() {
        var programHeader = headerInstructions.copy();
        var tacList = programHeader.copy();
        for (MethodBegin methodBegin : methodBeginList) {
            for (InstructionList instructionList: new TraceScheduler(methodBegin).getInstructionTrace())
                tacList.addAll(instructionList);
        }
        return tacList;
    }

    public void findGlobals() {
        globals = headerInstructions
                .stream()
                .filter(instruction -> instruction instanceof GlobalAllocation)
                .map(instruction -> (GlobalAllocation) instruction)
                .map(globalAllocation -> globalAllocation.variableName)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<AbstractName> getGlobals() {
        return globals;
    }

    public List<AbstractName> getLocals(MethodBegin methodBegin) {
        Set<AbstractName> uniqueNames = new HashSet<>();
        var flattened = TraceScheduler.flattenIr(methodBegin);

        for (Instruction instruction : flattened) {
            for (AbstractName name : instruction.getAllNames()) {
                if (name instanceof MemoryAddressName && !globals.contains(name)) {
                    uniqueNames.add(name);
                }
            }
        }

        for (Instruction instruction : flattened) {
            for (AbstractName name : instruction.getAllNames()) {
                if (!(name instanceof MemoryAddressName) && !globals.contains(name)) {
                    uniqueNames.add(name);
                }
            }
        }
        var locals =  uniqueNames
                .stream()
                .filter((name -> ((name instanceof AssignableName))))
                .distinct()
                .sorted(Comparator.comparing(Object::toString))
                .collect(Collectors.toList());
        reorderLocals(locals, methodBegin.methodDefinition);
        return locals;
    }


    private void reorderLocals(List<AbstractName> locals, MethodDefinition methodDefinition) {
        List<AbstractName> methodParametersNames = new ArrayList<>();

        Set<String> methodParameters = methodDefinition.methodDefinitionParameterList
                .stream()
                .map(methodDefinitionParameter -> methodDefinitionParameter.id.id)
                .collect(Collectors.toSet());

        List<AbstractName> methodParamNamesList = new ArrayList<>();
        for (AbstractName name : locals) {
            if (methodParameters.contains(name.toString())) {
                methodParamNamesList.add(name);
            }
        }
        for (AbstractName local : locals) {
            if (methodParameters.contains(local.toString())) {
                methodParametersNames.add(local);
            }
        }
        locals.removeAll(methodParametersNames);
        locals.addAll(0, methodParamNamesList
                .stream()
                .sorted(Comparator.comparing(AbstractName::toString))
                .collect(Collectors.toList()));
    }
}