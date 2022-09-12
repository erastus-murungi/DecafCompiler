package edu.mit.compilers.codegen.codes;

import java.util.Optional;
import java.util.Set;

import edu.mit.compilers.ast.AST;
import edu.mit.compilers.codegen.names.GlobalAddress;
import edu.mit.compilers.codegen.names.LValue;
import edu.mit.compilers.codegen.names.VirtualRegister;
import edu.mit.compilers.dataflow.operand.Operand;

public abstract class StoreInstruction extends HasOperand implements Cloneable {
    protected LValue destination;

    public StoreInstruction(LValue destination, AST source) {
        super(source);
        this.destination = destination;
    }

    public StoreInstruction(LValue destination) {
        super();
        this.destination = destination;
    }

    public StoreInstruction(LValue destination, AST source, String comment) {
        super(source, comment);
        this.destination = destination;
    }

    public LValue getDestination() {
        return destination;
    }

    public void setDestination(LValue dst) {
        this.destination = dst;
    }

    public abstract Optional<Operand> getOperandNoArray();

    public Optional<Operand> getOperandNoArrayNoGlobals(Set<GlobalAddress> globals) {
        Optional<Operand> operand = getOperandNoArray();
        if (operand.isPresent()) {
            if (operand.get().getNames().stream().anyMatch(globals::contains))
                return Optional.empty();
            }
        return operand;
    }

    @Override
    public StoreInstruction clone() {
        try {
            StoreInstruction clone = (StoreInstruction) super.clone();
            // TODO: copy mutable state here, so the clone can't change the internals of the original
            clone.destination = destination;
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
