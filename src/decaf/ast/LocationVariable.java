package decaf.ast;

import java.util.Collections;
import java.util.List;

import decaf.codegen.CodegenAstVisitor;
import decaf.codegen.names.IrAssignable;
import decaf.common.Pair;
import decaf.ir.AstVisitor;
import decaf.symboltable.SymbolTable;

public class LocationVariable extends Location {
    public LocationVariable(Name name) {
        super(name);
    }

    @Override
    public List<Pair<String, AST>> getChildren() {
        return Collections.singletonList(new Pair<>("name", name));
    }

    @Override
    public <T> T accept(AstVisitor<T> ASTVisitor, SymbolTable curSymbolTable) {
        return ASTVisitor.visit(this, curSymbolTable);
    }

    public <T> T accept(CodegenAstVisitor<T> codegenAstVisitor, IrAssignable resultLocation) {
        return codegenAstVisitor.visit(this, resultLocation);
    }

    @Override
    public boolean isTerminal() {
        return true;
    }

    @Override
    public String toString() {
        return "LocationVariable{" + "name=" + name + '}';
    }

    @Override
    public String getSourceCode() {
        return name.getSourceCode();
    }
}
