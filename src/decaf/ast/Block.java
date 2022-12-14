package decaf.ast;

import java.util.ArrayList;
import java.util.List;

import decaf.codegen.CodegenAstVisitor;
import decaf.codegen.names.IrAssignable;
import decaf.codegen.names.IrAssignable;
import decaf.common.Pair;
import decaf.ir.AstVisitor;
import decaf.symboltable.SymbolTable;

public class Block extends AST {
    final public List<FieldDeclaration> fieldDeclarationList;
    final public List<Statement> statementList;
    public SymbolTable blockSymbolTable;

    public Block(List<FieldDeclaration> fieldDeclarationList, List<Statement> statementList) {
        this.fieldDeclarationList = fieldDeclarationList;
        this.statementList = statementList;
    }

    @Override
    public Type getType() {
        return Type.Undefined;
    }

    @Override
    public List<Pair<String, AST>> getChildren() {
        ArrayList<Pair<String, AST>> nodeArrayList = new ArrayList<>();
        for (FieldDeclaration fieldDeclaration : fieldDeclarationList)
            nodeArrayList.add(new Pair<>("fieldDeclaration", fieldDeclaration));
        for (Statement statement : statementList)
            nodeArrayList.add(new Pair<>("statement", statement));
        return nodeArrayList;
    }

    @Override
    public boolean isTerminal() {
        return false;
    }

    @Override
    public String toString() {
        return "Block{" + "fieldDeclarationList=" + fieldDeclarationList + ", statementList=" + statementList + '}';
    }

    @Override
    public String getSourceCode() {
        List<String> list = new ArrayList<>();
        for (FieldDeclaration fieldDeclaration : fieldDeclarationList) {
            String sourceCode = fieldDeclaration.getSourceCode();
            list.add(sourceCode);
        }
        String fieldDeclarations = String.join(";\n    ", list);
        List<String> s = new ArrayList<>();
        for (int i = 0; i < statementList.size(); i++) {
            Statement statement = statementList.get(i);
            s.add(statement.getSourceCode());
            if (statement instanceof For || statement instanceof While || statement instanceof If) {
                s.add((i == statementList.size() - 1) ? "    " : "\n    ");
            } else {
                s.add((i == statementList.size() - 1) ? ";    " : ";\n    ");
            }
        }
        return fieldDeclarations + (fieldDeclarations.isBlank() ? "" : ";\n    ") + String.join("", s);
    }

    @Override
    public <T> T accept(AstVisitor<T> ASTVisitor, SymbolTable curSymbolTable) {
        return ASTVisitor.visit(this, curSymbolTable);
    }

    public <T> T accept(CodegenAstVisitor<T> codegenAstVisitor, IrAssignable resultLocation) {
        return codegenAstVisitor.visit(this, resultLocation);
    }
}