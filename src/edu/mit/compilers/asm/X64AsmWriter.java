package edu.mit.compilers.asm;

import static com.google.common.base.Preconditions.checkNotNull;
import static edu.mit.compilers.utils.Utils.WORD_SIZE;

import com.google.common.collect.Sets;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import edu.mit.compilers.asm.instructions.X64BinaryInstruction;
import edu.mit.compilers.asm.instructions.X64Instruction;
import edu.mit.compilers.asm.instructions.X64MetaData;
import edu.mit.compilers.asm.instructions.X64NoOperandInstruction;
import edu.mit.compilers.asm.instructions.X64UnaryInstruction;
import edu.mit.compilers.asm.operands.X64CallOperand;
import edu.mit.compilers.asm.operands.X64Constant;
import edu.mit.compilers.asm.operands.X64GlobalOperand;
import edu.mit.compilers.asm.operands.X64JumpTargetOperand;
import edu.mit.compilers.asm.operands.X64Operand;
import edu.mit.compilers.asm.operands.X64RegisterOperand;
import edu.mit.compilers.asm.operands.X64StackOperand;
import edu.mit.compilers.asm.types.X64BinaryInstructionType;
import edu.mit.compilers.asm.types.X64NopInstructionType;
import edu.mit.compilers.asm.types.X64UnaryInstructionType;
import edu.mit.compilers.ast.Type;
import edu.mit.compilers.codegen.TraceScheduler;
import edu.mit.compilers.codegen.codes.ArrayBoundsCheck;
import edu.mit.compilers.codegen.codes.BinaryInstruction;
import edu.mit.compilers.codegen.codes.ConditionalBranch;
import edu.mit.compilers.codegen.codes.CopyInstruction;
import edu.mit.compilers.codegen.codes.FunctionCall;
import edu.mit.compilers.codegen.codes.FunctionCallNoResult;
import edu.mit.compilers.codegen.codes.FunctionCallWithResult;
import edu.mit.compilers.codegen.codes.GetAddress;
import edu.mit.compilers.codegen.codes.GlobalAllocation;
import edu.mit.compilers.codegen.codes.Instruction;
import edu.mit.compilers.codegen.codes.Method;
import edu.mit.compilers.codegen.codes.MethodEnd;
import edu.mit.compilers.codegen.codes.ReturnInstruction;
import edu.mit.compilers.codegen.codes.RuntimeException;
import edu.mit.compilers.codegen.codes.StringConstantAllocation;
import edu.mit.compilers.codegen.codes.UnaryInstruction;
import edu.mit.compilers.codegen.codes.UnconditionalBranch;
import edu.mit.compilers.codegen.names.Constant;
import edu.mit.compilers.codegen.names.LValue;
import edu.mit.compilers.codegen.names.NumericalConstant;
import edu.mit.compilers.codegen.names.StringConstant;
import edu.mit.compilers.codegen.names.Value;
import edu.mit.compilers.codegen.names.Variable;
import edu.mit.compilers.registerallocation.RegisterAllocator;
import edu.mit.compilers.utils.Operators;
import edu.mit.compilers.utils.ProgramIr;

public class X64AsmWriter implements AsmWriter {
    @NotNull
    private final RegisterAllocator registerAllocator;
    @NotNull
    private final ProgramIr programIr;
    @NotNull
    private final Map<Method, Map<Value, X64Operand>> methodToX64OperandMapping = new HashMap<>();
    @NotNull
    private final Map<Method, Map<LValue, X64Operand>> paramToLocal = new HashMap<>();
    @NotNull
    private final List<X64Method> emittedMethods = new ArrayList<>();
    @NotNull
    private final List<X64Instruction> epilogue = new ArrayList<>();
    @NotNull
    private final List<X64Instruction> prologue = new ArrayList<>();
    @NotNull
    private final X64RegisterType COPY_TEMP_REGISTER = X64RegisterType.R10;
    @NotNull
    private final Map<Value, X64Operand> globalsMap = new HashMap<>();
    @NotNull
    private final Map<X64RegisterType, X64StackOperand> cache = new HashMap<>();
    @NotNull
    private final AsmWriterContext asmWriterContext = new AsmWriterContext();
    @NotNull
    private final Map<Method, Integer> methodToHighestStackLocation = new HashMap<>();
    @NotNull
    private X64Method x64Method = new X64Method();
    @Nullable
    private Instruction currentInstruction;
    @Nullable
    private Method currentMethod;
    private int stackLocation = 0;


