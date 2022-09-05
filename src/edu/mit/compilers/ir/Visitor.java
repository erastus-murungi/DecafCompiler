package edu.mit.compilers.ir;

import java.util.ArrayList;
import java.util.List;

import edu.mit.compilers.ast.Array;
import edu.mit.compilers.ast.AssignOpExpr;
import edu.mit.compilers.ast.Assignment;
import edu.mit.compilers.ast.BinaryOpExpression;
import edu.mit.compilers.ast.Block;
import edu.mit.compilers.ast.BooleanLiteral;
import edu.mit.compilers.ast.Break;
import edu.mit.compilers.ast.CharLiteral;
import edu.mit.compilers.ast.CompoundAssignOpExpr;
import edu.mit.compilers.ast.Continue;
import edu.mit.compilers.ast.DecimalLiteral;
import edu.mit.compilers.ast.Decrement;
import edu.mit.compilers.ast.ExpressionParameter;
import edu.mit.compilers.ast.FieldDeclaration;
import edu.mit.compilers.ast.For;
import edu.mit.compilers.ast.HexLiteral;
import edu.mit.compilers.ast.If;
import edu.mit.compilers.ast.ImportDeclaration;
import edu.mit.compilers.ast.Increment;
import edu.mit.compilers.ast.Initialization;
import edu.mit.compilers.ast.IntLiteral;
import edu.mit.compilers.ast.Len;
import edu.mit.compilers.ast.LocationArray;
import edu.mit.compilers.ast.LocationAssignExpr;
import edu.mit.compilers.ast.LocationVariable;
import edu.mit.compilers.ast.MethodCall;
import edu.mit.compilers.ast.MethodCallStatement;
import edu.mit.compilers.ast.MethodDefinition;
import edu.mit.compilers.ast.MethodDefinitionParameter;
import edu.mit.compilers.ast.Name;
import edu.mit.compilers.ast.ParenthesizedExpression;
import edu.mit.compilers.ast.Program;
import edu.mit.compilers.ast.Return;
import edu.mit.compilers.ast.StringLiteral;
import edu.mit.compilers.ast.UnaryOpExpression;
import edu.mit.compilers.ast.While;
import edu.mit.compilers.exceptions.DecafSemanticException;
import edu.mit.compilers.symboltable.SymbolTable;

public interface Visitor<T> {
    List<DecafSemanticException> exceptions = new ArrayList<>();

    T visit(IntLiteral intLiteral, SymbolTable symbolTable);

    T visit(BooleanLiteral booleanLiteral, SymbolTable symbolTable);

    T visit(DecimalLiteral decimalLiteral, SymbolTable symbolTable);

    T visit(HexLiteral hexLiteral, SymbolTable symbolTable);

    T visit(FieldDeclaration fieldDeclaration, SymbolTable symbolTable);

    T visit(MethodDefinition methodDefinition, SymbolTable symbolTable);

    T visit(ImportDeclaration importDeclaration, SymbolTable symbolTable);

    T visit(For forStatement, SymbolTable symbolTable);

    T visit(Break breakStatement, SymbolTable symbolTable);

    T visit(Continue continueStatement, SymbolTable symbolTable);

    T visit(While whileStatement, SymbolTable symbolTable);

    T visit(Program program, SymbolTable symbolTable);

    T visit(UnaryOpExpression unaryOpExpression, SymbolTable symbolTable);

    T visit(BinaryOpExpression binaryOpExpression, SymbolTable symbolTable);

    T visit(Block block, SymbolTable symbolTable);

    T visit(ParenthesizedExpression parenthesizedExpression, SymbolTable symbolTable);

    T visit(LocationArray locationArray, SymbolTable symbolTable);

    T visit(ExpressionParameter expressionParameter, SymbolTable symbolTable);

    T visit(If ifStatement, SymbolTable symbolTable);

    T visit(Return returnStatement, SymbolTable symbolTable);

    T visit(Array array, SymbolTable symbolTable);

    T visit(MethodCall methodCall, SymbolTable symbolTable);

    T visit(MethodCallStatement methodCallStatement, SymbolTable symbolTable);

    T visit(LocationAssignExpr locationAssignExpr, SymbolTable symbolTable);

    T visit(AssignOpExpr assignOpExpr, SymbolTable symbolTable);

    T visit(MethodDefinitionParameter methodDefinitionParameter, SymbolTable symbolTable);

    T visit(Name name, SymbolTable symbolTable);

    T visit(LocationVariable locationVariable, SymbolTable symbolTable);

    T visit(Len len, SymbolTable symbolTable);

    T visit(Increment increment, SymbolTable symbolTable);

    T visit(Decrement decrement, SymbolTable symbolTable);

    T visit(CharLiteral charLiteral, SymbolTable symbolTable);

    T visit(StringLiteral stringLiteral, SymbolTable symbolTable);

    T visit(CompoundAssignOpExpr compoundAssignOpExpr, SymbolTable symbolTable);

    T visit(Initialization initialization, SymbolTable symbolTable);

    T visit(Assignment assignment, SymbolTable symbolTable);
}
