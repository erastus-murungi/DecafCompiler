package edu.mit.compilers.ast;


import edu.mit.compilers.codegen.CodegenAstVisitor;
import edu.mit.compilers.codegen.names.AssignableName;
import edu.mit.compilers.grammar.TokenPosition;
import edu.mit.compilers.ir.Visitor;
import edu.mit.compilers.symbolTable.SymbolTable;
import edu.mit.compilers.utils.Pair;

import java.util.Collections;
import java.util.List;

import static edu.mit.compilers.grammar.DecafScanner.INCREMENT;

public class Increment extends AssignExpr {
    public Increment(TokenPosition tokenPosition) {
        super(tokenPosition, null);
    }

    @Override
    public List<Pair<String, AST>> getChildren() {
        return Collections.emptyList();
    }

    @Override
    public boolean isTerminal() {
        return true;
    }

    @Override
    public String toString() {
        return "Increment{}";
    }

    @Override
    public String getSourceCode() {
        return INCREMENT;
    }

    @Override
    public <T> T accept(Visitor<T> visitor, SymbolTable curSymbolTable) {
        return visitor.visit(this, curSymbolTable);
    }

    @Override
    public String getOperator() {
        return "++";
    }

    public <T> T accept(CodegenAstVisitor<T> codegenAstVisitor, AssignableName resultLocation) {
        return null;
    }
}
