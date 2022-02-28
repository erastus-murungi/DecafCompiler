package edu.mit.compilers.grammar;

import edu.mit.compilers.exceptions.DecafException;
import edu.mit.compilers.exceptions.DecafParserException;
import edu.mit.compilers.parse.*;
import edu.mit.compilers.utils.Utils;
import edu.mit.compilers.utils.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static edu.mit.compilers.grammar.TokenType.*;

public class DecafParser {
    private final DecafScanner scanner;

    private boolean error = false;
    private boolean showTrace = false;
    private ArrayList<Token> tokens;
    private int currentTokenIndex;
    private Program root;

    public DecafParser(DecafScanner scanner) {
        this.scanner = scanner;
        try {
            this.tokens = getAllTokens();
        } catch (DecafException e) {
            e.printStackTrace();
            this.tokens = new ArrayList<>();
        }
        this.currentTokenIndex = 0;
    }

    private Token getCurrentToken() {
        return tokens.get(currentTokenIndex);
    }

    private TokenType getCurrentTokenType() {
        return getCurrentToken().tokenType();
    }

    private ArrayList<Token> getAllTokens() throws DecafException {
        ArrayList<Token> tokens = new ArrayList<>();
        Token token;
        do {
            token = scanner.nextToken();
            tokens.add(token);
        } while (token.isNotEOF());
        return tokens;
    }

    private Token consumeToken(TokenType expectedTokenType, String expected)
            throws DecafParserException {
        if (getCurrentToken().tokenType() != expectedTokenType) {
            String s = getCurrentToken().lexeme();
            if (getCurrentToken().tokenType().toString().startsWith("RESERVED"))
                s = "reserved keyword " + "\"" + s + "\"";
            throw getContextualException("expected " + "\"" + expected + "\"" + " received " + s);
        }
        Token token = getCurrentToken();
        currentTokenIndex += 1;
        return token;
    }

    private Token consumeTokenNoCheck() {
        Token token = getCurrentToken();
        currentTokenIndex += 1;
        return token;
    }

    private Token consumeToken(TokenType expectedTokenType)
            throws DecafParserException {
        return consumeToken(expectedTokenType, expectedTokenType.toString());
    }

    private Token consumeToken(TokenType expectedTokenType, Function<Token, String> getErrMessage)
            throws DecafParserException {
        if (getCurrentTokenType() != expectedTokenType) {
            final String errMessage = getErrMessage.apply(getCurrentToken());
            throw getContextualException(errMessage);
        }
        Token token = getCurrentToken();
        currentTokenIndex += 1;
        return token;
    }

    public boolean hasError() {
        return error;
    }

    private void processImportDeclarations(List<ImportDeclaration> importDeclarationList) throws DecafParserException {
        if (getCurrentToken().tokenType() == RESERVED_IMPORT)
            importDeclarationList.add(parseImportDeclaration());
        if (getCurrentToken().tokenType() == RESERVED_IMPORT) {
            processImportDeclarations(importDeclarationList);
        }
    }

    private void parseFieldDeclarations(List<FieldDeclaration> fieldDeclarationList) throws DecafParserException {
        if (getCurrentToken().tokenType() == RESERVED_BOOL ||
                getCurrentToken().tokenType() == RESERVED_INT) {
            do {
                fieldDeclarationList.add(parseFieldDeclaration());
            } while ((getCurrentToken().tokenType() == RESERVED_BOOL ||
                    getCurrentToken().tokenType() == RESERVED_INT));
            consumeToken(SEMICOLON, (Token t) -> {
                if (t.tokenType() == ASSIGN) {
                    return "initializers not allowed here";
                } else if (t.tokenType() == ID) {
                    return "expected \";\" but found " + DecafScanner.IDENTIFIER +
                            " : maybe missing a comma between variables in field decl?";
                } else {
                    return "expected " + DecafScanner.SEMICOLON + " received " + getCurrentTokenType().toString();
                }
            });
            if ((getCurrentToken().tokenType() == RESERVED_BOOL ||
                    getCurrentToken().tokenType() == RESERVED_INT)) {
                parseFieldDeclarations(fieldDeclarationList);
            }
        }
    }

