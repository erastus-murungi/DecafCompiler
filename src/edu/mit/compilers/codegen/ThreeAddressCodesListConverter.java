package edu.mit.compilers.codegen;

import edu.mit.compilers.ast.*;
import edu.mit.compilers.ast.MethodCall;
import edu.mit.compilers.cfg.*;
import edu.mit.compilers.codegen.codes.*;
import edu.mit.compilers.codegen.names.*;
import edu.mit.compilers.descriptors.ArrayDescriptor;
import edu.mit.compilers.descriptors.Descriptor;
import edu.mit.compilers.descriptors.GlobalDescriptor;
import edu.mit.compilers.grammar.DecafScanner;
import edu.mit.compilers.grammar.TokenPosition;
import edu.mit.compilers.ir.Visitor;
import edu.mit.compilers.symbolTable.SymbolTable;
import edu.mit.compilers.utils.Pair;


import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class ThreeAddressCodesListConverter implements CFGVisitor<ThreeAddressCodeList> {
    private static class ThreeAddressCodesConverter implements Visitor<ThreeAddressCodeList> {
        HashMap<String, String> temporaryToStringLiteral;

        public ThreeAddressCodesConverter() {
            this.temporaryToStringLiteral = new HashMap<>();
        }

        @Override
        public ThreeAddressCodeList visit(IntLiteral intLiteral, SymbolTable symbolTable) {
            return new ThreeAddressCodeList(
                    ConstantName.fromIntLiteral(intLiteral));
        }

        @Override
        public ThreeAddressCodeList visit(BooleanLiteral booleanLiteral, SymbolTable symbolTable) {
            return new ThreeAddressCodeList(
                    ConstantName.fromBooleanLiteral(booleanLiteral));
        }

        @Override
        public ThreeAddressCodeList visit(DecimalLiteral decimalLiteral, SymbolTable symbolTable) {
            return new ThreeAddressCodeList(
                    ConstantName.fromIntLiteral(decimalLiteral));
        }

        @Override
        public ThreeAddressCodeList visit(HexLiteral hexLiteral, SymbolTable symbolTable) {
            return new ThreeAddressCodeList(ConstantName.fromIntLiteral(hexLiteral));
        }

        @Override
        public ThreeAddressCodeList visit(FieldDeclaration fieldDeclaration, SymbolTable symbolTable) {
            return new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
        }

        public List<PushParameter> flattenMethodDefinitionArguments(ThreeAddressCodeList threeAddressCodeList,
                                                                    List<MethodDefinitionParameter> methodDefinitionParameterList,
                                                                    SymbolTable symbolTable) {
            List<AssignableName> newParamNames = new ArrayList<>();
            for (MethodDefinitionParameter methodDefinitionParameter : methodDefinitionParameterList) {
                ThreeAddressCodeList paramTACList = methodDefinitionParameter.accept(this, symbolTable);
                threeAddressCodeList.add(paramTACList);
                newParamNames.add((AssignableName) paramTACList.place);
            }
            return getPushParameterCode(threeAddressCodeList, newParamNames, methodDefinitionParameterList);
        }

        private List<PushParameter> getPushParameterCode(ThreeAddressCodeList threeAddressCodeList,
                                                         List<AssignableName> newParamNames,
                                                         List<? extends AST> methodCallOrDefinitionArguments) {
            List<PushParameter> pushParams = new ArrayList<>();
            for (int i = 0; i < newParamNames.size(); i++) {
                final PushParameter pushParameter = new PushParameter(newParamNames.get(i), i, methodCallOrDefinitionArguments.get(i));
                threeAddressCodeList.addCode(pushParameter);
                pushParams.add(pushParameter);
            }
            return pushParams;
        }

        private List<PushParameter> flattenMethodCallArguments(ThreeAddressCodeList threeAddressCodeList,
                                                               List<MethodCallParameter> methodCallParameterList,
                                                               SymbolTable symbolTable) {
            List<AssignableName> newParamNames = new ArrayList<>();
            for (MethodCallParameter methodCallParameter : methodCallParameterList) {
                ThreeAddressCodeList paramTACList = methodCallParameter.accept(this, symbolTable);
                threeAddressCodeList.add(paramTACList);
                newParamNames.add((AssignableName) paramTACList.place);
            }

            return getPushParameterCode(threeAddressCodeList, newParamNames, methodCallParameterList);
        }

        @Override
        public ThreeAddressCodeList visit(MethodDefinition methodDefinition, SymbolTable symbolTable) {
            throw new IllegalStateException("A method definition is illegal");
        }

        @Override
        public ThreeAddressCodeList visit(ImportDeclaration importDeclaration, SymbolTable symbolTable) {
            return null;
        }

        @Override
        public ThreeAddressCodeList visit(For forStatement, SymbolTable symbolTable) {
            throw new IllegalStateException("A for statement is illegal");
        }

        @Override
        public ThreeAddressCodeList visit(Break breakStatement, SymbolTable symbolTable) {
            throw new IllegalStateException("A break statement is illegal");
        }

        @Override
        public ThreeAddressCodeList visit(Continue continueStatement, SymbolTable symbolTable) {
            throw new IllegalStateException("A continue statement is illegal");
        }

        @Override
        public ThreeAddressCodeList visit(While whileStatement, SymbolTable symbolTable) {
            throw new IllegalStateException("A while statement is illegal");
        }

        @Override
        public ThreeAddressCodeList visit(Program program, SymbolTable symbolTable) {
            throw new IllegalStateException("A program is illegal");
        }

        @Override
        public ThreeAddressCodeList visit(UnaryOpExpression unaryOpExpression, SymbolTable symbolTable) {
            final ThreeAddressCodeList operandTACList = unaryOpExpression.operand.accept(this, symbolTable);
            final ThreeAddressCodeList retTACList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
            retTACList.add(operandTACList);
            TemporaryName result = TemporaryName.generateTemporaryName(unaryOpExpression.builtinType.getFieldSize());
            retTACList.addCode(new OneOperandAssign(unaryOpExpression, result, operandTACList.place, unaryOpExpression.op.getSourceCode()));
            retTACList.place = result;
            return retTACList;
        }

        @Override
        public ThreeAddressCodeList visit(BinaryOpExpression binaryOpExpression, SymbolTable symbolTable) {
            final ThreeAddressCodeList leftTACList = binaryOpExpression.lhs.accept(this, symbolTable);
            final ThreeAddressCodeList rightTACList = binaryOpExpression.rhs.accept(this, symbolTable);

            final ThreeAddressCodeList binOpExpressionTACList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
            binOpExpressionTACList.add(leftTACList);
            binOpExpressionTACList.add(rightTACList);
            TemporaryName temporaryVariable = TemporaryName.generateTemporaryName(binaryOpExpression.builtinType.getFieldSize());
            binOpExpressionTACList.addCode(
                    new TwoOperandAssign(
                            binaryOpExpression,
                            temporaryVariable,
                            leftTACList.place,
                            binaryOpExpression.op.getSourceCode(),
                            rightTACList.place,
                            binaryOpExpression.getSourceCode()));
            binOpExpressionTACList.place = temporaryVariable;
            return binOpExpressionTACList;
        }

        @Override
        public ThreeAddressCodeList visit(Block block, SymbolTable symbolTable) {
            ThreeAddressCodeList threeAddressCodeList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
            for (FieldDeclaration fieldDeclaration : block.fieldDeclarationList)
                threeAddressCodeList.add(fieldDeclaration.accept(this, symbolTable));
            for (Statement statement : block.statementList)
                statement.accept(this, symbolTable);
            return threeAddressCodeList;
        }

        @Override
        public ThreeAddressCodeList visit(ParenthesizedExpression parenthesizedExpression, SymbolTable symbolTable) {
            return parenthesizedExpression.expression.accept(this, symbolTable);
        }

        @Override
        public ThreeAddressCodeList visit(LocationArray locationArray, SymbolTable symbolTable) {
            ThreeAddressCodeList locationThreeAddressCodeList = locationArray.expression.accept(this, symbolTable);
            ThreeAddressCodeList threeAddressCodeList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
            threeAddressCodeList.add(locationThreeAddressCodeList);

            Optional<Descriptor> descriptorFromValidScopes = symbolTable.getDescriptorFromValidScopes(locationArray.name.id);
            if (descriptorFromValidScopes.isEmpty()) {
                throw new IllegalStateException("expected to find array " + locationArray.name.id + " in scope");
            } else {
                ArrayDescriptor arrayDescriptor = (ArrayDescriptor) descriptorFromValidScopes.get();
                TemporaryName widthOfField = TemporaryName.generateTemporaryName((int) (arrayDescriptor.size * arrayDescriptor.type.getFieldSize()));
                threeAddressCodeList.addCode(new CopyInstruction(new ConstantName((long) arrayDescriptor.type.getFieldSize(), arrayDescriptor.type.getFieldSize()), widthOfField, locationArray));
                TemporaryName offsetResult = TemporaryName.generateTemporaryName(BuiltinType.Int.getFieldSize());
                threeAddressCodeList.addCode(new TwoOperandAssign(null, offsetResult, locationThreeAddressCodeList.place, DecafScanner.MULTIPLY, widthOfField, "offset"));
                TemporaryName locationResult = TemporaryName.generateTemporaryName(arrayDescriptor.type.getFieldSize());
                threeAddressCodeList.addCode(new TwoOperandAssign(null, locationResult, new VariableName(locationArray.name.id, arrayDescriptor.type.getFieldSize()), DecafScanner.PLUS, offsetResult, "array location"));
                threeAddressCodeList.place = locationResult;
                return threeAddressCodeList;
            }
        }

        @Override
        public ThreeAddressCodeList visit(ExpressionParameter expressionParameter, SymbolTable symbolTable) {
            ThreeAddressCodeList expressionTACList = expressionParameter.expression.accept(this, symbolTable);
            ThreeAddressCodeList expressionParameterTACList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
            expressionParameterTACList.add(expressionTACList);
            TemporaryName temporaryVariable = TemporaryName.generateTemporaryName(expressionParameter.expression.builtinType.getFieldSize());
            expressionParameterTACList.addCode(new CopyInstruction(expressionTACList.place, temporaryVariable, expressionParameter));
            expressionParameterTACList.place = temporaryVariable;
            return expressionParameterTACList;
        }

        @Override
        public ThreeAddressCodeList visit(If ifStatement, SymbolTable symbolTable) {
            throw new IllegalStateException("An if statement is illegal");
        }

        @Override
        public ThreeAddressCodeList visit(Return returnStatement, SymbolTable symbolTable) {
            ThreeAddressCodeList retTACList;
            if (returnStatement.retExpression != null) {
                ThreeAddressCodeList retExpressionTACList = returnStatement.retExpression.accept(this, symbolTable);
                retTACList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
                retTACList.add(retExpressionTACList);
                retTACList.place = retExpressionTACList.place;
                retTACList.addCode(new MethodReturn(returnStatement, (AssignableName) retTACList.place));
            } else {
                retTACList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
                retTACList.addCode(new MethodReturn(returnStatement));
            }
            return retTACList;
        }

        @Override
        public ThreeAddressCodeList visit(Array array, SymbolTable symbolTable) {
            return null;
        }

        @Override
        public ThreeAddressCodeList visit(MethodCall methodCall, SymbolTable symbolTable) {
            ThreeAddressCodeList threeAddressCodeList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
            List<PushParameter> pushParameterList = flattenMethodCallArguments(threeAddressCodeList, methodCall.methodCallParameterList, symbolTable);
            TemporaryName temporaryVariable = TemporaryName.generateTemporaryName(methodCall.builtinType.getFieldSize());
            threeAddressCodeList.addCode(new edu.mit.compilers.codegen.codes.MethodCall(methodCall, temporaryVariable, methodCall.getSourceCode()));
            for (int i = pushParameterList.size() - 1; i >= 0; i--)
                threeAddressCodeList.addCode(new PopParameter(pushParameterList.get(i).parameterName, methodCall.methodCallParameterList.get(i)));
            threeAddressCodeList.place = temporaryVariable;
            return threeAddressCodeList;
        }

        @Override
        public ThreeAddressCodeList visit(MethodCallStatement methodCallStatement, SymbolTable symbolTable) {
            ThreeAddressCodeList threeAddressCodeList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
            List<PushParameter> pushParameterList = flattenMethodCallArguments(threeAddressCodeList, methodCallStatement.methodCall.methodCallParameterList, symbolTable);
            threeAddressCodeList.addCode(new edu.mit.compilers.codegen.codes.MethodCall(methodCallStatement.methodCall, methodCallStatement.getSourceCode()));
            for (int i = pushParameterList.size() - 1; i >= 0; i--)
                threeAddressCodeList.addCode(new PopParameter(pushParameterList.get(i).parameterName, methodCallStatement.methodCall.methodCallParameterList.get(i)));
            return threeAddressCodeList;
        }

        @Override
        public ThreeAddressCodeList visit(LocationAssignExpr locationAssignExpr, SymbolTable symbolTable) {
            ThreeAddressCodeList lhs = locationAssignExpr.location.accept(this, symbolTable);
            AssignableName locationVariable = (AssignableName) lhs.place;
            ThreeAddressCodeList rhs = locationAssignExpr.assignExpr.accept(this, symbolTable);
            AbstractName valueVariable = rhs.place;
            lhs.add(rhs);
            lhs.addCode(new CopyInstruction(valueVariable, locationVariable, locationAssignExpr, locationAssignExpr.getSourceCode()));
            return lhs;
        }

        @Override
        public ThreeAddressCodeList visit(AssignOpExpr assignOpExpr, SymbolTable symbolTable) {
            throw new IllegalStateException("not allowed");
        }

        @Override
        public ThreeAddressCodeList visit(MethodDefinitionParameter methodDefinitionParameter, SymbolTable symbolTable) {
            int size = symbolTable.getDescriptorFromValidScopes(methodDefinitionParameter.id.id).get().type.getFieldSize();
            TemporaryName temporaryVariable = TemporaryName.generateTemporaryName(size);
            return new ThreeAddressCodeList(temporaryVariable,
                    Collections.singletonList(new CopyInstruction(new VariableName(methodDefinitionParameter.id.id, size), temporaryVariable, methodDefinitionParameter)));
        }

        @Override
        public ThreeAddressCodeList visit(Name name, SymbolTable symbolTable) {
            return new ThreeAddressCodeList(new VariableName(name.id, symbolTable.getDescriptorFromValidScopes(name.id).get().type.getFieldSize()));
        }

        @Override
        public ThreeAddressCodeList visit(LocationVariable locationVariable, SymbolTable symbolTable) {
            return new ThreeAddressCodeList(new VariableName(locationVariable.name.id, symbolTable.getDescriptorFromValidScopes(locationVariable.name.id).get().type.getFieldSize()));
        }

        @Override
        public ThreeAddressCodeList visit(Len len, SymbolTable symbolTable) {
            ThreeAddressCodeList threeAddressCodeList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
            Optional<Descriptor> optionalDescriptor = symbolTable.getDescriptorFromValidScopes(len.nameId.id);
            if (optionalDescriptor.isEmpty())
                throw new IllegalStateException(len.nameId.id + " should be present");
            else {
                ArrayDescriptor arrayDescriptor = (ArrayDescriptor) optionalDescriptor.get();
                TemporaryName temporaryNumberOfElemsVariable = TemporaryName.generateTemporaryName(arrayDescriptor.type.getFieldSize());
                threeAddressCodeList.addCode(new CopyInstruction(new ConstantName(arrayDescriptor.size, BuiltinType.IntArray.getFieldSize()), temporaryNumberOfElemsVariable, len));
                threeAddressCodeList.place = temporaryNumberOfElemsVariable;
                return threeAddressCodeList;
            }
        }

        @Override
        public ThreeAddressCodeList visit(Increment increment, SymbolTable symbolTable) {
            throw new IllegalStateException("not allowed");
        }

        @Override
        public ThreeAddressCodeList visit(Decrement decrement, SymbolTable symbolTable) {
            throw new IllegalStateException("not allowed");
        }

        @Override
        public ThreeAddressCodeList visit(CharLiteral charLiteral, SymbolTable symbolTable) {
            throw new IllegalStateException("not allowed");
        }

        @Override
        public ThreeAddressCodeList visit(StringLiteral stringLiteral, SymbolTable symbolTable) {
            StringLiteralStackAllocation label = literalStackAllocationHashMap.get(stringLiteral.literal);
            TemporaryName temporaryVariable = TemporaryName.generateTemporaryName(label.size());
            StringConstantName stringConstantName = new StringConstantName(label.label, label.size());
            return new ThreeAddressCodeList(
                    temporaryVariable,
                    Collections.singletonList(
                            new CopyInstruction(stringConstantName, temporaryVariable, stringLiteral)));
        }

        @Override
        public ThreeAddressCodeList visit(CompoundAssignOpExpr compoundAssignOpExpr, SymbolTable curSymbolTable) {
            return compoundAssignOpExpr.expression.accept(this, curSymbolTable);
        }

        @Override
        public ThreeAddressCodeList visit(Initialization initialization, SymbolTable symbolTable) {
            ThreeAddressCodeList initIdThreeAddressList = initialization.initId.accept(this, symbolTable);
            ThreeAddressCodeList initExpressionThreeAddressList = initialization.initExpression.accept(this, symbolTable);
            CopyInstruction copyInstruction = new CopyInstruction(initExpressionThreeAddressList.place, (AssignableName) initIdThreeAddressList.place, initialization);

            ThreeAddressCodeList initializationThreeAddressCodeList = new ThreeAddressCodeList(initIdThreeAddressList.place);
            initializationThreeAddressCodeList.add(initIdThreeAddressList);
            initializationThreeAddressCodeList.add(initExpressionThreeAddressList);
            initializationThreeAddressCodeList.addCode(copyInstruction);
            return initializationThreeAddressCodeList;
        }

        @Override
        public ThreeAddressCodeList visit(Assignment assignment, SymbolTable symbolTable) {
            ThreeAddressCodeList threeAddressCodeList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
            if (assignment.assignExpr instanceof AssignOpExpr) {
                AssignOpExpr assignOpExpr = (AssignOpExpr) assignment.assignExpr;
                if (!assignOpExpr.assignOp.op.equals(DecafScanner.ASSIGN)) {
                    // simplify
                    return convertToBinaryExpression(
                            assignment.location,
                            mapCompoundAssignOperator(assignOpExpr.assignOp),
                            assignOpExpr.expression,
                            symbolTable,
                            assignment.location.name.id,
                            assignment
                    );
                }
            } else if (assignment.assignExpr instanceof Decrement) {
                Decrement decrement = (Decrement) assignment.assignExpr;
                return convertToBinaryExpression(
                        assignment.location,
                        new ArithmeticOperator(decrement.tokenPosition, DecafScanner.MINUS),
                        new DecimalLiteral(decrement.tokenPosition, "1"),
                        symbolTable,
                        assignment.location.name.id,
                        assignment
                );
            } else if (assignment.assignExpr instanceof Increment) {
                    Increment increment = (Increment) assignment.assignExpr;
                    return convertToBinaryExpression(
                            assignment.location,
                            new ArithmeticOperator(increment.tokenPosition, DecafScanner.PLUS),
                            new DecimalLiteral(increment.tokenPosition, "1"),
                            symbolTable,
                            assignment.location.name.id,
                            assignment
                    );
                }

            ThreeAddressCodeList rhs = assignment.assignExpr.expression.accept(this, symbolTable);
            ThreeAddressCodeList lhs = assignment.location.accept(this, symbolTable);
            threeAddressCodeList.add(rhs);
            threeAddressCodeList.add(lhs);
            threeAddressCodeList.addCode(new CopyInstruction(rhs.place, (AssignableName) lhs.place, assignment));
            threeAddressCodeList.place = lhs.place;
            return threeAddressCodeList;
        }

        private BinOperator mapCompoundAssignOperator(AST augmentedAssignOperator) {
            String op;
            TokenPosition tokenPosition;
            if (augmentedAssignOperator instanceof CompoundAssignOperator) {
                op = ((CompoundAssignOperator) augmentedAssignOperator).op;
                tokenPosition = ((CompoundAssignOperator) augmentedAssignOperator).tokenPosition;
            } else if (augmentedAssignOperator instanceof AssignOperator) {
                op = ((AssignOperator) augmentedAssignOperator).op;
                tokenPosition = ((AssignOperator) augmentedAssignOperator).tokenPosition;
            } else
                throw new IllegalStateException(augmentedAssignOperator.getClass().getSimpleName() + " not cannot be map");
            BinOperator binOperator;
            switch (op) {
                case DecafScanner.ADD_ASSIGN:
                    binOperator = new ArithmeticOperator(tokenPosition, DecafScanner.PLUS);
                    break;
                case DecafScanner.MINUS_ASSIGN:
                    binOperator = new ArithmeticOperator(tokenPosition, DecafScanner.MINUS);
                    break;
                case DecafScanner.MULTIPLY_ASSIGN:
                    binOperator = new ArithmeticOperator(tokenPosition, DecafScanner.MULTIPLY);
                    break;
                default:
                    throw new IllegalStateException(op + " not recognized");
            }
            return binOperator;
        }


        private ThreeAddressCodeList convertToBinaryExpression(
                Expression lhs,
                BinOperator binOperator,
                Expression rhs,
                SymbolTable symbolTable,
                String locationId,
                AST source
        ) {
            ThreeAddressCodeList updateExprTAC;
            BinaryOpExpression binaryOpExpression = new BinaryOpExpression(lhs, binOperator, rhs);
            binaryOpExpression.builtinType = symbolTable.getDescriptorFromValidScopes(locationId).get().type;
            updateExprTAC = binaryOpExpression.accept(this, symbolTable);
            CopyInstruction copyInstruction = new CopyInstruction(updateExprTAC.place, new VariableName(locationId, symbolTable.getDescriptorFromValidScopes(locationId).get().type.getFieldSize()), source);
            updateExprTAC.addCode(copyInstruction);
            return updateExprTAC;
        }

        @Override
        public ThreeAddressCodeList visit(Update update, SymbolTable symbolTable) {
            ThreeAddressCodeList updateExprTAC;
            if (update.updateAssignExpr instanceof CompoundAssignOpExpr) {
                CompoundAssignOpExpr compoundAssignOpExpr = (CompoundAssignOpExpr) update.updateAssignExpr;
                return convertToBinaryExpression(
                        update.updateLocation,
                        mapCompoundAssignOperator(compoundAssignOpExpr.compoundAssignOp),
                        compoundAssignOpExpr.expression,
                        symbolTable,
                        update.updateLocation.name.id,
                        update
                );
            } else if (update.updateAssignExpr instanceof Decrement) {
                Decrement decrement = (Decrement) update.updateAssignExpr;
                return convertToBinaryExpression(
                        update.updateLocation,
                        new ArithmeticOperator(decrement.tokenPosition, DecafScanner.MINUS),
                        new DecimalLiteral(decrement.tokenPosition, "1"),
                        symbolTable,
                        update.updateLocation.name.id,
                        update
                );
            } else if (update.updateAssignExpr instanceof Increment) {
                Increment decrement = (Increment) update.updateAssignExpr;
                return convertToBinaryExpression(
                        update.updateLocation,
                        new ArithmeticOperator(decrement.tokenPosition, DecafScanner.PLUS),
                        new DecimalLiteral(decrement.tokenPosition, "1"),
                        symbolTable,
                        update.updateLocation.name.id,
                        update
                );
            } else {
                ThreeAddressCodeList updateLocationTAC = update.updateLocation.accept(this, symbolTable);
                updateExprTAC = update.updateAssignExpr.accept(this, symbolTable);
                CopyInstruction copyInstruction = new CopyInstruction(updateExprTAC.place, (AssignableName) updateLocationTAC.place, update);

                ThreeAddressCodeList tac = new ThreeAddressCodeList(updateLocationTAC.place);
                tac.add(updateLocationTAC);
                tac.add(updateExprTAC);
                tac.addCode(copyInstruction);
                return tac;
            }
        }
    }


    ThreeAddressCodesConverter visitor;
    Set<CFGBlock> visited = new HashSet<>();
    HashMap<CFGBlock, Label> blockToLabelHashMap = new HashMap<>();
    public HashMap<String, SymbolTable> cfgSymbolTables;
    final private static HashMap<String, StringLiteralStackAllocation> literalStackAllocationHashMap = new HashMap<>();

    public ThreeAddressCodeList dispatch(CFGBlock cfgBlock, SymbolTable symbolTable) {
        if (cfgBlock instanceof CFGNonConditional)
            return visit((CFGNonConditional) cfgBlock, symbolTable);
        else
            return visit((CFGConditional) cfgBlock, symbolTable);
    }

    private List<AbstractName> getLocals(ThreeAddressCodeList threeAddressCodeList) {
        Set<AbstractName> uniqueNames = new HashSet<>();
        for (ThreeAddressCode threeAddressCode: threeAddressCodeList)
            uniqueNames.addAll(threeAddressCode.getNames());
        List<AbstractName> assignableNames = uniqueNames.stream().filter((name -> ((name instanceof AssignableName)))).distinct().sorted(Comparator.comparing(Object::toString)).collect(Collectors.toList());
        System.out.println(assignableNames);
        return assignableNames;
    }

    private ThreeAddressCodeList convertMethodDefinition(MethodDefinition methodDefinition,
                                                         CFGBlock methodStart,
                                                         SymbolTable symbolTable) {

        TemporaryNameGenerator.reset();

        ThreeAddressCodeList threeAddressCodeList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
        MethodBegin methodBegin = new MethodBegin(methodDefinition);
        threeAddressCodeList.addCode(methodBegin);

        List<PushParameter> pushParams = visitor.flattenMethodDefinitionArguments(threeAddressCodeList, methodDefinition.methodDefinitionParameterList, symbolTable);

        threeAddressCodeList.add(dispatch(methodStart, symbolTable));

        for (int i = pushParams.size() - 1; i >= 0; i--)
            threeAddressCodeList.addCode(new PopParameter(pushParams.get(i).parameterName, methodDefinition.methodDefinitionParameterList.get(i)));
        threeAddressCodeList.addCode(new MethodEnd(methodDefinition));

        methodBegin.setLocals(getLocals(threeAddressCodeList));

        return threeAddressCodeList;
    }

    private MethodDefinition getMethodDefinitionFromProgram(String name, Program program) {
        for (MethodDefinition methodDefinition : program.methodDefinitionList) {
            if (methodDefinition.methodName.id.equals(name)) {
                return methodDefinition;
            }
        }
        throw new IllegalStateException("expected to find method " + name);
    }

    private ThreeAddressCodeList fillOutGlobals(List<FieldDeclaration> fieldDeclarationList, SymbolTable symbolTable) {
        ThreeAddressCodeList threeAddressCodeList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
        for (FieldDeclaration fieldDeclaration: fieldDeclarationList) {
            for (Name name: fieldDeclaration.names) {
                threeAddressCodeList.addCode(new DataSectionAllocation(name, " <<<< " + name.getSourceCode(), new VariableName(name.id, symbolTable.getDescriptorFromValidScopes(name.id).get().type.getFieldSize()), fieldDeclaration.builtinType.getFieldSize(), fieldDeclaration.builtinType));
            }
            for (Array array: fieldDeclaration.arrays) {
                threeAddressCodeList.addCode(new DataSectionAllocation(array, " <<<< " + array.getSourceCode(), new VariableName(array.id.id, symbolTable.getDescriptorFromValidScopes(array.id.id).get().type.getFieldSize()), (int) (fieldDeclaration.builtinType.getFieldSize() * array.size.convertToLong()), fieldDeclaration.builtinType));
            }
        }
        return threeAddressCodeList;
    }


    private ThreeAddressCodeList initProgram(Program program, CFGNonConditional initialGlobalBlock, SymbolTable symbolTable) {
        ThreeAddressCodeList threeAddressCodeList = fillOutGlobals(program.fieldDeclarationList, symbolTable);
        Set<String> stringLiteralList = findAllStringLiterals(program);
        for (String stringLiteral : stringLiteralList) {
            final StringLiteralStackAllocation literalStackAllocation = new StringLiteralStackAllocation(stringLiteral);
            threeAddressCodeList.addCode(literalStackAllocation);
            literalStackAllocationHashMap.put(stringLiteral, literalStackAllocation);
        }
        return threeAddressCodeList;
    }

    private Set<String> findAllStringLiterals(Program program) {
        Set<String> literalList = new HashSet<>();
        Stack<AST> toExplore = new Stack<>();
        toExplore.addAll(program.methodDefinitionList);
        while (!toExplore.isEmpty()) {
            AST node = toExplore.pop();
            if (node instanceof StringLiteral)
                literalList.add(((StringLiteral) node).literal);
            else {
                for (Pair<String, AST> astPair : node.getChildren()) {
                    toExplore.add(astPair.second());
                }
            }
        }
        return literalList;
    }

    public ThreeAddressCodeList fill(iCFGVisitor visitor,
                                     Program program) {
        ThreeAddressCodeList threeAddressCodeList = initProgram(program, visitor.initialGlobalBlock, cfgSymbolTables.get("main"));
        threeAddressCodeList.add(convertMethodDefinition(getMethodDefinitionFromProgram("main", program), visitor.methodCFGBlocks.get("main"), cfgSymbolTables.get("main")));
        visitor.methodCFGBlocks.forEach((k, v) -> {
            if (!k.equals("main")) {
                threeAddressCodeList.add(convertMethodDefinition(getMethodDefinitionFromProgram(k, program), v, cfgSymbolTables.get(k)));
            }
        });
        return threeAddressCodeList;
    }

    public ThreeAddressCodesListConverter(GlobalDescriptor globalDescriptor) {
        this.visitor = new ThreeAddressCodesConverter();
        CFGSymbolTableConverter cfgSymbolTableConverter = new CFGSymbolTableConverter(globalDescriptor);
        this.cfgSymbolTables = cfgSymbolTableConverter.createCFGSymbolTables();
    }

    @Override
    public ThreeAddressCodeList visit(CFGNonConditional cfgNonConditional, SymbolTable symbolTable) {
        visited.add(cfgNonConditional);
//        TemporaryNameGenerator.reset();
        ThreeAddressCodeList universalThreeAddressCodeList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
        for (CFGLine line : cfgNonConditional.lines) {
            universalThreeAddressCodeList.add(line.ast.accept(visitor, symbolTable));
        }
        if (cfgNonConditional.autoChild != null) {
            if (visited.contains(cfgNonConditional.autoChild)) {
                assert blockToLabelHashMap.containsKey(cfgNonConditional.autoChild);
                universalThreeAddressCodeList.addCode(new UnconditionalJump(blockToLabelHashMap.get(cfgNonConditional.autoChild)));
            } else {
                universalThreeAddressCodeList.add(cfgNonConditional.autoChild.accept(this, symbolTable));
            }
        }
        return universalThreeAddressCodeList;
    }

    private Label getLabel(CFGBlock cfgBlock, Boolean isConditional, Label from) {
        BiFunction<CFGBlock, Label, Label> function = (cfgBlock1, label) -> {
            if (label == null) {
                return new Label(TemporaryNameGenerator.getNextLabel(), cfgBlock1);
            } else if (isConditional) {
                return label;
            } else {
                label.aliasLabels.add(from.label + "_False");
            }
            return label;
        };
        return blockToLabelHashMap.compute(cfgBlock, function);
    }

    @Override
    public ThreeAddressCodeList visit(CFGConditional cfgConditional, SymbolTable symbolTable) {
        visited.add(cfgConditional);
        Expression condition = (Expression) (cfgConditional.condition).ast;
        ThreeAddressCodeList universalThreeAddressCodeList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
        ThreeAddressCodeList testConditionThreeAddressList;

        if (condition instanceof BinaryOpExpression)
            testConditionThreeAddressList = visitor.visit((BinaryOpExpression) condition, symbolTable);
        else if (condition instanceof UnaryOpExpression)
            testConditionThreeAddressList = visitor.visit((UnaryOpExpression) condition, symbolTable);
        else if (condition instanceof MethodCall)
            testConditionThreeAddressList = visitor.visit((MethodCall) condition, symbolTable);
        else if (condition instanceof LocationVariable)
            testConditionThreeAddressList = visitor.visit((LocationVariable) condition, symbolTable);
        else if (condition instanceof ParenthesizedExpression)
            testConditionThreeAddressList = visitor.visit((ParenthesizedExpression) condition, symbolTable);
        else throw new IllegalStateException("an expression of type " + condition + " is not allowed");

        final Label conditionalLabel = getLabel(cfgConditional, true, null);
        final Label falseLabel = getLabel(cfgConditional.falseChild, false, conditionalLabel);

        universalThreeAddressCodeList.addCode(conditionalLabel);
        universalThreeAddressCodeList.add(testConditionThreeAddressList);

        ThreeAddressCodeList falseBlock = null, trueBlock = null;
        if (cfgConditional.trueChild != null && !visited.contains(cfgConditional.trueChild))
            trueBlock = cfgConditional.trueChild.accept(this, symbolTable);
        if (cfgConditional.falseChild != null && !visited.contains(cfgConditional.falseChild))
            falseBlock = cfgConditional.falseChild.accept(this, symbolTable);

        Label endLabel = new Label(conditionalLabel.label + "_End", cfgConditional);

        universalThreeAddressCodeList.addCode(
                new JumpIfFalse(cfgConditional.condition.ast, testConditionThreeAddressList.place, falseLabel, "if !(" + cfgConditional.condition.ast.getSourceCode() + ")"));
        boolean endLabelAdded = false;

        if (trueBlock != null) {
            universalThreeAddressCodeList.add(trueBlock);
            if (falseBlock != null) {
                universalThreeAddressCodeList.addCode(new UnconditionalJump(endLabel));
                endLabelAdded = true;
            }
        }
        if (falseBlock != null) {
            // no need for consecutive similar labels
            if (!falseBlock.isEmpty() && falseBlock.get(0) instanceof Label) {
                String label = ((Label) falseBlock.get(0)).label;
                if (!label.equals(falseLabel.label))
                    universalThreeAddressCodeList.addCode(falseLabel);
            } else {
                universalThreeAddressCodeList.addCode(falseLabel);
            }
            universalThreeAddressCodeList.add(falseBlock);
        }
        if (endLabelAdded)
            universalThreeAddressCodeList.addCode(endLabel);
        return universalThreeAddressCodeList;
    }

    @Override
    public ThreeAddressCodeList visit(NOP nop, SymbolTable symbolTable) {
        throw new IllegalStateException("There should be no NOPs at this point, call NopVisitor to deal with it");
    }
}
