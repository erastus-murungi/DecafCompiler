package edu.mit.compilers.codegen.codes;

import edu.mit.compilers.ast.MethodCall;
import edu.mit.compilers.codegen.InstructionVisitor;
import edu.mit.compilers.codegen.names.AbstractName;
import edu.mit.compilers.dataflow.operand.MethodCallOperand;
import edu.mit.compilers.dataflow.operand.Operand;
import edu.mit.compilers.utils.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import java.util.stream.Collectors;

public class FunctionCallNoResult extends HasOperand implements FunctionCall {
    private Stack<AbstractName> arguments;

    public FunctionCallNoResult(MethodCall methodCall, Stack<AbstractName> arguments, String comment) {
        super(methodCall, comment);
        this.arguments = arguments;
    }

    public Stack<AbstractName> getArguments() {
        return arguments;
    }

    @Override
    public MethodCall getMethod() {
        return (MethodCall) source;
    }
    @Override
    public String toString() {
        return String.format("%s%s %s %s%s", DOUBLE_INDENT, "call", getMethodName(), DOUBLE_INDENT, getComment().isPresent() ? " #  " + getComment().get() : "");
    }

    @Override
    public <T, E> T accept(InstructionVisitor<T, E> visitor, E extra) {
        return visitor.visit(this, extra);
    }

    @Override
    public List<AbstractName> getAllNames() {
        return new ArrayList<>(arguments);
    }

    @Override
    public Operand getOperand() {
        return new MethodCallOperand(this);
    }

    @Override
    public List<AbstractName> getOperandNames() {
        return new ArrayList<>(arguments);
    }

    public boolean replace(AbstractName oldName, AbstractName newName) {
        var replaced = false;
        int i = 0;
        for (AbstractName abstractName: arguments) {
            if (abstractName.equals(oldName)) {
                arguments.set(i, newName);
                replaced = true;
            }
            i += 1;
        }
        return replaced;
    }

    @Override
    public String repr() {
        var callString =  Utils.coloredPrint("call", Utils.ANSIColorConstants.ANSI_GREEN_BOLD);
        var args = arguments.stream().map(AbstractName::repr).collect(Collectors.joining(", "));
        return String.format("%s%s %s @%s(%s) %s%s", DOUBLE_INDENT, callString, getMethodReturnType(), getMethodName(), args, DOUBLE_INDENT, getComment().isPresent() ? " #  " + getComment().get() : "");
    }

    @Override
    public Instruction copy() {
        return new FunctionCallNoResult((MethodCall) source, arguments, getComment().orElse(null));
    }
}