    private void processFieldOrMethod(Program program) throws DecafParserException {
        if (getCurrentTokenType() == RESERVED_INT || getCurrentTokenType() == RESERVED_BOOL) {
            // could be an int or bool
            BuiltinType builtinType = parseBuiltinFieldType();
            Name nameId = parseName(DecafScanner.IDENTIFIER, ExprContext.DECLARE);
            if (getCurrentTokenType() == LEFT_PARENTHESIS) {
                // dealing with methodDeclarations
                List<MethodDeclarationArg> methodDeclarationArgList = parseMethodArguments();
                Block block = parseBlock();
                program.methodDefinitionList
                        .add(new MethodDefinition(builtinType, methodDeclarationArgList, nameId, block));
                parseMethodDeclarations(program.methodDefinitionList);
            } else {
                // dealing with fieldDeclarations
                List<Name> variables = new ArrayList<>();
                List<Array> arrays = new ArrayList<>();
                if (getCurrentToken().tokenType() == LEFT_SQUARE_BRACKET) {
                    Token t = consumeToken(LEFT_SQUARE_BRACKET);
                    IntLiteral intLiteral = parseIntLiteral("array size must be a int literal");
                    consumeToken(RIGHT_SQUARE_BRACKET,
                            (token -> "cannot declare array size with " + "\"" +
                                    scanner.getInputString().substring(t.tokenPosition().stringIndex() + 1,
                                            getCurrentToken().tokenPosition().stringIndex() + 1) +
                                    "\""));
                    arrays.add(new Array(intLiteral, nameId));
                } else {
                    variables.add(nameId);
                }

                while (getCurrentToken().tokenType() == COMMA) {
                    consumeToken(COMMA);
                    parseFieldDeclarationGroup(variables, arrays);
                }
                consumeToken(SEMICOLON, DecafScanner.SEMICOLON);
                program.fieldDeclarationList.add(new FieldDeclaration(builtinType, variables, arrays));
                processFieldOrMethod(program);
            }
        } else if (getCurrentTokenType() == RESERVED_VOID) {
            parseMethodDeclarations(program.methodDefinitionList);
        } else {
            if (getCurrentTokenType() == ID && currentTokenIndex + 1<tokens.size() &&
                    tokens.get(currentTokenIndex + 1).tokenType() == LEFT_PARENTHESIS) {
                throw getContextualException(
                        "method \"" + getCurrentToken().lexeme() + "\" does not have a return type");
            } else if (getCurrentTokenType() == ID) {
                throw getContextualException("field \"" + getCurrentToken().lexeme() + "\" does not have a type");
            }
        }
        if (getCurrentTokenType() != EOF) {
            throw getContextualException("extra token: \"" + getCurrentToken().lexeme() + "\" found after program end");
        }
    }

    private DecafParserException getContextualException(String errMessage) {
        return new DecafParserException(getCurrentToken(),
                scanner.getContextualErrorMessage(getCurrentToken().tokenPosition(), errMessage));
    }

    public final void program() {
        final Program program = new Program();
        try {
            processImportDeclarations(program.importDeclarationList);
            processFieldOrMethod(program);
        } catch (DecafException e) {
            e.printStackTrace();
            error = true;
            return;
        }
        root = program;
        if (showTrace)
            printParseTree();
    }

    private void parseMethodDeclarations(List<MethodDefinition> methodDefinitionList) throws DecafParserException {
        while (getCurrentToken().tokenType() == RESERVED_BOOL || getCurrentToken().tokenType() == RESERVED_INT ||
                getCurrentToken().tokenType() == RESERVED_VOID) {
            methodDefinitionList.add(parseMethodDeclaration());
        }
    }

    private BuiltinType parseMethodReturnType() throws DecafParserException {
        final Token token = consumeTokenNoCheck();
        return switch (token.tokenType()) {
            case RESERVED_BOOL, RESERVED_INT, RESERVED_VOID -> new BuiltinType(token.tokenPosition(), token.lexeme());
            default ->
                    throw new DecafParserException(getCurrentToken(), "expected method return type");
        };
    }

    private MethodDeclarationArg parseMethodArgument() throws DecafParserException {
        final BuiltinType builtinType = parseBuiltinFieldType();
        final Name nameId = parseName(DecafScanner.IDENTIFIER, ExprContext.DECLARE);
        return new MethodDeclarationArg(nameId, builtinType);
    }

