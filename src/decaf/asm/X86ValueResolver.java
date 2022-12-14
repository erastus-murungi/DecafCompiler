package decaf.asm;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.lang.Math.min;

import com.google.common.collect.Sets;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import decaf.asm.instructions.X64BinaryInstruction;
import decaf.asm.instructions.X64Instruction;
import decaf.asm.operands.X86ConstantValue;
import decaf.asm.operands.X86GlobalValue;
import decaf.asm.operands.X86MemoryAddress;
import decaf.asm.operands.X86MemoryAddressComputation;
import decaf.asm.operands.X86MemoryAddressInRegister;
import decaf.asm.operands.X86MemoryAddressInStack;
import decaf.asm.operands.X86RegisterMappedValue;
import decaf.asm.operands.X86StackMappedValue;
import decaf.asm.operands.X86Value;
import decaf.asm.types.X64BinaryInstructionType;
import decaf.codegen.codes.GetAddress;
import decaf.codegen.codes.GlobalAllocation;
import decaf.codegen.codes.Instruction;
import decaf.codegen.codes.Method;
import decaf.codegen.codes.StringConstantAllocation;
import decaf.codegen.names.IrConstant;
import decaf.codegen.names.IrGlobal;
import decaf.codegen.names.IrGlobalArray;
import decaf.codegen.names.IrGlobalScalar;
import decaf.codegen.names.IrIntegerConstant;
import decaf.codegen.names.IrMemoryAddress;
import decaf.codegen.names.IrRegisterAllocatable;
import decaf.codegen.names.IrSsaRegister;
import decaf.codegen.names.IrStackArray;
import decaf.codegen.names.IrValue;
import decaf.common.ProgramIr;
import decaf.common.Utils;
import decaf.regalloc.RegisterAllocator;

/**
 * Resolve names
 */
public class X86ValueResolver {
  @NotNull
  private final Map<Method, Map<IrValue, X86RegisterMappedValue>> registerMappedIrValues = new HashMap<>();
  @NotNull
  private final Map<IrConstant, X86ConstantValue> constants = new HashMap<>();
  @NotNull
  private final Map<IrValue, X86GlobalValue> globals = new HashMap<>();
  @NotNull
  private final Map<Method, Map<IrValue, Integer>> stackOffsets = new HashMap<>();
  @NotNull
  private final Map<X86Value, Integer> temporarySaveLocations = new HashMap<>();
  @NotNull
  private final Map<Method, Integer> largestStackOffset = new HashMap<>();
  @NotNull
  private final Map<Method, Map<IrSsaRegister, X86Value>> initialArgumentLocations = new HashMap<>();
  @NotNull
  private final Map<IrMemoryAddress, X86MemoryAddress> memoryAddresses = new HashMap<>();
  @NotNull
  private final RegisterAllocator registerAllocator;
  @NotNull
  private final List<X64Instruction> preparatoryInstructions = new ArrayList<>();
  @NotNull
  private Method currentMethod;
  @NotNull
  private X86Method currentX86Method;
  private int currentStackOffset = 0;


  public X86ValueResolver(
      @NotNull ProgramIr programIr,
      @NotNull RegisterAllocator registerAllocator
  ) {
    currentMethod = programIr.getMethods()
                             .get(0);
    currentX86Method = new X86Method();
    this.registerAllocator = registerAllocator;
    mapGlobalsToX64Operands(programIr);
    mapLocalsToX64Operand(
        programIr,
        registerAllocator
    );
  }

  public boolean parameterUsedInCurrentMethod(@NotNull IrValue irValue) {
    return registerAllocator.getVariableToRegisterMap()
                            .get(currentMethod)
                            .containsKey(irValue);
  }

  public void setCurrentMethod(@NotNull Method currentMethod) {
    this.currentMethod = currentMethod;
  }

  public void setCurrentX64Method(@NotNull X86Method currentX86Method) {
    this.currentX86Method = currentX86Method;
  }

  private void mapLocalsToX64Operand(
      ProgramIr programIr,
      RegisterAllocator registerAllocator
  ) {
    for (var method : programIr.getMethods()) {
      prepareForMethod(
          method,
          currentX86Method
      );
      mapParametersToLocations(
          method,
          registerAllocator
      );
      mapLocalToX64OperandsForMethod(
          method,
          registerAllocator
      );
    }
  }

