package edu.mit.compilers.ssa;

import static com.google.common.base.Preconditions.checkState;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import edu.mit.compilers.cfg.BasicBlock;
import edu.mit.compilers.asm.AsmWriter;
import edu.mit.compilers.codegen.codes.Instruction;
import edu.mit.compilers.codegen.codes.StoreInstruction;
import edu.mit.compilers.codegen.names.VirtualRegister;
import edu.mit.compilers.codegen.names.Value;
import edu.mit.compilers.dataflow.operand.Operand;
import edu.mit.compilers.dataflow.operand.PhiOperand;

public class Phi extends StoreInstruction {
    private final Map<BasicBlock, Value> basicBlockValueMap;

    public Phi(VirtualRegister v, Map<BasicBlock, Value> xs) {
        super(v, null);
        assert xs.size() > 2;
        basicBlockValueMap = xs;
    }

    public void removePhiOperand(Value value) {
        assert basicBlockValueMap.containsValue(value);
        for (var entrySet: basicBlockValueMap.entrySet()) {
            if (entrySet.getValue().equals(value)) {
                basicBlockValueMap.remove(entrySet.getKey());
                return;
            }
        }
        throw new IllegalStateException();
    }

    @NotNull public Value getVariableForB(@NotNull BasicBlock B) {
        var value =  basicBlockValueMap.get(B);
        checkState(value != null, this + "\nno variable for block: \n" + B);
        return value;
    }

    public void replaceBlock(@NotNull BasicBlock B, @NotNull BasicBlock R) {
        var ret = basicBlockValueMap.remove(B);
        checkState(ret != null);
        basicBlockValueMap.put(R, ret);
    }

    @NotNull public BasicBlock getBasicBlockForV(@NotNull Value value) {
        checkState(basicBlockValueMap.containsValue(value));
        for (var entrySet: basicBlockValueMap.entrySet()) {
            if (entrySet.getValue().equals(value))
                return entrySet.getKey();
        }
        throw new IllegalStateException();
    }

    @Override
    public Operand getOperand() {
        return new PhiOperand(this);
    }

    @Override
    public List<Value> getOperandValues() {
        return new ArrayList<>(basicBlockValueMap.values());
    }

    @Override
    public boolean replaceValue(Value oldName, Value newName) {
        var replaced = false;
        for (BasicBlock basicBlock : basicBlockValueMap.keySet()) {
            var abstractName = basicBlockValueMap.get(basicBlock);
            if (abstractName.equals(oldName)) {
                basicBlockValueMap.put(basicBlock, newName);
                replaced = true;
            }
        }
        return replaced;
    }

    @Override
    public void accept(AsmWriter asmWriter) {
    }

    @Override
    public List<Value> getAllValues() {
        var allNames = getOperandValues();
        allNames.add(getDestination());
        return allNames;
    }

    @Override
    public Instruction copy() {
        return new Phi((VirtualRegister) getDestination(), basicBlockValueMap);
    }

    @Override
    public Optional<Operand> getOperandNoArray() {
        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Phi phi = (Phi) o;
        return Objects.equals(basicBlockValueMap.values(), phi.basicBlockValueMap.values()) && Objects.equals(getDestination(), phi.getDestination());
    }

    @Override
    public int hashCode() {
        return Objects.hash(basicBlockValueMap.values());
    }

    @Override
    public String toString() {
        String rhs = basicBlockValueMap.values().stream().map(Value::repr).collect(Collectors.joining(", "));
        return String.format("%s%s: %s = phi (%s)", DOUBLE_INDENT, getDestination().repr(), getDestination().getType().getSourceCode(), rhs);
    }

    @Override
    public String syntaxHighlightedToString() {
        String rhs = basicBlockValueMap.values().stream().map(Value::repr).collect(Collectors.joining(", "));
        return String.format("%s%s: %s = phi (%s)", DOUBLE_INDENT, getDestination().repr(), getDestination().getType().getColoredSourceCode(), rhs);
    }
}