    private void parseMethodArguments(List<MethodDeclarationArg> methodDeclarationArgList) throws DecafParserException {
        methodDeclarationArgList.add(parseMethodArgument());
        if (getCurrentToken().tokenType() == COMMA) {
            consumeToken(COMMA);
            parseMethodArguments(methodDeclarationArgList);
        }
    }

    private List<MethodDeclarationArg> parseMethodArguments() throws DecafParserException {
        consumeToken(LEFT_PARENTHESIS, (Token token) -> {
            if (token.tokenType() == COMMA || token.tokenType() == SEMICOLON ||
                    token.tokenType() == LEFT_SQUARE_BRACKET) {
                return "field decls must be first";
            } else {
                return "invalid method decl syntax: expected " + "\"" + DecafScanner.LEFT_PARENTHESIS + "\"" +
                        " received " + "\"" + token.lexeme() + "\"";
            }
        });
        if (getCurrentToken().tokenType() == RESERVED_INT || getCurrentToken().tokenType() == RESERVED_BOOL) {
            List<MethodDeclarationArg> methodDeclarationArgList = new ArrayList<>();
            parseMethodArguments(methodDeclarationArgList);
            consumeToken(RIGHT_PARENTHESIS);
            return methodDeclarationArgList;
        } else if (getCurrentTokenType() != RIGHT_PARENTHESIS) {
            if (getCurrentTokenType() == ID)
                throw getContextualException(
                        "method parameter " + "`" + getCurrentToken().lexeme() + "`" + " does not have a type");
            throw getContextualException("illegal method arg type: " + getCurrentToken().lexeme());
        }
        consumeToken(RIGHT_PARENTHESIS);
        return Collections.emptyList();
    }

    private MethodDefinition parseMethodDeclaration() throws DecafParserException {
        final BuiltinType methodReturnType = parseMethodReturnType();
        final Name nameId = parseName("method to have an identifier", ExprContext.DECLARE);

        List<MethodDeclarationArg> methodDeclarationArgList = parseMethodArguments();

        Block block = parseBlock();

        return new MethodDefinition(methodReturnType, methodDeclarationArgList, nameId, block);
    }

    private void parseMethodCallArguments(List<MethodCallArg> methodCallArgList) throws DecafParserException {
        if (getCurrentTokenType() == STRING_LITERAL) {
            final Token token = consumeTokenNoCheck();
            methodCallArgList.add(new StringLiteral(token.tokenPosition(), token.lexeme()));
        } else {
            methodCallArgList.add(new ExprArg(parseExpr()));
        }
        if (getCurrentTokenType() == COMMA) {
            consumeToken(COMMA);
            parseMethodCallArguments(methodCallArgList);
        }
    }

    private List<MethodCallArg> parseMethodCallArguments() throws DecafParserException {
        if (getCurrentTokenType() == RIGHT_PARENTHESIS)
            return Collections.emptyList();
        List<MethodCallArg> methodCallArgList = new ArrayList<>();
        parseMethodCallArguments(methodCallArgList);
        return methodCallArgList;
    }

    private MethodCall parseMethodCall(Token token) throws DecafParserException {
        consumeToken(LEFT_PARENTHESIS);
        List<MethodCallArg> methodCallArgList = parseMethodCallArguments();
        consumeToken(RIGHT_PARENTHESIS);
        return new MethodCall(new Name(token.lexeme(), token.tokenPosition(), ExprContext.LOAD), methodCallArgList);
    }

    private Statement parseLocationAndAssignExprOrMethodCall() throws DecafParserException {
        final Token token = consumeToken(ID, DecafScanner.IDENTIFIER);
        Statement statement;
        if (getCurrentToken().tokenType() == LEFT_PARENTHESIS) {
            statement = new MethodCallStatement(token.tokenPosition(), parseMethodCall(token));
        } else {
            statement = parseLocationAndAssignExpr(token);
        }
        consumeToken(SEMICOLON, DecafScanner.SEMICOLON);
        return statement;
    }

    private Expr parseLocationOrMethodCall() throws DecafParserException {
        final Token token = consumeToken(ID, DecafScanner.IDENTIFIER);
        if (getCurrentToken().tokenType() == LEFT_PARENTHESIS) {
            return parseMethodCall(token);
        } else {
            return parseLocation(token, ExprContext.LOAD);
        }
    }