  private void mapLocalToX64OperandsForMethod(
      @NotNull Method method,
      @NotNull RegisterAllocator registerAllocator
  ) {
    var methodRegisterMapping = registerAllocator.getVariableToRegisterMap()
                                                 .get(method);
    var liveIntervals = registerAllocator.getLiveIntervalsManager()
                                         .getLiveIntervals(method);
    liveIntervals.forEach(liveInterval -> {
      var irAssignableValue = liveInterval.irSsaRegister();
      if (irAssignableValue instanceof IrRegisterAllocatable) {
        var dest = methodRegisterMapping.get(irAssignableValue);
        if (dest != null && !dest.equals(X86Register.STACK)) {
          registerMappedIrValues.get(method)
                                .put(
                                    irAssignableValue,
                                    new X86RegisterMappedValue(
                                        dest,
                                        irAssignableValue
                                    )
                                );
        } else {
          var alreadyInDestination = initialArgumentLocations.get(method)
                                                             .get(irAssignableValue);
          if (alreadyInDestination instanceof X86StackMappedValue x86StackMappedValue) {
            stackOffsets.get(method)
                        .put(
                            irAssignableValue,
                            x86StackMappedValue.getOffset()
                        );
          } else {
            stackOffsets.get(method)
                        .put(
                            irAssignableValue,
                            pushStack()
                        );
          }
        }
      } else if (irAssignableValue instanceof IrGlobalScalar) {
      } else {
        throw new IllegalStateException(irAssignableValue.getClass()
                                                         .getName() + " " + irAssignableValue);
      }
    });
    mapIrStackArraysToStack(method);
    largestStackOffset.put(
        method,
        currentStackOffset
    );
  }

  private void mapIrStackArraysToStack(@NotNull Method method) {
    ProgramIr.getIrStackArrays(method)
             .forEach(irStackArray -> {
               int offset = -Utils.roundUp16(Math.abs(pushStack((int) irStackArray.getNumElements())));
               stackOffsets.get(method)
                           .put(
                               irStackArray,
                               offset
                           );
               currentStackOffset = offset;
             });
  }

  @NotNull
  private Map<IrSsaRegister, X86Value> getInitialArgumentLocations() {
    return initialArgumentLocations.get(currentMethod);
  }

  @NotNull
  private X86ConstantValue resolveIrConstantValue(@NotNull IrConstant irConstant) {
    return checkNotNull(constants.computeIfAbsent(
        irConstant,
        k -> new X86ConstantValue(irConstant)
    ));
  }

  boolean isUnResolvedArgumentIrValue(@NotNull IrValue irValue) {
    return irValue instanceof IrSsaRegister argumentIrRegister &&
        getInitialArgumentLocations().containsKey(argumentIrRegister);
  }

  @NotNull
  private X86Value localizeArgument(@NotNull IrSsaRegister argument) {
    var initialArgumentLocation = getInitialArgumentLocations().remove(argument);
    var localizedArgumentX86Value = resolveIrValueInternal(argument);
    if (!localizedArgumentX86Value.equals(initialArgumentLocation)) {
      preparatoryInstructions.add(new X64BinaryInstruction(
          X64BinaryInstructionType.movq,
          initialArgumentLocation,
          localizedArgumentX86Value
      ));
    }
    return localizedArgumentX86Value;
  }

  private void spillValuesToStack(
      @NotNull X86Register x86Register,
      @NotNull Collection<IrValue> valuesToSpill
  ) {
    valuesToSpill.forEach(irAssignableValue -> {
      var spillLocation = new X86StackMappedValue(
          X86Register.RBP,
          pushStack()
      );
      preparatoryInstructions.add(new X64BinaryInstruction(
          X64BinaryInstructionType.movq,
          resolveIrValueInternal(irAssignableValue),
          spillLocation
      ));
      checkState(registerMappedIrValues.get(currentMethod)
                                       .remove(irAssignableValue)
                                       .getX64RegisterType()
                                       .equals(x86Register));
      checkState(!stackOffsets.get(currentMethod)
                              .containsKey(irAssignableValue));
      stackOffsets.get(currentMethod)
                  .put(
                      irAssignableValue,
                      spillLocation.getOffset()
                  );
    });
  }