    public X64AsmWriter(@NotNull ProgramIr programIr, @NotNull RegisterAllocator registerAllocator) {
        this.registerAllocator = registerAllocator;
        this.programIr = programIr;
        write();
    }

    private static int roundUp16(int n) {
        if (n == 0) return 16;
        return n >= 0 ? ((n + 16 - 1) / 16) * 16 : (n / 16) * 16;
    }

    private void write() {
        mapGlobalsToX64Operands();
        mapLocalsToX64Operand();
        emitProgramPrologue();
        emitMethods();
        emitProgramEpilogue();
    }

    private void emitProgramPrologue() {
        prologue.add(new X64MetaData(".data"));
        for (Instruction instruction : programIr.getPrologue()) {
            if (instruction instanceof StringConstantAllocation stringConstantAllocation) {
                prologue.add(new X64MetaData(stringConstantAllocation.getASM()));
            } else if (instruction instanceof GlobalAllocation globalAllocation) {
                prologue.add(new X64MetaData(String.format(".comm %s, %s, %s", globalAllocation.getValue(), globalAllocation.getSize(), 64)));
            } else {
                throw new IllegalStateException();
            }
        }
    }

    private void emitProgramEpilogue() {
        epilogue.add(new X64MetaData(".subsections_via_symbols"));
    }

    private void emitMethods() {
        for (var method : programIr.getMethods()) {
            emittedMethods.add(emitMethod(method));
        }
    }

    private X64Method emitMethod(@NotNull Method method) {
        currentMethod = method;
        x64Method = new X64Method();
        for (var instructionList : TraceScheduler.getInstructionTrace(method)) {
            if (!instructionList.isEntry() && !instructionList.getLabel()
                                                              .equals("UNSET")) {
                var label = instructionList.getLabel();
                x64Method.add(new X64MetaData(String.format(".%s:", label)));
            }
            for (var instruction : instructionList) {
                currentInstruction = instruction;
                instruction.accept(this);
            }
        }
        return x64Method;
    }

    private void mapGlobalsToX64Operands() {
        for (Instruction instruction : programIr.getPrologue()) {
            if (instruction instanceof GlobalAllocation globalAllocation) {
                globalsMap.put(globalAllocation.getValue(), new X64GlobalOperand(globalAllocation.getValue()));
            } else if (instruction instanceof StringConstantAllocation stringConstantAllocation) {
                var stringConstant = stringConstantAllocation.getStringConstant();
                globalsMap.put(stringConstant, new X64Constant(stringConstant));
            } else {
                throw new IllegalStateException();
            }
        }
    }

    private int pushStack() {
        stackLocation = stackLocation - WORD_SIZE;
        return stackLocation;
    }

    private void mapParametersToLocations(@NotNull Method method) {
        // store all method args in unused registers and stack space if needed
        // the only registers we will ignore are the ones used to store the method args

        var valueMappedRegisters = Set.copyOf(registerAllocator.getVariableToRegisterMap()
                                                               .get(method)
                                                               .values());
        var nUsedArgRegisters = Math.min(6, method.getParameterNames()
                                                  .size());
        var usedArgRegisters = Set.copyOf(X64RegisterType.parameterRegisters.subList(0, nUsedArgRegisters));
        var unusedRegisters = List.copyOf(Sets.difference(Set.copyOf(X64RegisterType.regsToAllocate), Sets.union(usedArgRegisters, valueMappedRegisters)));

        var destinations = new HashMap<LValue, X64Operand>();

        // next we save all the arguments to their corresponding locations
        for (int parameterIndex = 0; parameterIndex < method.getParameterNames()
                                                            .size(); parameterIndex++) {
            var parameter = method.getParameterNames()
                                  .get(parameterIndex);

            if (parameterIndex < unusedRegisters.size()) {
                destinations.put(parameter, new X64RegisterOperand(unusedRegisters.get(parameterIndex), parameter));
            } else {
                destinations.put(parameter, new X64StackOperand(X64RegisterType.RBP, pushStack(), parameter));
            }
        }
        methodToHighestStackLocation.put(method, stackLocation);
        paramToLocal.put(method, destinations);
    }

    private void mapLocalsToX64Operand() {
        for (var method : programIr.getMethods()) {
            mapParametersToLocations(method);
            mapLocalToX64OperandsForMethod(method);
        }
    }