    private LocationAssignExpr parseLocationAndAssignExpr(Token token) throws DecafParserException {
        final TokenPosition tokenPosition = getCurrentToken().tokenPosition();
        Location locationNode = parseLocation(token, ExprContext.STORE);
        AssignExpr assignExprNode = parseAssignExpr();
        return new LocationAssignExpr(tokenPosition, locationNode, assignExprNode);
    }

    private AssignOp parseAssignOp(TokenType tokenType) throws DecafParserException {
        Token token = consumeToken(tokenType, tokenType.toString());
        return switch (tokenType) {
            case ASSIGN, ADD_ASSIGN, MINUS_ASSIGN, MULTIPLY_ASSIGN -> new AssignOp(token.tokenPosition(), token.lexeme());
            default ->
                    throw getContextualException("expected compound assignOp");
        };
    }

    private CompoundAssignOp parseCompoundAssignOp(TokenType tokenType) throws DecafParserException {
        Token token = consumeToken(tokenType, tokenType.toString());
        return switch (tokenType) {
            case ADD_ASSIGN, MINUS_ASSIGN, MULTIPLY_ASSIGN -> new CompoundAssignOp(token.tokenPosition(), token.lexeme());
            default ->
                    throw getContextualException("expected compound assignOp");
        };
    }

    private AssignOpExpr parseAssignOpExpr(TokenType tokenType) throws DecafParserException {
        AssignOp assignOp = parseAssignOp(tokenType);
        Expr expr = parseExpr();
        return new AssignOpExpr(assignOp, expr);
    }

    private CompoundAssignOpExpr parseCompoundAssignOpExpr(TokenType tokenType) throws DecafParserException {
        CompoundAssignOp assignOp = parseCompoundAssignOp(tokenType);
        Expr expr = parseExpr();
        return new CompoundAssignOpExpr(assignOp, expr);
    }

    private Increment parseIncrement() throws DecafParserException {
        TokenType expectedTokenType = getCurrentToken().tokenType();
        consumeToken(expectedTokenType);
        if (expectedTokenType == INCREMENT)
            return new Increment(Increment.IncrementType.INCREMENT);
        else if (expectedTokenType == DECREMENT) {
            return new Increment(Increment.IncrementType.DECREMENT);
        } else {
            throw new IllegalStateException();
        }
    }

    private AssignExpr parseAssignExpr() throws DecafParserException {
        return switch (getCurrentToken().tokenType()) {
            case DECREMENT, INCREMENT -> parseIncrement();
            case ASSIGN, ADD_ASSIGN, MINUS_ASSIGN, MULTIPLY_ASSIGN -> parseAssignOpExpr(getCurrentToken().tokenType());
            default -> {
                if (tokens.get(currentTokenIndex - 1).tokenType() == ID) {
                    throw getContextualException(
                            "invalid type " + "\"" + tokens.get(currentTokenIndex - 1).lexeme() + "\"");
                }
                throw getContextualException("invalid assign_expr");
            }
        };
    }

    private AssignExpr parseCompoundAssignExpr() throws DecafParserException {
        return switch (getCurrentToken().tokenType()) {
            case DECREMENT, INCREMENT -> parseIncrement();
            case ADD_ASSIGN, MINUS_ASSIGN, MULTIPLY_ASSIGN -> parseCompoundAssignOpExpr(getCurrentToken().tokenType());
            default ->
                    throw new DecafParserException(getCurrentToken(), "expected compound_assign_expr");
        };
    }

    private LocationArray parseLocationArray(Token token) throws DecafParserException {
        Expr expr = parseExpr();
        final LocationArray locationArray = new LocationArray(new Name(token.lexeme(), token.tokenPosition(), ExprContext.STORE), expr);
        consumeToken(RIGHT_SQUARE_BRACKET);
        return locationArray;
    }

    private Location parseLocation(Token token, ExprContext exprContext) throws DecafParserException {
        if (getCurrentToken().tokenType() == LEFT_SQUARE_BRACKET) {
            consumeToken(LEFT_SQUARE_BRACKET);
            return parseLocationArray(token);
        }
        return new Location(new Name(token.lexeme(), token.tokenPosition(), exprContext));
    }