  private void updateIrValueMappingTo(
      @NotNull IrValue irValue,
      @NotNull X86Register x86Register
  ) {
    var oldLocation = resolveIrValueInternal(irValue);
    var newLocation = new X86RegisterMappedValue(
        x86Register,
        irValue
    );
    preparatoryInstructions.add(new X64BinaryInstruction(
        X64BinaryInstructionType.movq,
        oldLocation,
        newLocation
    ));
    registerMappedIrValues.get(currentMethod)
                          .remove(irValue);
    stackOffsets.get(currentMethod)
                .remove(irValue);
    registerMappedIrValues.get(currentMethod)
                          .put(
                              irValue,
                              newLocation
                          );
  }

  private Set<IrValue> getVariablesInLiveIntervalOf(@NotNull IrValue irValue) {
    if (irValue instanceof IrIntegerConstant) {
      return Collections.emptySet();
    } else {
      return Utils.genRegAllocatableValuesFromInstructions(registerAllocator.getLiveIntervalsManager()
                                                                            .genInstructionsInLiveIntervalOf(
                                                                                irValue,
                                                                                currentMethod
                                                                            ));
    }
  }

  public void processGetAddress(
      @NotNull GetAddress getAddress
  ) {
    X86MemoryAddressComputation address;
    X86MemoryAddress destination;
    X86RegisterMappedValue baseRegister, indexRegister = null;
    if (isRegisterMappedIrValue(getAddress.getDestination())) {
      destination = new X86MemoryAddressInRegister(resolveRegisterMappedIrValue(getAddress.getDestination()));
    } else {
      destination = new X86MemoryAddressInStack(resolveStackMappedIrValue(getAddress.getDestination()));
    }
    // get base index
    var indexValue = resolveIrValueInternal(getAddress.getIndex());
    if (indexValue instanceof X86StackMappedValue || indexValue instanceof X86MemoryAddressComputation ||
        (getAddress.getBaseAddress() instanceof IrGlobalArray && !(indexValue instanceof X86RegisterMappedValue))) {
      // we should spill
      // try to avoid base register if possible
      var toAvoid = new ArrayList<X86Register>();
      if (isRegisterMappedIrValue(getAddress.getBaseAddress()))
        toAvoid.add(getRegisterOrFail(getAddress.getBaseAddress()));

      indexRegister = new X86RegisterMappedValue(
          genRegisterToSpill(
              getAddress.getIndex(),
              toAvoid
          ),
          getAddress.getIndex()
      );
      indexValue = indexRegister;
    }
    if (getAddress.getBaseAddress() instanceof IrStackArray irStackArray) {
      var baseValue = resolveStackMappedIrValue(irStackArray);
      address = new X86MemoryAddressComputation(
          baseValue,
          indexValue
      );
      currentX86Method.add(new X64BinaryInstruction(
          X64BinaryInstructionType.leaq,
          address,
          destination
      ));
      return;
    } else {
      var baseValue = resolveIrValueInternal(getAddress.getBaseAddress());
      if (baseValue instanceof X86StackMappedValue) {
        // we should spill and avoid the index register
        baseRegister = new X86RegisterMappedValue(
            genRegisterToSpill(
                getAddress.getBaseAddress(),
                new ArrayList<>(
                    indexRegister == null ? Collections.emptyList(): List.of(indexRegister.getX64RegisterType()))
            ),
            getAddress.getBaseAddress()
        );
      } else {
        baseRegister = (X86RegisterMappedValue) baseValue;
      }
      if (getAddress.getBaseAddress() instanceof IrGlobalArray irGlobalArray) {
        currentX86Method.add(new X64BinaryInstruction(
            X64BinaryInstructionType.movq,
            new X86GlobalValue(irGlobalArray),
            baseRegister
        ));
      }
      address = new X86MemoryAddressComputation(
          baseRegister,
          indexValue
      );
      memoryAddresses.put(
          getAddress.getDestination(), destination
      );
    }
    currentX86Method.add(new X64BinaryInstruction(
        X64BinaryInstructionType.leaq,
        address,
        destination.getWrapped()
    ));
  }

