package edu.mit.compilers.dataflow.operand;

import edu.mit.compilers.codegen.codes.HasResult;
import edu.mit.compilers.codegen.codes.Triple;
import edu.mit.compilers.codegen.names.AbstractName;
import edu.mit.compilers.codegen.names.AssignableName;

import java.util.Objects;

public class UnaryOperand extends Operand {
    public final AbstractName operand;
    public final String operator;

    public UnaryOperand(Triple triple) {
        this.operand = triple.operand;
        this.operator = triple.operator;
    }
    @Override
    public boolean contains(AbstractName name) {
        return this.operand.equals(name);
    }

    @Override
    public boolean isContainedIn(HasResult hasResult) {
        if (hasResult instanceof Triple) {
            Triple triple = (Triple) hasResult;
            return new UnaryOperand(triple).equals(this);
        }
        return false;
    }

    @Override
    public HasResult fromOperand(AssignableName resultLocation) {
        return new Triple(resultLocation, operator, operand, null);
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