    private Expr parseUnaryOpExpr() throws DecafParserException {
        final Token unaryOpToken = consumeTokenNoCheck();
        return new UnaryOpExpr(new UnaryOp(unaryOpToken.tokenPosition(), unaryOpToken.lexeme()), parseExprHelper());
    }

    private Expr parseExpr() throws DecafParserException {
        Expr expr;
        try {
            expr = parseExprHelper();
            switch (getCurrentTokenType()) {
                case DIVIDE, MULTIPLY, MOD, CONDITIONAL_AND, CONDITIONAL_OR, PLUS, MINUS, LEQ, NEQ, EQ, GEQ, LT, GT -> expr = parseBinaryOpExpr(
                        expr);
                case SEMICOLON, RIGHT_SQUARE_BRACKET, RIGHT_PARENTHESIS, COMMA -> {}
                default ->
                        throw getContextualException("invalid expression");
            }
        } catch (IllegalStateException e) {
            expr = parseExprHelper();
        }
        return expr;
    }

    private BinOp parseBinOp() throws DecafParserException {
        final Token token = consumeTokenNoCheck();
        return switch (token.tokenType()) {
            case PLUS -> new ArithmeticOp(token.tokenPosition(), DecafScanner.PLUS);
            case MINUS -> new ArithmeticOp(token.tokenPosition(), DecafScanner.MINUS);
            case MULTIPLY -> new ArithmeticOp(token.tokenPosition(), DecafScanner.MULTIPLY);
            case DIVIDE -> new ArithmeticOp(token.tokenPosition(), DecafScanner.DIVIDE);
            case MOD -> new ArithmeticOp(token.tokenPosition(), DecafScanner.MOD);

            case EQ -> new EqualityOp(token.tokenPosition(), DecafScanner.EQ);
            case NEQ -> new EqualityOp(token.tokenPosition(), DecafScanner.NEQ);

            case LT -> new RelationalOp(token.tokenPosition(), DecafScanner.LT);
            case GT -> new RelationalOp(token.tokenPosition(), DecafScanner.GT);
            case GEQ -> new RelationalOp(token.tokenPosition(), DecafScanner.GEQ);
            case LEQ -> new RelationalOp(token.tokenPosition(), DecafScanner.LEQ);

            case CONDITIONAL_AND -> new ConditionalOp(token.tokenPosition(), DecafScanner.CONDITIONAL_AND);
            case CONDITIONAL_OR -> new ConditionalOp(token.tokenPosition(), DecafScanner.CONDITIONAL_OR);

            default ->
                    throw getContextualException("Unexpected binary operator: " + token.lexeme());
        };

    }

    private BinOpExpr parseBinaryOpExpr(Expr left) throws DecafParserException {
        BinOp binaryOp = parseBinOp();
        Expr right = parseExpr();
        return new BinOpExpr(left, binaryOp, right);
    }

    private Expr parseExprHelper() throws DecafParserException {
        return switch (getCurrentTokenType()) {
            case MINUS, NOT -> parseUnaryOpExpr();
            case CHAR_LITERAL, RESERVED_FALSE, RESERVED_TRUE, HEX_LITERAL, DECIMAL_LITERAL -> parseLiteral(
                    getCurrentTokenType());
            case RESERVED_LEN -> parseLen();
            case LEFT_PARENTHESIS -> parseParenthesizedExpression();
            case ID -> parseLocationOrMethodCall();
            default ->
                    throw new IllegalStateException();
        };
    }

    private Expr parseParenthesizedExpression() throws DecafParserException {
        consumeToken(LEFT_PARENTHESIS, DecafScanner.LEFT_PARENTHESIS);
        final Expr expr = parseExpr();
        consumeToken(RIGHT_PARENTHESIS, DecafScanner.RIGHT_PARENTHESIS);
        return new ParenthesizedExpr(expr);
    }

