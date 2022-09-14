package edu.mit.compilers.dataflow.usedef;

import edu.mit.compilers.codegen.codes.Instruction;
import edu.mit.compilers.codegen.names.IrValue;

public class Use extends UseDef {
    public Use(IrValue variable, Instruction line) {
        super(variable, line);
    }


    @Override
    public String toString() {
        return "use " + variable;
    }
}
