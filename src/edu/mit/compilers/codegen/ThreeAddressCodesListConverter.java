package edu.mit.compilers.codegen;

import edu.mit.compilers.ast.*;
import edu.mit.compilers.ast.Assignment;
import edu.mit.compilers.ast.MethodCall;
import edu.mit.compilers.cfg.*;
import edu.mit.compilers.codegen.codes.*;
import edu.mit.compilers.codegen.codes.RuntimeException;
import edu.mit.compilers.codegen.names.*;
import edu.mit.compilers.descriptors.ArrayDescriptor;
import edu.mit.compilers.descriptors.Descriptor;
import edu.mit.compilers.exceptions.DecafException;
import edu.mit.compilers.ir.Visitor;
import edu.mit.compilers.symbolTable.SymbolTable;
import edu.mit.compilers.utils.Pair;
import edu.mit.compilers.utils.Utils;


import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class ThreeAddressCodesListConverter implements BasicBlockVisitor<ThreeAddressCodeList> {
    private final List<DecafException> errors;
    private Set<String> globals;

    private static class ThreeAddressCodesConverter implements Visitor<ThreeAddressCodeList> {
        HashMap<String, String> temporaryToStringLiteral;

        public ThreeAddressCodesConverter() {
            this.temporaryToStringLiteral = new HashMap<>();
        }

        @Override
        public ThreeAddressCodeList visit(IntLiteral intLiteral, SymbolTable symbolTable) {
            return new ThreeAddressCodeList(ConstantName.fromIntLiteral(intLiteral));
        }

        @Override
        public ThreeAddressCodeList visit(BooleanLiteral booleanLiteral, SymbolTable symbolTable) {
            TemporaryName temporaryVariable = TemporaryName.generateTemporaryName();
            return new ThreeAddressCodeList(
                    temporaryVariable,
                    Collections.singletonList(new Triple(temporaryVariable, "=", ConstantName.fromBooleanLiteral(booleanLiteral), booleanLiteral)));
        }

        @Override
        public ThreeAddressCodeList visit(DecimalLiteral decimalLiteral, SymbolTable symbolTable) {
            TemporaryName temporaryVariable = TemporaryName.generateTemporaryName();
            return new ThreeAddressCodeList(
                    temporaryVariable,
                    Collections.singletonList(new Triple(temporaryVariable, "=", ConstantName.fromIntLiteral(decimalLiteral), decimalLiteral)));
        }

        @Override
        public ThreeAddressCodeList visit(HexLiteral hexLiteral, SymbolTable symbolTable) {
            TemporaryName temporaryVariable = TemporaryName.generateTemporaryName();
            return new ThreeAddressCodeList(
                    temporaryVariable,
                    Collections.singletonList(new Triple(temporaryVariable, "=", ConstantName.fromIntLiteral(hexLiteral), hexLiteral)));
        }

        @Override
        public ThreeAddressCodeList visit(FieldDeclaration fieldDeclaration, SymbolTable symbolTable) {
            return new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
        }

        @Override
        public ThreeAddressCodeList visit(MethodDefinition methodDefinition, SymbolTable symbolTable) {
            throw new IllegalStateException("A method definition is illegal");
        }

        @Override
        public ThreeAddressCodeList visit(ImportDeclaration importDeclaration, SymbolTable symbolTable) {
            throw new IllegalStateException("An import statement is illegal");
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
            TemporaryName result = TemporaryName.generateTemporaryName();
            retTACList.addCode(new Triple(result, unaryOpExpression.op.getSourceCode(), operandTACList.place, unaryOpExpression));
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
            TemporaryName temporaryVariable = TemporaryName.generateTemporaryName();
            binOpExpressionTACList.addCode(
                    new Quadruple(
                            temporaryVariable, leftTACList.place, binaryOpExpression.op.getSourceCode(), rightTACList.place, binaryOpExpression.getSourceCode(), binaryOpExpression
                    ));
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


        private void addArrayAccessBoundsCheck(
                ThreeAddressCodeList threeAddressCodeList,
                ArrayAccess arrayAccess
        ) {

            final String boundsIndex = TemporaryNameGenerator.getNextBoundsCheckLabel();
            Label boundsBad = new Label("IndexIsLessThanArrayLengthCheckDone_" + boundsIndex, null);
            Label boundsGood = new Label("IndexIsNotNegativeCheckDone_" + boundsIndex, null);
            threeAddressCodeList.addCode(new ArrayBoundsCheck(null, arrayAccess, null, boundsBad, boundsGood));
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
                ArrayAccess arrayAccess = new ArrayAccess(locationArray, locationArray.getSourceCode(), new ArrayName(locationArray.name.id, arrayDescriptor.size * Utils.WORD_SIZE), new ConstantName(arrayDescriptor.size), locationThreeAddressCodeList.place);
                addArrayAccessBoundsCheck(threeAddressCodeList, arrayAccess);
                threeAddressCodeList.addCode(arrayAccess);
                threeAddressCodeList.place = arrayAccess.arrayName;
                return threeAddressCodeList;
            }
        }

        @Override
        public ThreeAddressCodeList visit(ExpressionParameter expressionParameter, SymbolTable symbolTable) {
            ThreeAddressCodeList expressionTACList = expressionParameter.expression.accept(this, symbolTable);
            ThreeAddressCodeList expressionParameterTACList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
            expressionParameterTACList.add(expressionTACList);
            if (expressionParameter.expression instanceof LocationVariable) {
                // no need for temporaries
                expressionParameterTACList.place = new VariableName(((Location) expressionParameter.expression).name.id);
            } else if (expressionParameter.expression instanceof IntLiteral) {
                expressionParameterTACList.place = new ConstantName(((IntLiteral) expressionParameter.expression).convertToLong());
            } else {
                TemporaryName temporaryVariable = TemporaryName.generateTemporaryName();
                expressionParameterTACList.addCode(new Triple(temporaryVariable, "=", expressionTACList.place, expressionParameter));
                expressionParameterTACList.place = temporaryVariable;
            }
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

        private void flattenMethodCallArguments(ThreeAddressCodeList threeAddressCodeList,
                                                List<MethodCallParameter> methodCallParameterList,
                                                SymbolTable symbolTable) {
            List<AbstractName> newParamNames = new ArrayList<>();
            for (MethodCallParameter methodCallParameter : methodCallParameterList) {
                ThreeAddressCodeList paramTACList = methodCallParameter.accept(this, symbolTable);
                threeAddressCodeList.add(paramTACList);
                newParamNames.add((paramTACList.place));
            }

            getPushParameterCode(threeAddressCodeList, newParamNames, methodCallParameterList);
        }

        private void getPushParameterCode(ThreeAddressCodeList threeAddressCodeList,
                                          List<AbstractName> newParamNames,
                                          List<? extends AST> methodCallOrDefinitionArguments) {
            for (int i = newParamNames.size() - 1; i >= 0; i--) {
                final PushParameter pushParameter = new PushParameter(newParamNames.get(i), i, methodCallOrDefinitionArguments.get(i));
                threeAddressCodeList.addCode(pushParameter);
                pushParameter.setComment("# index param = " + i);
            }
        }

        @Override
        public ThreeAddressCodeList visit(MethodCall methodCall, SymbolTable symbolTable) {
            ThreeAddressCodeList threeAddressCodeList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
            flattenMethodCallArguments(threeAddressCodeList, methodCall.methodCallParameterList, symbolTable);
            TemporaryName temporaryVariable = TemporaryName.generateTemporaryName();

            threeAddressCodeList.addCode(new MethodCallSetResult(methodCall, temporaryVariable, methodCall.getSourceCode()));
            threeAddressCodeList.place = temporaryVariable;
            return threeAddressCodeList;
        }


        @Override
        public ThreeAddressCodeList visit(MethodCallStatement methodCallStatement, SymbolTable symbolTable) {
            ThreeAddressCodeList threeAddressCodeList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
            flattenMethodCallArguments(threeAddressCodeList, methodCallStatement.methodCall.methodCallParameterList, symbolTable);
            threeAddressCodeList.addCode(new MethodCallSetResult(methodCallStatement.methodCall, methodCallStatement.getSourceCode()));
            return threeAddressCodeList;
        }

        @Override
        public ThreeAddressCodeList visit(LocationAssignExpr locationAssignExpr, SymbolTable symbolTable) {
            ThreeAddressCodeList lhs = locationAssignExpr.location.accept(this, symbolTable);
            AssignableName locationVariable = (AssignableName) lhs.place;
            ThreeAddressCodeList rhs = locationAssignExpr.assignExpr.accept(this, symbolTable);
            AbstractName valueVariable = rhs.place;
            lhs.add(rhs);
            lhs.addCode(new Triple(locationVariable, locationAssignExpr.getSourceCode(), valueVariable, locationAssignExpr));
            return lhs;
        }

        @Override
        public ThreeAddressCodeList visit(AssignOpExpr assignOpExpr, SymbolTable symbolTable) {
            throw new IllegalStateException("not allowed");
        }

        @Override
        public ThreeAddressCodeList visit(MethodDefinitionParameter methodDefinitionParameter, SymbolTable symbolTable) {
            return new ThreeAddressCodeList(new VariableName(methodDefinitionParameter.id.id));
        }

        @Override
        public ThreeAddressCodeList visit(Name name, SymbolTable symbolTable) {
            return new ThreeAddressCodeList(new VariableName(name.id));
        }

        @Override
        public ThreeAddressCodeList visit(LocationVariable locationVariable, SymbolTable symbolTable) {
            return new ThreeAddressCodeList(new VariableName(locationVariable.name.id));
        }

        @Override
        public ThreeAddressCodeList visit(Len len, SymbolTable symbolTable) {
            Optional<Descriptor> optionalDescriptor = symbolTable.getDescriptorFromValidScopes(len.nameId.id);
            if (optionalDescriptor.isEmpty())
                throw new IllegalStateException(len.nameId.id + " should be present");
            else {
                ArrayDescriptor arrayDescriptor = (ArrayDescriptor) optionalDescriptor.get();
                return new ThreeAddressCodeList(new ConstantName(arrayDescriptor.size));
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
            return new ThreeAddressCodeList(ConstantName.fromIntLiteral(charLiteral));
        }

        @Override
        public ThreeAddressCodeList visit(StringLiteral stringLiteral, SymbolTable symbolTable) {
            StringLiteralStackAllocation label = literalStackAllocationHashMap.get(stringLiteral.literal);
            StringConstantName stringConstantName = new StringConstantName(label);
            return new ThreeAddressCodeList(stringConstantName);
        }

        @Override
        public ThreeAddressCodeList visit(CompoundAssignOpExpr compoundAssignOpExpr, SymbolTable curSymbolTable) {
            return compoundAssignOpExpr.expression.accept(this, curSymbolTable);
        }

        @Override
        public ThreeAddressCodeList visit(Initialization initialization, SymbolTable symbolTable) {
            ThreeAddressCodeList initIdThreeAddressList = initialization.initId.accept(this, symbolTable);
            ThreeAddressCodeList initExpressionThreeAddressList = initialization.initExpression.accept(this, symbolTable);
            Triple copyInstruction = new Triple((AssignableName) initIdThreeAddressList.place, "=", initExpressionThreeAddressList.place, initialization);

            ThreeAddressCodeList initializationThreeAddressCodeList = new ThreeAddressCodeList(initIdThreeAddressList.place);
            initializationThreeAddressCodeList.add(initIdThreeAddressList);
            initializationThreeAddressCodeList.add(initExpressionThreeAddressList);
            initializationThreeAddressCodeList.addCode(copyInstruction);
            return initializationThreeAddressCodeList;
        }

        @Override
        public ThreeAddressCodeList visit(Assignment assignment, SymbolTable symbolTable) {
            ThreeAddressCodeList returnTACList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
            ThreeAddressCodeList lhs = assignment.location.accept(this, symbolTable);
            ThreeAddressCodeList rhs;
            if (assignment.assignExpr.expression != null) {
                rhs = assignment.assignExpr.expression.accept(this, symbolTable);
            } else {
                rhs = ThreeAddressCodeList.empty();
            }
            returnTACList.add(rhs);
            returnTACList.add(lhs);

            if (assignment.assignExpr instanceof AssignOpExpr) {
                returnTACList.addCode(new Triple((AssignableName) lhs.place, ((AssignOpExpr) assignment.assignExpr).assignOp.getSourceCode(), rhs.place, assignment));
            } else if (assignment.assignExpr instanceof CompoundAssignOpExpr) {
                returnTACList.addCode(new Triple((AssignableName) lhs.place, ((CompoundAssignOpExpr) assignment.assignExpr).compoundAssignOp.getSourceCode(), rhs.place, assignment));
            } else if (assignment.assignExpr instanceof Decrement || assignment.assignExpr instanceof Increment) {
                returnTACList.addCode(new Triple((AssignableName) lhs.place, assignment.assignExpr.getSourceCode(), lhs.place, assignment.assignExpr));
            } else {
                returnTACList.addCode(new Triple((AssignableName) lhs.place, "=", rhs.place, assignment));
            }
            returnTACList.place = lhs.place;
            return returnTACList;
        }
    }


    ThreeAddressCodesConverter visitor;
    Label endLabelGlobal;
    Set<BasicBlock> visited = new HashSet<>();
    HashMap<BasicBlock, Label> blockToLabelHashMap = new HashMap<>();
    HashMap<BasicBlock, ThreeAddressCodeList> blockToCodeHashMap = new HashMap<>();
    public HashMap<String, SymbolTable> cfgSymbolTables;
    final private static HashMap<String, StringLiteralStackAllocation> literalStackAllocationHashMap = new HashMap<>();

    public ThreeAddressCodeList dispatch(BasicBlock cfgBlock, SymbolTable symbolTable) {
        if (cfgBlock instanceof BasicBlockBranchLess)
            return visit((BasicBlockBranchLess) cfgBlock, symbolTable);
        else
            return visit((BasicBlockWithBranch) cfgBlock, symbolTable);
    }

    private List<AbstractName> getLocals(ThreeAddressCodeList threeAddressCodeList) {
        Set<AbstractName> uniqueNames = new HashSet<>();

        for (ThreeAddressCode threeAddressCode : threeAddressCodeList) {
            for (AbstractName name : threeAddressCode.getNames()) {
                if (name instanceof ArrayName && !globals.contains(name.toString())) {
                    uniqueNames.add(name);
                }
            }
        }

        for (ThreeAddressCode threeAddressCode : threeAddressCodeList) {
            for (AbstractName name : threeAddressCode.getNames()) {
                if (!(name instanceof ArrayName) && !globals.contains(name.toString())) {
                    uniqueNames.add(name);
                }
            }
        }
        return uniqueNames
                .stream()
                .filter((name -> ((name instanceof AssignableName))))
                .distinct()
                .sorted(Comparator.comparing(Object::toString))
                .collect(Collectors.toList());
    }

    private ThreeAddressCodeList convertMethodDefinition(MethodDefinition methodDefinition,
                                                         BasicBlock methodStart,
                                                         SymbolTable symbolTable) {

        TemporaryNameGenerator.reset();
        endLabelGlobal = new Label("LExit_" + methodDefinition.methodName.id, methodStart);

        ThreeAddressCodeList threeAddressCodeList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
        MethodBegin methodBegin = new MethodBegin(methodDefinition);
        threeAddressCodeList.addCode(methodBegin);
        if (!this.errors.isEmpty()) {
            for (DecafException error: errors) {
                threeAddressCodeList.addCode(new RuntimeException(error.getMessage(), -2, error));
            }
        }

        flattenMethodDefinitionArguments(threeAddressCodeList, methodDefinition.methodDefinitionParameterList);

        threeAddressCodeList.setNext(dispatch(methodStart, symbolTable));
        threeAddressCodeList = threeAddressCodeList.flatten();

        threeAddressCodeList.addCode(endLabelGlobal);

        threeAddressCodeList.addCode(new MethodEnd(methodDefinition));
        methodBegin.setLocals(getLocals(threeAddressCodeList));

        return threeAddressCodeList;
    }


    public void flattenMethodDefinitionArguments(ThreeAddressCodeList threeAddressCodeList,
                                                 List<MethodDefinitionParameter> methodDefinitionParameterList) {

        for (int i = 0; i < methodDefinitionParameterList.size(); i++) {
            MethodDefinitionParameter parameter = methodDefinitionParameterList.get(i);
            threeAddressCodeList.addCode(new PopParameter(
                    new VariableName(parameter.id.id),
                    parameter,
                    i,
                    "# index param = " + i
            ));
        }
    }

    private MethodDefinition getMethodDefinitionFromProgram(String name, Program program) {
        for (MethodDefinition methodDefinition : program.methodDefinitionList) {
            if (methodDefinition.methodName.id.equals(name)) {
                return methodDefinition;
            }
        }
        throw new IllegalStateException("expected to find method " + name);
    }

    private ThreeAddressCodeList fillOutGlobals(List<FieldDeclaration> fieldDeclarationList) {
        ThreeAddressCodeList threeAddressCodeList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
        for (FieldDeclaration fieldDeclaration : fieldDeclarationList) {
            for (Name name : fieldDeclaration.names) {
                threeAddressCodeList.addCode(new GlobalAllocation(name, "# " + name.getSourceCode(), new VariableName(name.id), fieldDeclaration.builtinType.getFieldSize(), fieldDeclaration.builtinType));
            }
            for (Array array : fieldDeclaration.arrays) {
                long size = (fieldDeclaration.builtinType.getFieldSize() * array.size.convertToLong());
                threeAddressCodeList.addCode(new GlobalAllocation(array, "# " + array.getSourceCode(),
                        new ArrayName(array.id.id,
                                size), size, fieldDeclaration.builtinType));
            }
        }
        return threeAddressCodeList;
    }


    private ThreeAddressCodeList initProgram(Program program) {
        ThreeAddressCodeList threeAddressCodeList = fillOutGlobals(program.fieldDeclarationList);
        this.globals = getGlobals(threeAddressCodeList);
        Set<String> stringLiteralList = findAllStringLiterals(program);
        for (String stringLiteral : stringLiteralList) {
            final StringLiteralStackAllocation literalStackAllocation = new StringLiteralStackAllocation(stringLiteral);
            threeAddressCodeList.addCode(literalStackAllocation);
            literalStackAllocationHashMap.put(stringLiteral, literalStackAllocation);
        }
        return threeAddressCodeList;
    }

    private Set<String> getGlobals(ThreeAddressCodeList threeAddressCodeList) {
        HashSet<String> set = new HashSet<>();
        for (ThreeAddressCode threeAddressCode : threeAddressCodeList) {
            for (AbstractName name : threeAddressCode.getNames()) {
                if (name instanceof VariableName)
                    set.add(name.toString());
            }
        }
        return set;
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
        ThreeAddressCodeList threeAddressCodeList = initProgram(program);
        threeAddressCodeList.add(
                convertMethodDefinition(
                        getMethodDefinitionFromProgram("main", program),
                        visitor.methodCFGBlocks.get("main"),
                        cfgSymbolTables.get("main"))
        );
        visitor.methodCFGBlocks.forEach((k, v) -> {
            if (!k.equals("main")) {
                threeAddressCodeList.add(convertMethodDefinition(getMethodDefinitionFromProgram(k, program), v, cfgSymbolTables.get(k)));
            }
        });
        return threeAddressCodeList;
    }

    public ThreeAddressCodesListConverter(CFGGenerator cfgGenerator) {
        this.errors = cfgGenerator.errors;
        this.visitor = new ThreeAddressCodesConverter();
        SymbolTableFlattener symbolTableFlattener = new SymbolTableFlattener(cfgGenerator.globalDescriptor);
        this.cfgSymbolTables = symbolTableFlattener.createCFGSymbolTables();
    }

    @Override
    public ThreeAddressCodeList visit(BasicBlockBranchLess basicBlockBranchLess, SymbolTable symbolTable) {
        visited.add(basicBlockBranchLess);
        ThreeAddressCodeList universalThreeAddressCodeList = new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
        for (CFGLine line : basicBlockBranchLess.lines) {
            universalThreeAddressCodeList.add(line.ast.accept(visitor, symbolTable));
        }
        blockToCodeHashMap.put(basicBlockBranchLess, universalThreeAddressCodeList);
        if (!(basicBlockBranchLess instanceof NOP) && !(basicBlockBranchLess.autoChild instanceof NOP)) {
            if (visited.contains(basicBlockBranchLess.autoChild)) {
                assert blockToLabelHashMap.containsKey(basicBlockBranchLess.autoChild);
                universalThreeAddressCodeList.setNext(ThreeAddressCodeList.of(new UnconditionalJump(blockToLabelHashMap.get(basicBlockBranchLess.autoChild))));
            } else {
                universalThreeAddressCodeList.setNext(basicBlockBranchLess.autoChild.accept(this, symbolTable));
            }
        } else {
            universalThreeAddressCodeList.setNext(ThreeAddressCodeList.of(new UnconditionalJump(endLabelGlobal)));
        }
        return universalThreeAddressCodeList;
    }

    private Label getLabel(BasicBlock cfgBlock, Label from) {
        if (cfgBlock instanceof NOP) {
            return endLabelGlobal;
        }
        BiFunction<BasicBlock, Label, Label> function = (cfgBlock1, label) -> {
            if (label == null) {
                return new Label(TemporaryNameGenerator.getNextLabel(), cfgBlock1);
            } else {
                label.aliasLabels.add(from == null ? "haha" : from.label + "_False");
            }
            return label;
        };
        return blockToLabelHashMap.compute(cfgBlock, function);
    }

    private ThreeAddressCodeList getConditionTACList(Expression condition, SymbolTable symbolTable) {
        if (condition instanceof BinaryOpExpression)
            return visitor.visit((BinaryOpExpression) condition, symbolTable);
        else if (condition instanceof UnaryOpExpression)
            return visitor.visit((UnaryOpExpression) condition, symbolTable);
        else if (condition instanceof MethodCall)
            return visitor.visit((MethodCall) condition, symbolTable);
        else if (condition instanceof LocationVariable)
            return visitor.visit((LocationVariable) condition, symbolTable);
        else if (condition instanceof ParenthesizedExpression)
            return visitor.visit((ParenthesizedExpression) condition, symbolTable);
        else throw new IllegalStateException("an expression of type " + condition + " is not allowed");
    }

    private ThreeAddressCodeList getConditionalChildBlock(
            BasicBlock child,
            Label conditionalLabel,
            SymbolTable symbolTable) {

        ThreeAddressCodeList codeList;
        ThreeAddressCodeList trueBlock;

        if (!(child instanceof NOP)) {
            if (visited.contains(child)) {
                codeList = blockToCodeHashMap.get(child);
                Label label;
                if (codeList.get(0) instanceof Label)
                    label = (Label) codeList.get(0);
                else {
                    label = getLabel(child, conditionalLabel);
                    codeList.prepend(label);
                }
                trueBlock = ThreeAddressCodeList.of(new UnconditionalJump(label));
            } else
                trueBlock = child.accept(this, symbolTable);
        } else {
            trueBlock = ThreeAddressCodeList.of(new UnconditionalJump(endLabelGlobal));
        }
        return trueBlock;
    }

    @Override
    public ThreeAddressCodeList visit(BasicBlockWithBranch basicBlockWithBranch, SymbolTable symbolTable) {
        visited.add(basicBlockWithBranch);

        Expression condition = (Expression) (basicBlockWithBranch.condition).ast;

        ThreeAddressCodeList testConditionThreeAddressList = getConditionTACList(condition, symbolTable);

        final Label conditionLabel = getLabel(basicBlockWithBranch, null);

        final ThreeAddressCodeList conditionLabelTACList = ThreeAddressCodeList.of(conditionLabel);
        conditionLabelTACList.add(testConditionThreeAddressList);

        blockToCodeHashMap.put(basicBlockWithBranch, conditionLabelTACList);

        final ThreeAddressCodeList trueBlock = getConditionalChildBlock(basicBlockWithBranch.trueChild, conditionLabel, symbolTable);
        final ThreeAddressCodeList falseBlock = getConditionalChildBlock(basicBlockWithBranch.falseChild, conditionLabel, symbolTable);


        Label falseLabel = getLabel(basicBlockWithBranch.falseChild, conditionLabel);
        Label endLabel = new Label(conditionLabel.label + "end", null);

        ConditionalJump jumpIfFalse =
                new ConditionalJump(condition,
                        testConditionThreeAddressList.place,
                        falseLabel, "if !(" + basicBlockWithBranch.condition.ast.getSourceCode() + ")");
        conditionLabelTACList.addCode(jumpIfFalse);
        if (!(trueBlock.last() instanceof UnconditionalJump))
            trueBlock.setNext(ThreeAddressCodeList.of(new UnconditionalJump(endLabel)));

        // no need for consecutive similar labels
        if (!falseBlock.isEmpty() && falseBlock.first() instanceof UnconditionalJump) {
            UnconditionalJump unconditionalJump = (UnconditionalJump) falseBlock.first();
            if (!unconditionalJump.goToLabel.label.equals(falseLabel.label))
                falseBlock.prepend(falseLabel);
        } else {
            falseBlock.prepend(falseLabel);
        }
        if (!(falseBlock.last() instanceof UnconditionalJump) && !(trueBlock.last() instanceof UnconditionalJump))
            falseBlock.setNext(ThreeAddressCodeList.of(new UnconditionalJump(endLabel)));
        if (falseBlock.flattenedSize() == 1 && falseBlock.first() instanceof UnconditionalJump) {
            conditionLabelTACList.setNext(trueBlock);
            return conditionLabelTACList;
        }
        conditionLabelTACList
                .setNext(trueBlock)
                .setNext(falseBlock);
        return conditionLabelTACList;
    }

    @Override
    public ThreeAddressCodeList visit(NOP nop, SymbolTable symbolTable) {
        return new ThreeAddressCodeList(ThreeAddressCodeList.UNDEFINED);
    }
}