    private Len parseLen() throws DecafParserException {
        final TokenPosition tokenPosition = consumeToken(RESERVED_LEN, DecafScanner.RESERVED_LEN).tokenPosition();
        consumeToken(LEFT_PARENTHESIS, "expected a ( after " + DecafScanner.RESERVED_LEN);
        Name name = parseName((Token t) -> {
            if (t.tokenType() == RIGHT_PARENTHESIS) {
                return "cannot find len of nothing";
            } else {
                return "cannot find len of (..." + t.lexeme() + ")";
            }
        });
        consumeToken(RIGHT_PARENTHESIS, DecafScanner.RIGHT_PARENTHESIS);
        return new Len(tokenPosition, name);

    }

    private Literal parseLiteral(TokenType expectedLiteralType) throws DecafParserException {
        final Token token = consumeToken(expectedLiteralType);
        return switch (expectedLiteralType) {
            case CHAR_LITERAL -> new CharLiteral(token.tokenPosition(), token.lexeme());
            case RESERVED_FALSE -> new BooleanLiteral(token.tokenPosition(), DecafScanner.RESERVED_FALSE);
            case RESERVED_TRUE -> new BooleanLiteral(token.tokenPosition(), DecafScanner.RESERVED_TRUE);
            case HEX_LITERAL -> new HexLiteral(token.tokenPosition(), token.lexeme());
            case DECIMAL_LITERAL -> new DecimalLiteral(token.tokenPosition(), token.lexeme());
            default ->
                    throw new DecafParserException(getCurrentToken(), "unexpected literal");
        };
    }

    private Return parseReturnStatement() throws DecafParserException {
        final TokenPosition tokenPosition = consumeToken(RESERVED_RETURN, DecafScanner.RESERVED_RETURN).tokenPosition();
        final Expr expr = parseExpr();
        consumeToken(SEMICOLON, DecafScanner.SEMICOLON);
        return new Return(tokenPosition, expr);
    }

    private Statement parseWhileStatement() throws DecafParserException {
        final TokenPosition tokenPosition = consumeToken(RESERVED_WHILE).tokenPosition();
        consumeToken(LEFT_PARENTHESIS);
        final Expr expr = parseExpr();
        consumeToken(RIGHT_PARENTHESIS);
        final Block block = parseBlock();
        return new While(tokenPosition, expr, block);
    }

    private If parseIfStatement() throws DecafParserException {
        final TokenPosition tokenPosition = consumeToken(RESERVED_IF, DecafScanner.RESERVED_IF).tokenPosition();
        consumeToken(LEFT_PARENTHESIS, DecafScanner.LEFT_PARENTHESIS);
        Expr predicate;
        try {
            predicate = parseExpr();
        } catch (DecafException e) {
            if (getCurrentTokenType() == RIGHT_PARENTHESIS)
                throw getContextualException("if statement lacks a condition");
            throw e;
        }
        consumeToken(RIGHT_PARENTHESIS, DecafScanner.RIGHT_PARENTHESIS);
        Block ifBlock = parseBlock();
        Block elseBlock = null;
        if (getCurrentTokenType() == RESERVED_ELSE) {
            consumeToken(RESERVED_ELSE);
            elseBlock = parseBlock();
        }
        return new If(tokenPosition, predicate, ifBlock, elseBlock);
    }

    private Statement parseForStatement() throws DecafParserException {
        final TokenPosition tokenPosition = consumeToken(RESERVED_FOR, DecafScanner.RESERVED_FOR).tokenPosition();
        consumeToken(LEFT_PARENTHESIS, DecafScanner.LEFT_PARENTHESIS);

        final Name initId = parseName(DecafScanner.IDENTIFIER, ExprContext.STORE);

        consumeToken(ASSIGN, DecafScanner.ASSIGN);

        final Expr initializationExpr = parseExpr();

        consumeToken(SEMICOLON, DecafScanner.SEMICOLON);

        final Expr terminatingCondition = parseExpr();

        consumeToken(SEMICOLON, DecafScanner.SEMICOLON);

        final Location updatingLocation = parseLocation(consumeToken(ID, DecafScanner.IDENTIFIER), ExprContext.STORE);
        final AssignExpr updatingAssignExpr = parseCompoundAssignExpr();

        consumeToken(RIGHT_PARENTHESIS, DecafScanner.RIGHT_PARENTHESIS);

        final Block block = parseBlock();

        return new For(tokenPosition, initId, initializationExpr, terminatingCondition, updatingLocation, updatingAssignExpr,
                block);
    }

