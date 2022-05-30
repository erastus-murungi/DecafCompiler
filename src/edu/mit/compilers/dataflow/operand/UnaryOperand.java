package edu.mit.compilers.dataflow.operand;

import edu.mit.compilers.codegen.codes.StoreInstruction;
import edu.mit.compilers.codegen.codes.UnaryInstruction;
import edu.mit.compilers.codegen.names.AbstractName;

import java.util.Objects;

public class UnaryOperand extends Operand {
    public final AbstractName operand;
    public final String operator;

    public UnaryOperand(UnaryInstruction unaryInstruction) {
        this.operand = unaryInstruction.operand;
        this.operator = unaryInstruction.operator;
    }
    @Override
    public boolean contains(AbstractName name) {
        return this.operand.equals(name);
    }

    @Override
    public boolean isContainedIn(StoreInstruction storeInstruction) {
        if (storeInstruction instanceof UnaryInstruction) {
            UnaryInstruction unaryInstruction = (UnaryInstruction) storeInstruction;
            return new UnaryOperand(unaryInstruction).equals(this);
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnaryOperand that = (UnaryOperand) o;
        return Objects.equals(operand, that.operand) && Objects.equals(operator, that.operator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operand, operator);
    }

    @Override
    public String toString() {
        return String.format("%s %s", operator, operand);
    }
}