  private X86Register genRegisterToSpill(
      @NotNull IrValue irValue,
      ArrayList<X86Register> toAvoid
  ) {
    final var liveValuesInInterval = getVariablesInLiveIntervalOf(irValue);
    final var m = new HashMap<X86Register, Set<IrValue>>();
    for (var irSsaRegister : liveValuesInInterval) {
      var location = registerMappedIrValues.get(currentMethod)
                                           .get(irSsaRegister);
      if (location != null) m.computeIfAbsent(
                                 location.getX64RegisterType(),
                                 k -> new HashSet<>()
                             )
                             .add(irSsaRegister);
    }
    for (var register : X86Register.regsToAllocate) {
      if (!m.containsKey(register)) {
        m.put(
            register,
            new HashSet<>()
        );
      }
    }
    toAvoid.add(X86Register.STACK);
    // get the one with the
    var fewestReferences = 10000;
    var reg = X86Register.STACK;
    for (var register : m.keySet()) {
      if (toAvoid.contains(register)) continue;
      if (m.get(register)
           .size() < fewestReferences) {
        fewestReferences = m.get(register)
                            .size();
        reg = register;
      }
    }
    checkState(reg != X86Register.STACK);
    spillValuesToStack(
        reg,
        m.get(reg)
    );
    updateIrValueMappingTo(
        irValue,
        reg
    );
    return reg;
  }

  private void addTransientInstruction() {

  }

  public boolean isStackMappedIrValue(@NotNull IrValue irValue) {
    return irValue instanceof IrSsaRegister && stackOffsets.get(currentMethod)
                                                           .containsKey(irValue);
  }

  public boolean isRegisterMappedIrValue(@NotNull IrValue irValue) {
    return irValue instanceof IrRegisterAllocatable && registerMappedIrValues.get(currentMethod)
                                                                     .containsKey(irValue);
  }

  @NotNull
  public X86Register getRegisterOrFail(@NotNull IrValue irValue) {
    return registerMappedIrValues.get(currentMethod)
                                 .get(irValue)
                                 .getX64RegisterType();
  }

  @NotNull
  private X86StackMappedValue resolveStackMappedIrValue(@NotNull IrValue irValue) {
    return new X86StackMappedValue(
        X86Register.RBP,
        checkNotNull(stackOffsets.get(currentMethod)
                                 .get(irValue)),
        irValue
    );
  }


  public List<X64Instruction> getPreparatoryInstructions() {
    return List.copyOf(preparatoryInstructions);
  }

  @NotNull
  public X86Value resolveIrValue(
      @NotNull IrValue irValue,
      boolean updateMethodX86InstructionList
  ) {
    preparatoryInstructions.clear();
    var initialMethodLength = currentX86Method.size();
    var resolved = resolveIrValueInternal(irValue);
    checkState(currentX86Method.size() == initialMethodLength);
    if (updateMethodX86InstructionList) {
      currentX86Method.addAll(preparatoryInstructions);
    }
    return resolved;
  }

  @NotNull
  private X86Value resolveIrValueInternal(@NotNull IrValue irValue) {
    if (isUnResolvedArgumentIrValue(irValue)) {
      return localizeArgument((IrSsaRegister) irValue);
    }
    if (irValue instanceof IrConstant irConstant) {
      return resolveIrConstantValue(irConstant);
    }
    if (irValue instanceof IrMemoryAddress irMemoryAddress) {
      return resolveMemoryAddress(irMemoryAddress);
    }
    if (irValue instanceof IrGlobalScalar irGlobalScalar) {
      return resolveIrGlobal(irGlobalScalar);
    }
    if (isStackMappedIrValue(irValue)) {
      return resolveStackMappedIrValue(irValue);
    } else {
      return resolveRegisterMappedIrValue(irValue);
    }
  }

  @NotNull private X86Value resolveMemoryAddress(@NotNull IrMemoryAddress irMemoryAddress) {
    return memoryAddresses.get(irMemoryAddress);
  }

  @NotNull
  private X86RegisterMappedValue resolveRegisterMappedIrValue(@NotNull IrValue irValue) {
    checkState(irValue instanceof IrRegisterAllocatable);
    if (!isRegisterMappedIrValue(irValue)) throw new IllegalArgumentException(irValue.toString());
    return registerMappedIrValues.get(currentMethod)
                                 .get(irValue);
  }

  @NotNull
  private X86Value resolveIrGlobal(@NotNull IrGlobal irGlobal) {
    return globals.get((IrValue) irGlobal);
  }