    private BreakOrContinue parseBreakOrContinue(TokenType tokenType) throws DecafParserException {
        if (tokenType == RESERVED_BREAK) {
            TokenPosition tokenPosition = consumeToken(RESERVED_BREAK).tokenPosition();
            consumeToken(SEMICOLON, DecafScanner.SEMICOLON);
            return new BreakOrContinue(tokenPosition, BreakOrContinue.Type.BREAK);
        } else if (tokenType == RESERVED_CONTINUE) {
            TokenPosition tokenPosition = consumeToken(RESERVED_CONTINUE).tokenPosition();
            consumeToken(SEMICOLON, DecafScanner.SEMICOLON);
            return new BreakOrContinue(tokenPosition, BreakOrContinue.Type.CONTINUE);
        } else {
            throw new DecafParserException(getCurrentToken(), "expected break or continue");
        }
    }

    private Statement parseStatement() throws DecafParserException {
        return switch (getCurrentTokenType()) {
            case RESERVED_BREAK, RESERVED_CONTINUE -> parseBreakOrContinue(getCurrentTokenType());
            case RESERVED_RETURN -> parseReturnStatement();
            case RESERVED_WHILE -> parseWhileStatement();
            case RESERVED_IF -> parseIfStatement();
            case RESERVED_FOR -> parseForStatement();
            default -> {
                if (getCurrentTokenType() == ID) {
                    yield parseLocationAndAssignExprOrMethodCall();
                } else {
                    throw new IllegalStateException();
                }
            }
        };
    }

    @SuppressWarnings("InfiniteLoopStatement")
    private void parseStatements(List<Statement> statementList) throws DecafParserException {
        try {
            while (true)
                statementList.add(parseStatement());
        } catch (IllegalStateException ignored) {}
    }

    private Block parseBlock() throws DecafParserException {
        consumeToken(LEFT_CURLY, DecafScanner.LEFT_CURLY);
        List<FieldDeclaration> fieldDeclarationList = new ArrayList<>();
        List<Statement> statementList = new ArrayList<>();
        parseFieldDeclarations(fieldDeclarationList);
        parseStatements(statementList);
        consumeToken(RIGHT_CURLY, DecafScanner.RIGHT_CURLY);
        return new Block(fieldDeclarationList, statementList);
    }

    private ImportDeclaration parseImportDeclaration() throws DecafParserException {
        consumeToken(RESERVED_IMPORT, DecafScanner.RESERVED_IMPORT);
        Name importName = parseName(DecafScanner.IDENTIFIER, ExprContext.IMPORT);
        consumeToken(SEMICOLON, DecafScanner.SEMICOLON);
        return new ImportDeclaration(importName);
    }

    private FieldDeclaration parseFieldDeclaration() throws DecafParserException {
        BuiltinType builtinType = parseBuiltinFieldType();
        List<Name> variables = new ArrayList<>();
        List<Array> arrays = new ArrayList<>();
        parseFieldDeclarationGroup(variables, arrays);

        while (getCurrentToken().tokenType() == COMMA) {
            consumeToken(COMMA, DecafScanner.COMMA);
            parseFieldDeclarationGroup(variables, arrays);
        }
        return new FieldDeclaration(builtinType, variables, arrays);
    }

    private void parseFieldDeclarationGroup(List<Name> variables, List<Array> arrays) throws DecafParserException {
        Name nameId = parseName(DecafScanner.IDENTIFIER, ExprContext.DECLARE);
        if (getCurrentToken().tokenType() == LEFT_SQUARE_BRACKET) {
            consumeToken(LEFT_SQUARE_BRACKET, DecafScanner.LEFT_SQUARE_BRACKET);
            final IntLiteral intLiteral = parseIntLiteral("invalid int literal");
            consumeToken(RIGHT_SQUARE_BRACKET, DecafScanner.RIGHT_SQUARE_BRACKET);
            arrays.add(new Array(intLiteral, nameId));
        } else {
            variables.add(nameId);
        }
    }

