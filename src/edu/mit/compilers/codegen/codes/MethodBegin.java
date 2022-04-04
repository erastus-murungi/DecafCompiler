package edu.mit.compilers.codegen.codes;

import edu.mit.compilers.ast.*;
import edu.mit.compilers.codegen.ThreeAddressCodeVisitor;
import edu.mit.compilers.codegen.names.AbstractName;

import java.util.*;
import java.util.stream.Collectors;

public class MethodBegin extends ThreeAddressCode {
    public long sizeOfLocals;
    public final MethodDefinition methodDefinition;
    private List<AbstractName> locals;
    public HashMap<String, Integer> nameToStackOffset = new HashMap<>();

    public MethodBegin(MethodDefinition methodDefinition) {
        super(methodDefinition);
        this.methodDefinition = methodDefinition;
        this.sizeOfLocals = 0;
    }

    @Override
    public String toString() {
        return String.format("%s:\n%s%s %s", methodDefinition.methodName.id, DOUBLE_INDENT, "BeginFunction", sizeOfLocals);
    }

    @Override
    public <T, E> T accept(ThreeAddressCodeVisitor<T, E> visitor, E extra) {
        return visitor.visit(this, extra);
    }

    @Override
    public List<AbstractName> getNames() {
        return Collections.emptyList();
    }

    private void reorderLocals() {
        List<AbstractName> methodParametersNames = new ArrayList<>();

        Set<String> methodParameters = methodDefinition.methodDefinitionParameterList
                .stream()
                .map(methodDefinitionParameter -> methodDefinitionParameter.id.id)
                .collect(Collectors.toSet());

        List<AbstractName> methodParamNamesList = new ArrayList<>();
        for (AbstractName name : locals)
            if (methodParameters.contains(name.toString())) {
                methodParamNamesList.add(name);
            }
        for (AbstractName local : locals) {
            if (methodParameters.contains(local.toString())) {
                methodParametersNames.add(local);
            }
        }
        for (AbstractName name : methodParametersNames) {
            locals.remove(name);
        }
        locals.addAll(0, methodParamNamesList
                .stream()
                .sorted(Comparator.comparing(AbstractName::toString))
                .collect(Collectors.toList()));
    }


    public void setLocals(List<AbstractName> locals) {
        this.locals = locals;
        reorderLocals();
        this.sizeOfLocals = (long) locals.stream().map(abstractName -> abstractName.wordSize).reduce(0, Integer::sum);
    }

    public List<AbstractName> getLocals() {
        return locals;
    }
}
