package decaf.codegen.codes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import decaf.ast.AST;
import decaf.codegen.names.IrMemoryAddress;
import decaf.codegen.names.IrValue;
import decaf.dataflow.operand.Operand;

public abstract class HasOperand extends Instruction {
  public HasOperand(
      AST source,
      String comment
  ) {
    super(
        source,
        comment
    );
  }

  public HasOperand(AST source) {
    super(
        source,
        null
    );
  }

  public HasOperand() {
    super(
        null,
        null
    );
  }

  public abstract Operand getOperand();

  public abstract List<IrValue> genOperandIrValuesSurface();

  public List<IrValue> genOperandIrValues() {
    return genOperandIrValuesSurface();
  }

  public <T extends IrValue> List<T> genOperandIrValuesFiltered(Class<T> tClass) {
    return genOperandIrValuesSurface().stream().filter(irValue -> tClass.isAssignableFrom(irValue.getClass())).map(tClass::cast).toList();
  }

  public List<IrValue> genOperandIrValuesFiltered(Predicate<IrValue> irValuePredicate) {
    return genOperandIrValuesSurface().stream()
                               .filter(irValuePredicate)
                               .toList();
  }

  public abstract boolean replaceValue(
      IrValue oldName,
      IrValue newName
  );
}