    private IntLiteral parseIntLiteral(String errMessage) throws DecafParserException {
        Token intLiteralToken;
        if (getCurrentToken().tokenType() == DECIMAL_LITERAL) {
            intLiteralToken = consumeTokenNoCheck();
            return new DecimalLiteral(intLiteralToken.tokenPosition(), intLiteralToken.lexeme());
        } else if (getCurrentToken().tokenType() == HEX_LITERAL) {
            intLiteralToken = consumeTokenNoCheck();
            return new HexLiteral(intLiteralToken.tokenPosition(), intLiteralToken.lexeme());
        } else {
            if (getCurrentTokenType() == RIGHT_SQUARE_BRACKET)
                throw getContextualException("missing array size");
            else
                throw getContextualException(errMessage);
        }
    }

    private Name parseName(String expected, ExprContext context) throws DecafParserException {
        final Token idToken = consumeToken(TokenType.ID, expected);
        return new Name(idToken.lexeme(), idToken.tokenPosition(), context);
    }

    private Name parseName(Function<Token, String> errMessageProvider)
            throws DecafParserException {
        final Token token = consumeToken(TokenType.ID, errMessageProvider);
        return new Name(token.lexeme(), token.tokenPosition(), ExprContext.LOAD);
    }

    private BuiltinType parseBuiltinFieldType() throws DecafParserException {
        final Token typeToken =
                switch (getCurrentToken().tokenType()) {
                    case RESERVED_INT -> consumeToken(RESERVED_INT, DecafScanner.RESERVED_INT);
                    case RESERVED_BOOL -> consumeToken(RESERVED_BOOL, DecafScanner.RESERVED_BOOL);
                    default ->
                            throw getContextualException(
                                    "expected " + "\"" +
                                            DecafScanner.RESERVED_INT + "\"" + " or " + "\"" +
                                            DecafScanner.RESERVED_BOOL + "\"");
                };
        return new BuiltinType(typeToken.tokenPosition(), typeToken.lexeme());
    }

    private void addTerminal(Pair<String, Node> labelAndNode, String prefix, String connector, List<String> tree) {
        if (!labelAndNode.second().isTerminal())
            throw new IllegalArgumentException();
        tree.add(prefix + connector +
                " " + Utils.coloredPrint(labelAndNode.first(), Utils.ANSIColorConstants.ANSI_BLUE) +
                " = " + labelAndNode.second());
    }

    private void addNonTerminal(Pair<String, Node> labelAndNode,
                                int index,
                                int numChildren,
                                String prefix,
                                String connector,
                                List<String> tree) {

        tree.add(prefix + connector +
                " " + Utils.coloredPrint(labelAndNode.first() +
                " = " + labelAndNode.second(), Utils.ANSIColorConstants.ANSI_PURPLE));

        if (index != numChildren - 1) {
            prefix += PrintConstants.PIPE_PREFIX;
        } else {
            prefix += PrintConstants.SPACE_PREFIX;
        }
        treeBody(labelAndNode, tree, prefix);
    }

    private void treeHead(List<String> tree) {
        tree.add(Utils.coloredPrint("Root", Utils.ANSIColorConstants.ANSI_GREEN));
        tree.add(PrintConstants.PIPE);
    }

    private void treeBody(Pair<String, Node> parentNode, List<String> tree, String prefix) {
        List<Pair<String, Node>> nodeList = parentNode.second().getChildren();
        for (int i = 0; i<nodeList.size(); i++) {
            final String connector = (i == nodeList.size() - 1) ? PrintConstants.ELBOW : PrintConstants.TEE;
            final Pair<String, Node> labelAndNode = nodeList.get(i);
            if (labelAndNode.second().isTerminal())
                addTerminal(labelAndNode, prefix, connector, tree);
            else
                addNonTerminal(labelAndNode, i, nodeList.size(), prefix, connector, tree);
        }
    }

    public void printParseTree() {
        List<String> tree = new ArrayList<>();
        treeHead(tree);
        treeBody(new Pair<>("program", root), tree, "");
        while (tree.get(tree.size() - 1).equals(""))
            tree.remove(tree.size() - 1);
        for (String s: tree) {
            System.out.println(s);
        }
    }

    public void setTrace(boolean showTrace) {
        this.showTrace = showTrace;
    }

    private static class PrintConstants {
        public static final String PIPE = "│";
        public static final String ELBOW = "└──";
        public static final String TEE = "├──";
        public static final String PIPE_PREFIX = "│   ";
        public static final String SPACE_PREFIX = "    ";
    }
}