    private void mapLocalToX64OperandsForMethod(@NotNull Method method) {
        var destinations = new HashMap<Value, X64Operand>();
        var methodRegisterMapping = registerAllocator.getVariableToRegisterMap()
                                                     .get(method);
        for (var instruction : TraceScheduler.flattenIr(method)) {
            for (var value : instruction.getAllValues()) {
                if (value instanceof Constant constant) {
                    destinations.put(value, new X64Constant(constant));
                } else if (value instanceof Variable variable) {
                    var dest = methodRegisterMapping.get(value);
                    if (dest != null && !dest.equals(X64RegisterType.STACK)) {
                        destinations.put(value, new X64RegisterOperand(dest, variable));
                    } else {
                        var alreadyInDestination = paramToLocal.get(method)
                                                               .get(value);
                        destinations.put(value, Objects.requireNonNullElseGet(alreadyInDestination, () -> new X64StackOperand(X64RegisterType.RBP, pushStack(), variable)));
                    }
                } else {
                    throw new IllegalStateException();
                }
            }
        }
        methodToX64OperandMapping.put(method, destinations);
    }

    public X64Program convert() {
        return new X64Program(prologue, epilogue, emittedMethods);
    }

    public X64Operand resolveName(@NotNull Value value) {
        checkNotNull(currentMethod);
        if (globalsMap.containsKey(value)) return globalsMap.get(value);

        var x64Operand = paramToLocal.get(currentMethod)
                                     .get(value);

        if (x64Operand != null) {
            paramToLocal.get(currentMethod)
                        .remove(value);
            // value hasn't been loaded yet

            // load it with an instruction
            // remove it from sources
            // add index to list of free stack locations

            x64Method.add(new X64BinaryInstruction(X64BinaryInstructionType.movq,
                    x64Operand,
                    methodToX64OperandMapping.get(currentMethod)
                                             .get(value)));
        }
        return checkNotNull(methodToX64OperandMapping.get(currentMethod)
                                                     .get(value));
    }

    public X64Operand resolveNextStackLocation(@NotNull X64RegisterType registerType) {
        if (cache.containsKey(registerType)) return cache.get(registerType);
        else {
            var newLocation = new X64StackOperand(X64RegisterType.RBP, pushStack());
            cache.put(registerType, newLocation);
            return newLocation;
        }
    }

    @NotNull
    private X64Operand getX64Operand(@NotNull Value value) {
        var arg = paramToLocal.get(currentMethod)
                              .get(value);
        if (arg != null) {
            return arg;
        }
        return checkNotNull(methodToX64OperandMapping.get(currentMethod)
                                                     .get(value));
    }

    private void calleeSave() {
        var toSave = X64RegisterType.calleeSaved;
        for (var register : toSave) {
            x64Method.addAtIndex(asmWriterContext.getLocationOfSubqInst(), new X64UnaryInstruction(X64UnaryInstructionType.pushq, X64RegisterOperand.unassigned(register)));
        }
    }

    private void calleeRestore() {
        var toRestore = new ArrayList<>(X64RegisterType.calleeSaved);
        for (var register : toRestore) {
            x64Method.addLine(new X64UnaryInstruction(X64UnaryInstructionType.popq, X64RegisterOperand.unassigned(register)));
        }
    }