  private void mapGlobalsToX64Operands(@NotNull ProgramIr programIr) {
    for (Instruction instruction : programIr.getPrologue()) {
      if (instruction instanceof GlobalAllocation globalAllocation) {
        if (globalAllocation.getValue() instanceof IrGlobalScalar) {
          globals.put(
              globalAllocation.getValue(),
              new X86GlobalValue(globalAllocation.getValue())
          );
        }
      } else if (instruction instanceof StringConstantAllocation stringConstantAllocation) {
        var stringConstant = stringConstantAllocation.getStringConstant();
        constants.put(
            stringConstant,
            new X86ConstantValue(stringConstant)
        );
      } else {
        throw new IllegalStateException(instruction.toString());
      }
    }
  }

  private int pushStack() {
    currentStackOffset = currentStackOffset - Utils.WORD_SIZE;
    return currentStackOffset;
  }

  private int pushStack(int n) {
    currentStackOffset = currentStackOffset - (Utils.WORD_SIZE * n);
    return currentStackOffset;
  }

  private void mapParametersToLocations(
      @NotNull Method method,
      @NotNull RegisterAllocator registerAllocator
  ) {
    // store all method args in unused registers and stack space if needed
    // the only registers we will ignore are the ones used to store the method args

    var unusedRegisters = getUnusedRegisters(
        currentMethod,
        registerAllocator
    );

    var destinations = new HashMap<IrSsaRegister, X86Value>();

    // next we save all the arguments to their corresponding locations
    for (int parameterIndex = 0; parameterIndex < method.getParameterNames()
                                                        .size(); parameterIndex++) {
      var parameter = method.getParameterNames()
                            .get(parameterIndex);

      if (parameterIndex < unusedRegisters.size()) {
        destinations.put(
            parameter,
            new X86RegisterMappedValue(
                unusedRegisters.get(parameterIndex),
                parameter
            )
        );
      } else {
        destinations.put(
            parameter,
            new X86StackMappedValue(
                X86Register.RBP,
                pushStack(),
                parameter
            )
        );
      }
    }
    largestStackOffset.put(
        method,
        currentStackOffset
    );
    initialArgumentLocations.put(
        method,
        destinations
    );
  }

  private List<X86Register> getUnusedRegisters(
      @NotNull Method method,
      @NotNull RegisterAllocator registerAllocator
  ) {
    var valueMappedRegisters = Set.copyOf(registerAllocator.getVariableToRegisterMap()
                                                           .get(method)
                                                           .values());
    var nUsedArgRegisters = min(
        6,
        method.getParameterNames()
              .size()
    );
    var usedArgRegisters = Set.copyOf(X86Register.argumentRegisters.subList(
        0,
        nUsedArgRegisters
    ));
    return List.copyOf(Sets.difference(
        Set.copyOf(X86Register.regsToAllocate),
        Sets.union(
            usedArgRegisters,
            valueMappedRegisters
        )
    ));
  }

  public void prepareForMethod(
      @NotNull Method method,
      @NotNull X86Method x86Method
  ) {
    setCurrentMethod(method);
    setCurrentX64Method(x86Method);
    currentStackOffset = largestStackOffset.getOrDefault(
        method,
        0
    );
    stackOffsets.computeIfAbsent(
        method,
        k -> new HashMap<>()
    );
    registerMappedIrValues.computeIfAbsent(
        method,
        k -> new HashMap<>()
    );
  }

  public X86Value resolveNextStackLocation(@NotNull X86Value x86Value) {
    if (temporarySaveLocations.containsKey(x86Value)) return new X86StackMappedValue(
        X86Register.RBP,
        temporarySaveLocations.get(x86Value)
    );
    else {
      var newLocation = pushStack();
      temporarySaveLocations.put(
          x86Value,
          newLocation
      );
      return new X86StackMappedValue(
          X86Register.RBP,
          newLocation
      );
    }
  }

  public X86StackMappedValue pushStackNoSave() {
    var newLocation = pushStack();
    return new X86StackMappedValue(
        X86Register.RBP,
        newLocation
    );
  }

  @NotNull
  public X86Value resolveInitialArgumentLocation(@NotNull IrSsaRegister irSsaRegister) {
    return initialArgumentLocations.get(currentMethod)
                                   .get(irSsaRegister);
  }

  public int getCurrentStackOffset() {
    return currentStackOffset;
  }
}
