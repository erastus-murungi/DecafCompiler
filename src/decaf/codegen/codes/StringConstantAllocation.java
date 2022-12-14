package decaf.codegen.codes;

import java.util.Collections;
import java.util.List;

import decaf.asm.AsmWriter;
import decaf.codegen.names.IrValue;
import decaf.codegen.names.IrStringConstant;

public class StringConstantAllocation extends Instruction {
    private final IrStringConstant stringConstant;

    public StringConstantAllocation(IrStringConstant stringConstant) {
        super(null);
        this.stringConstant = stringConstant;
    }

    public IrStringConstant getStringConstant() {
        return stringConstant;
    }

    public String toString() {
        return String.format("%s:\n\t.%s   %s", stringConstant, "string", stringConstant.getValue());
    }

    @Override
    public void accept(AsmWriter asmWriter) {
    }

    @Override
    public List<IrValue> genIrValuesSurface() {
        return Collections.emptyList();
    }

    @Override
    public String syntaxHighlightedToString() {
        return String.format("@.%s = %s%s%s", stringConstant.getLabel(), stringConstant.getValue(), DOUBLE_INDENT, "# " + stringConstant.size() + " bytes");
    }

    @Override
    public Instruction copy() {
        return new StringConstantAllocation(stringConstant);
    }

    public String getASM() {
        return String.format("%s:\n\t.%s   %s\n\t.align 16", stringConstant.getLabel(), "string", stringConstant.getValue());
    }
}