    private void saveMethodArgsToLocations(@NotNull Method method) {
        // next we save all the arguments to their corresponding locations
        for (int parameterIndex = 0; parameterIndex < method.getParameterNames()
                                                            .size(); parameterIndex++) {
            var parameter = method.getParameterNames()
                                  .get(parameterIndex);

            var dst = paramToLocal.get(method)
                                  .get(parameter);

            if (parameterIndex < X64RegisterType.N_ARG_REGISTERS) {
                var src = new X64RegisterOperand(X64RegisterType.parameterRegisters.get(parameterIndex), parameter);
                x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, src, dst));
            } else {
                var src = new X64StackOperand(X64RegisterType.RBP, (parameterIndex - 5) * WORD_SIZE + 8, parameter);
                x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, src, new X64RegisterOperand(COPY_TEMP_REGISTER, parameter)));
                x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, X64RegisterOperand.unassigned(COPY_TEMP_REGISTER), dst));
            }
        }
        for (int parameterIndex = 0; parameterIndex < method.getParameterNames()
                                                            .size(); parameterIndex++) {
            var parameter = checkNotNull(method.getParameterNames()
                                               .get(parameterIndex));
            var x64Operand = checkNotNull(paramToLocal.get(method)
                                                      .get(parameter));
            var finalLoc = methodToX64OperandMapping.get(currentMethod)
                                                    .get(parameter);
            if (finalLoc == null) continue;
            x64Method.add(new X64BinaryInstruction(X64BinaryInstructionType.movq, x64Operand, finalLoc));
        }
    }

    private void callerRestore(@Nullable X64Operand returnAddressRegister) {
        var registerMapping = registerAllocator.getMethodToLiveRegistersInfo()
                                               .getOrDefault(currentMethod, Collections.emptyMap())
                                               .getOrDefault(currentInstruction, Collections.emptySet());
        var toRestore = registerMapping.stream()
                                       .filter(X64RegisterType.callerSaved::contains)
                                       .toList();
        for (var x64Register : toRestore) {
            if (x64Register.equals(X64RegisterType.STACK)) continue;
            var regOperand = X64RegisterOperand.unassigned(x64Register);
            if (!Objects.equals(returnAddressRegister, regOperand)) {
                var location = resolveNextStackLocation(x64Register);
                x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, location, regOperand));
            }
        }
    }

    private void callerSave(@Nullable X64Operand returnAddressRegister) {
        var registerMapping = registerAllocator.getMethodToLiveRegistersInfo()
                                               .getOrDefault(currentMethod, Collections.emptyMap())
                                               .getOrDefault(currentInstruction, Collections.emptySet());
        var toSave = registerMapping.stream()
                                    .filter(X64RegisterType.callerSaved::contains)
                                    .toList();
        int startIndex = x64Method.size();
        for (var x64Register : toSave) {
            if (x64Register.equals(X64RegisterType.STACK)) continue;
            var regOperand = X64RegisterOperand.unassigned(x64Register);
            if (!Objects.equals(returnAddressRegister, regOperand)) {
                var location = resolveNextStackLocation(x64Register);
                x64Method.addAtIndex(startIndex, new X64BinaryInstruction(X64BinaryInstructionType.movq, regOperand, location));
            }
        }
    }


    private List<X64Instruction> emitStackArgumentsInstructions(@NotNull FunctionCall functionCall) {
        var arguments = functionCall.getArguments();

        // instructions we will splice into the builder
        var instructions = new ArrayList<X64Instruction>();

        // we need to create stack space for the start arguments
        if (arguments.size() > 6) {
            var stackSpaceForArgs = new X64Constant(new NumericalConstant((long) roundUp16((arguments.size() - X64RegisterType.N_ARG_REGISTERS) * WORD_SIZE), Type.Int));
            x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.subq, stackSpaceForArgs, X64RegisterOperand.unassigned(X64RegisterType.RSP)));
        }
        for (int stackArgumentIndex = 6; stackArgumentIndex < arguments.size(); stackArgumentIndex++) {
            var stackArgument = resolveName(arguments.get(stackArgumentIndex));
            var dst = new X64StackOperand(X64RegisterType.RSP, (stackArgumentIndex - X64RegisterType.N_ARG_REGISTERS) * WORD_SIZE);
            if (stackArgument instanceof X64StackOperand) {
                instructions.add(new X64BinaryInstruction(X64BinaryInstructionType.movq, stackArgument, X64RegisterOperand.unassigned(COPY_TEMP_REGISTER)));
                instructions.add(new X64BinaryInstruction(X64BinaryInstructionType.movq, X64RegisterOperand.unassigned(COPY_TEMP_REGISTER), dst));
            } else {
                instructions.add(new X64BinaryInstruction(X64BinaryInstructionType.movq, stackArgument, dst));
            }
        }
        return instructions;
    }

    /**
     * Splices push arguments into the correct position in the XBuilder
     * while avoiding overwrites
     */
    private void orderFunctionCallArguments(@NotNull FunctionCall functionCall) {
        var arguments = functionCall.getArguments();

        // instructions we will splice into the builder
        var instructions = emitStackArgumentsInstructions(functionCall);
        // first let's copy the parameters we will push to the stack
        // this makes it easier to extract the most recent 6 parameters

        var copyOfPushArguments = new ArrayList<>(arguments);

        // extract the most recent pushes
        var argumentsToStoreInParameterRegisters = new ArrayList<>(copyOfPushArguments.subList(0, Math.min(X64RegisterType.N_ARG_REGISTERS, arguments.size())));

        // this is a map of a register to the argument it stores
        // note that the registers are represented as enums -> we use an EnumMap
        // note that X64Register.STACK is also included, so we need to consider it differently later
        var registerToResidentArgument = new EnumMap<X64RegisterType, Integer>(X64RegisterType.class);

        // we also have a reverse map to make looking an argument's register easier
        var residentArgumentToRegister = new HashMap<Integer, X64RegisterType>();

        // this is a map of arguments to the correct argument registers
        // we assign the arguments in the exact order, i.e RDI, RSI, RDX, RCX, R8, R9
        // this is why we use an indexOfArgument variable to loop through the argument registers
        // in the correct order
        var pushParameterX64RegisterMap = new HashMap<Integer, X64RegisterOperand>();

        // the index of the parameter register
        int indexOfParameterRegister = 0;
        for (Value argument : argumentsToStoreInParameterRegisters) {
            // argument register
            var parameterRegister = X64RegisterType.parameterRegisters.get(indexOfParameterRegister);

            // the register which houses this argument
            // if the mapping doesn't contain a register for this argument, we default to X64Register.STACK
            var residentRegister = resolveName(argument);
            if (residentRegister instanceof X64RegisterOperand x64RegisterOperand) {
                registerToResidentArgument.put(x64RegisterOperand.getX64RegisterType(), indexOfParameterRegister);
                residentArgumentToRegister.put(indexOfParameterRegister, x64RegisterOperand.getX64RegisterType());
            }

            pushParameterX64RegisterMap.put(indexOfParameterRegister, X64RegisterOperand.unassigned(parameterRegister));

            // march forward
            indexOfParameterRegister++;
        }

        // we create a set of parameter registers to make lookups more convenient
        var setOfParameterRegisters = Set.copyOf(X64RegisterType.parameterRegisters);

        // here we store arguments whose resident registers are the same as parameter registers
        // which could be potentially overwritten as we push arguments to parameter registers
        var potentiallyOverwrittenParameterRegisters = new HashMap<Value, X64RegisterType>();

        for (X64RegisterType x64RegisterType : registerToResidentArgument.keySet()) {
            if (setOfParameterRegisters.contains(x64RegisterType)) {
                potentiallyOverwrittenParameterRegisters.put(argumentsToStoreInParameterRegisters.get(registerToResidentArgument.get(x64RegisterType)), x64RegisterType);
                // this is the stack location storing the argument resident register
                var registerCache = resolveNextStackLocation(x64RegisterType);
                instructions.add(new X64BinaryInstruction(X64BinaryInstructionType.movq, X64RegisterOperand.unassigned(x64RegisterType), registerCache));
            }
        }

        Collections.reverse(argumentsToStoreInParameterRegisters);
        indexOfParameterRegister = argumentsToStoreInParameterRegisters.size() - 1;
        for (Value argument : argumentsToStoreInParameterRegisters) {
            var argumentResidentRegister = residentArgumentToRegister.getOrDefault(indexOfParameterRegister, X64RegisterType.STACK);
            // if the argument is stored in the stack, just move it from the stack to parameter register
            if (argumentResidentRegister == X64RegisterType.STACK) {
                if (argument instanceof StringConstant)
                    instructions.add(new X64BinaryInstruction(X64BinaryInstructionType.leaq, resolveName(argument), pushParameterX64RegisterMap.get(indexOfParameterRegister)));
                else {
                    instructions.add(new X64BinaryInstruction(X64BinaryInstructionType.movq, resolveName(argument), pushParameterX64RegisterMap.get(indexOfParameterRegister)));
                }
            } else {
                // if conflict might happen, then move from the register cache
                if (potentiallyOverwrittenParameterRegisters.containsKey(argument)) {
                    var parameterRegisterCache = resolveNextStackLocation(argumentResidentRegister);
                    instructions.add(new X64BinaryInstruction(X64BinaryInstructionType.movq, parameterRegisterCache, pushParameterX64RegisterMap.get(indexOfParameterRegister)));
                } else {
                    // just resolve the location, for arguments like string constants and constants
                    instructions.add(new X64BinaryInstruction(X64BinaryInstructionType.movq, X64RegisterOperand.unassigned(argumentResidentRegister), pushParameterX64RegisterMap.get(indexOfParameterRegister)));
                }
            }
            indexOfParameterRegister--;
        }
        x64Method.addAllAtIndex(x64Method.size(), instructions);

    }

    @Override
    public void emitInstruction(@NotNull FunctionCallWithResult functionCallWithResult) {
        callerSave(resolveName(functionCallWithResult.getDestination()));
        orderFunctionCallArguments(functionCallWithResult);
        if (functionCallWithResult.isImported()) {
            x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.xorl, X64RegisterOperand.unassigned(X64RegisterType.EAX), X64RegisterOperand.unassigned(X64RegisterType.EAX)));
            x64Method.addLine(new X64UnaryInstruction(X64UnaryInstructionType.callq, new X64CallOperand(functionCallWithResult)))
                     .addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, X64RegisterOperand.unassigned(X64RegisterType.RAX), resolveName(functionCallWithResult.getDestination())));
        } else {
            x64Method.addLine(new X64UnaryInstruction(X64UnaryInstructionType.callq, new X64CallOperand(functionCallWithResult)))
                     .addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, X64RegisterOperand.unassigned(X64RegisterType.RAX), resolveName(functionCallWithResult.getDestination())));
        }
        restoreStack(functionCallWithResult);
        callerRestore(resolveName(functionCallWithResult.getDestination()));
        asmWriterContext.setMaxStackSpaceForArgs(functionCallWithResult);
    }

    private void restoreStack(@NotNull FunctionCall functionCall) {
        if (functionCall.getNumArguments() > X64RegisterType.N_ARG_REGISTERS) {
            long numStackArgs = functionCall.getNumArguments() - X64RegisterType.N_ARG_REGISTERS;
            x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.addq, new X64Constant(new NumericalConstant((long) roundUp16((int) (numStackArgs * WORD_SIZE)), Type.Int)), X64RegisterOperand.unassigned(X64RegisterType.RSP)));
        }
    }

    @Override
    public void emitInstruction(@NotNull Method method) {
        asmWriterContext.setLastComparisonOperator(null);
        currentMethod = method;

        if (!asmWriterContext.isTextLabelAdded()) {
            x64Method.addLine(new X64MetaData(".text"));
            asmWriterContext.setTextLabelAdded();
        }

        if (method.isMain()) x64Method.addLine(new X64MetaData(".global _main"))
                                      .addLine(new X64MetaData(".p2align  4, 0x90"));

        if (method.isMain()) {
            x64Method.addLine(new X64MetaData("_main:"));
        } else {
            x64Method.addLine(new X64MetaData(method.methodName() + ":"));
        }

        stackLocation = methodToHighestStackLocation.get(currentMethod);
        asmWriterContext.setLocationOfSubqInst(x64Method.size());
        saveMethodArgsToLocations(method);
    }

    @Override
    public void emitInstruction(@NotNull ConditionalBranch conditionalBranch) {
        var resolvedCondition = resolveName(conditionalBranch.getCondition());
        if (asmWriterContext.getLastComparisonOperator() == null) {
            if (conditionalBranch.getCondition() instanceof NumericalConstant) {
                x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, resolvedCondition, X64RegisterOperand.unassigned(COPY_TEMP_REGISTER)))
                         .addLine(new X64BinaryInstruction(X64BinaryInstructionType.cmpq, new X64Constant(NumericalConstant.zero()), X64RegisterOperand.unassigned(COPY_TEMP_REGISTER)))
                         .addLine(new X64UnaryInstruction(X64UnaryInstructionType.je, new X64JumpTargetOperand(conditionalBranch.getTarget())));
            } else {
                x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.cmpq, new X64Constant(NumericalConstant.zero()), resolvedCondition))
                         .addLine(new X64UnaryInstruction(X64UnaryInstructionType.je, new X64JumpTargetOperand(conditionalBranch.getTarget())));
            }
            return;
        } else {
            x64Method.addLine(new X64UnaryInstruction(X64UnaryInstructionType.getCorrectJumpIfFalseInstruction(asmWriterContext.getLastComparisonOperator()), new X64JumpTargetOperand(conditionalBranch.getTarget())));
        }
        asmWriterContext.setLastComparisonOperator(null);
    }

    @Override
    public void emitInstruction(@NotNull FunctionCallNoResult functionCallNoResult) {
        callerSave(null);
        orderFunctionCallArguments(functionCallNoResult);
        if (functionCallNoResult.isImported())
            x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.xorl, X64RegisterOperand.unassigned(X64RegisterType.EAX), X64RegisterOperand.unassigned(X64RegisterType.EAX)))
                     .addLine(new X64UnaryInstruction(X64UnaryInstructionType.callq, new X64CallOperand(functionCallNoResult)));
        else
            x64Method.addLine(new X64UnaryInstruction(X64UnaryInstructionType.callq, new X64CallOperand(functionCallNoResult)));
        restoreStack(functionCallNoResult);
        callerRestore(null);
    }

    @Override
    public void emitInstruction(@NotNull MethodEnd methodEnd) {
        var space = new X64Constant(new NumericalConstant((long) roundUp16(-stackLocation), Type.Int));

        x64Method.addAtIndex(asmWriterContext.getLocationOfSubqInst(), new X64BinaryInstruction(X64BinaryInstructionType.subq, space, X64RegisterOperand.unassigned(X64RegisterType.RSP)));
        calleeSave();
        x64Method.addAtIndex(asmWriterContext.getLocationOfSubqInst(), new X64BinaryInstruction(X64BinaryInstructionType.movq, X64RegisterOperand.unassigned(X64RegisterType.RSP), X64RegisterOperand.unassigned(X64RegisterType.RBP)));
        x64Method.addAtIndex(asmWriterContext.getLocationOfSubqInst(), new X64UnaryInstruction(X64UnaryInstructionType.pushq, X64RegisterOperand.unassigned(X64RegisterType.RBP)));
        x64Method = (methodEnd.isMain() ? x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.xorl, X64RegisterOperand.unassigned(X64RegisterType.EAX), X64RegisterOperand.unassigned(X64RegisterType.EAX))) : x64Method);

        x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.addq, space, X64RegisterOperand.unassigned(X64RegisterType.RSP)));

        calleeRestore();
        x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, X64RegisterOperand.unassigned(X64RegisterType.RBP), X64RegisterOperand.unassigned(X64RegisterType.RSP)))
                 .addLine(new X64UnaryInstruction(X64UnaryInstructionType.popq, X64RegisterOperand.unassigned(X64RegisterType.RBP)));
        x64Method.addLine(new X64NoOperandInstruction(X64NopInstructionType.retq));
        stackLocation = 0;
    }

    @Override
    public void emitInstruction(@NotNull ReturnInstruction returnInstruction) {
        if (returnInstruction.getReturnAddress()
                             .isPresent())
            x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, resolveName(returnInstruction.getReturnAddress()
                                                                                                                   .get()), X64RegisterOperand.unassigned(X64RegisterType.RAX)));
    }

    @Override
    public void emitInstruction(@NotNull UnaryInstruction unaryInstruction) {
        var src = resolveName(unaryInstruction.operand);
        var dst = resolveName(unaryInstruction.getDestination());

        switch (unaryInstruction.operator) {
            case Operators.NOT -> {
                asmWriterContext.setLastComparisonOperator(null);
                x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, src, X64RegisterOperand.unassigned(COPY_TEMP_REGISTER)))
                         .addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, X64RegisterOperand.unassigned(COPY_TEMP_REGISTER), dst))
                         .addLine(new X64BinaryInstruction(X64BinaryInstructionType.xorq, new X64Constant(NumericalConstant.one()), dst));
            }
            case Operators.MINUS ->
                    x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, src, X64RegisterOperand.unassigned(COPY_TEMP_REGISTER)))
                             .addLine(new X64UnaryInstruction(X64UnaryInstructionType.neg, X64RegisterOperand.unassigned(COPY_TEMP_REGISTER)))
                             .addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, X64RegisterOperand.unassigned(COPY_TEMP_REGISTER), dst));
            default -> throw new IllegalStateException(unaryInstruction.toString());
        }
    }

    @Override
    public void emitInstruction(@NotNull UnconditionalBranch unconditionalBranch) {
        x64Method.addLine(new X64UnaryInstruction(X64UnaryInstructionType.jmp, new X64JumpTargetOperand(unconditionalBranch.getTarget())));
    }

    @Override
    public void emitInstruction(@NotNull ArrayBoundsCheck arrayBoundsCheck) {
    }

    @Override
    public void emitInstruction(@NotNull RuntimeException runtimeException) {
    }

    @Override
    public void emitInstruction(@NotNull CopyInstruction copyInstruction) {
        var sourceStackLocation = resolveName(copyInstruction.getValue());
        var destStackLocation = resolveName(copyInstruction.getDestination());

        if (!sourceStackLocation.equals(destStackLocation)) {
            if (sourceStackLocation instanceof X64RegisterOperand || sourceStackLocation instanceof X64Constant)
                x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, sourceStackLocation, destStackLocation));
            else
                x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, sourceStackLocation, X64RegisterOperand.unassigned(COPY_TEMP_REGISTER)))
                         .addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, X64RegisterOperand.unassigned(COPY_TEMP_REGISTER), destStackLocation));
        }
    }

    @Override
    public void emitInstruction(@NotNull GetAddress getAddress) {

    }

    @Override
    public void emitInstruction(@NotNull BinaryInstruction binaryInstruction) {
        var firstOperand = resolveName(binaryInstruction.fstOperand);
        var secondOperand = resolveName(binaryInstruction.sndOperand);
        var dst = resolveName(binaryInstruction.getDestination());

        switch (binaryInstruction.operator) {
            case Operators.PLUS, Operators.MINUS, Operators.MULTIPLY, Operators.CONDITIONAL_OR, Operators.CONDITIONAL_AND ->
                    x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, firstOperand, X64RegisterOperand.unassigned(COPY_TEMP_REGISTER)))
                             .addLine(new X64BinaryInstruction(X64BinaryInstructionType.getX64BinaryInstruction(binaryInstruction.operator), secondOperand, X64RegisterOperand.unassigned(COPY_TEMP_REGISTER)))
                             .addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, X64RegisterOperand.unassigned(COPY_TEMP_REGISTER), dst));
            case Operators.DIVIDE, Operators.MOD -> {
                // If we are planning to use RDX, we spill it first
                if (!resolveName(binaryInstruction.getDestination()).toString()
                                                                    .equals(X64RegisterType.RDX.toString()))
                    x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, X64RegisterOperand.unassigned(X64RegisterType.RDX), resolveNextStackLocation(X64RegisterType.RDX)));
                if (binaryInstruction.sndOperand instanceof NumericalConstant) {
                    x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, firstOperand, X64RegisterOperand.unassigned(X64RegisterType.RAX)))
                             .addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, resolveName(binaryInstruction.sndOperand), X64RegisterOperand.unassigned(COPY_TEMP_REGISTER)))
                             .addLine(new X64NoOperandInstruction(X64NopInstructionType.cqto))
                             .addLine(new X64UnaryInstruction(X64UnaryInstructionType.idivq, X64RegisterOperand.unassigned(COPY_TEMP_REGISTER)));
                } else {
                    x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, firstOperand, X64RegisterOperand.unassigned(X64RegisterType.RAX)))
                             .addLine(new X64NoOperandInstruction(X64NopInstructionType.cqto))
                             .addLine(new X64UnaryInstruction(X64UnaryInstructionType.idivq, secondOperand));
                }
                x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, X64RegisterOperand.unassigned(binaryInstruction.operator.equals("%") ? X64RegisterType.RDX : X64RegisterType.RAX), dst));
                // restore RDX
                if (!resolveName(binaryInstruction.getDestination()).toString()
                                                                    .equals(X64RegisterType.RDX.toString()))
                    x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, resolveNextStackLocation(X64RegisterType.RDX), X64RegisterOperand.unassigned(X64RegisterType.RDX)));
            }
            // comparison operators
            case Operators.EQ, Operators.NEQ, Operators.LT, Operators.GT, Operators.LEQ, Operators.GEQ -> {
                asmWriterContext.setLastComparisonOperator(binaryInstruction.operator);
                x64Method.addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, firstOperand, X64RegisterOperand.unassigned(COPY_TEMP_REGISTER)))
                         .addLine(new X64BinaryInstruction(X64BinaryInstructionType.cmpq, secondOperand, X64RegisterOperand.unassigned(COPY_TEMP_REGISTER)))
                         .addLine(new X64UnaryInstruction(X64UnaryInstructionType.getCorrectComparisonSetInstruction(binaryInstruction.operator), X64RegisterOperand.unassigned(X64RegisterType.al)))
                         .addLine(new X64BinaryInstruction(X64BinaryInstructionType.movzbq, X64RegisterOperand.unassigned(X64RegisterType.al), X64RegisterOperand.unassigned(COPY_TEMP_REGISTER)))
                         .addLine(new X64BinaryInstruction(X64BinaryInstructionType.movq, X64RegisterOperand.unassigned(COPY_TEMP_REGISTER), dst));
            }
            default -> throw new IllegalStateException(binaryInstruction.toString());
        }
    }
    // instruction visitors
}
