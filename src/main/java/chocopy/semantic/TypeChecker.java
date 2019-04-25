package chocopy.semantic;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.ClassValueType;
import chocopy.common.analysis.types.FuncType;
import chocopy.common.analysis.types.ListValueType;
import chocopy.common.analysis.types.SymbolType;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.AssignStmt;
import chocopy.common.astnodes.BinaryExpr;
import chocopy.common.astnodes.BooleanLiteral;
import chocopy.common.astnodes.CallExpr;
import chocopy.common.astnodes.ClassDef;
import chocopy.common.astnodes.Declaration;
import chocopy.common.astnodes.Errors;
import chocopy.common.astnodes.Expr;
import chocopy.common.astnodes.ExprStmt;
import chocopy.common.astnodes.ForStmt;
import chocopy.common.astnodes.FuncDef;
import chocopy.common.astnodes.Identifier;
import chocopy.common.astnodes.IfExpr;
import chocopy.common.astnodes.IfStmt;
import chocopy.common.astnodes.IndexExpr;
import chocopy.common.astnodes.IntegerLiteral;
import chocopy.common.astnodes.ListExpr;
import chocopy.common.astnodes.MemberExpr;
import chocopy.common.astnodes.MethodCallExpr;
import chocopy.common.astnodes.Node;
import chocopy.common.astnodes.NoneLiteral;
import chocopy.common.astnodes.Program;
import chocopy.common.astnodes.ReturnStmt;
import chocopy.common.astnodes.Stmt;
import chocopy.common.astnodes.StringLiteral;
import chocopy.common.astnodes.UnaryExpr;
import chocopy.common.astnodes.VarDef;
import chocopy.common.astnodes.WhileStmt;
import chocopy.semantic.errors.TypeErrorMessages;

import java.util.List;

import static chocopy.common.analysis.types.SymbolType.BOOL_TYPE;
import static chocopy.common.analysis.types.SymbolType.INT_TYPE;
import static chocopy.common.analysis.types.SymbolType.NONE_TYPE;
import static chocopy.common.analysis.types.SymbolType.OBJECT_TYPE;
import static chocopy.common.analysis.types.SymbolType.STR_TYPE;


/**
 * Analyzer that performs ChocoPy type checks on all nodes.  Applied after
 * collecting declarations.
 */
public class TypeChecker extends AbstractNodeAnalyzer<SymbolType> {

    /**
     * Current symbol table.  Changes with new declarative region.
     */
    private SymbolTable<DeclarationNode> sym;
    /**
     * Global symbol table.
     */
    private final SymbolTable<DeclarationNode> globals;
    private SymbolTable<DeclarationNode> classHierarchy;
    /**
     * Collector for errors.
     */
    private Errors errors;
    /**
     * The inheritance relationship between classes
     */
    private final InheritanceTable inheritanceTable;
    /**
     * Current return type for function. Changes with new declarative region.
     */
    private ValueType currentReturnType;
    /**
     * True if our analysis is working with definitions inside class.
     */
    private boolean atClassLevel;
    /**
     * Current MethodCallExpr that is the parent of current MemberExpr.
     * This is null, if MemberExpr is examined individually.
     */
    private Node currentMethodCallExpr;


    /**
     * Inserts an error message in NODE if there isn't one already.
     * The message is constructed with MESSAGE and ARGS as for
     * String.format.
     */
    private void err(Node node, String message, Object... args) {
        this.errors.semError(node, message, args);
    }

    /**
     * Creates a type checker using GLOBALSYMBOLS for the initial global
     * symbol table and ERRORS0 to receive semantic errors.
     */
    public TypeChecker(SymbolTable<DeclarationNode> globalSymbols,
                       Errors errors0,
                       InheritanceTable inheritanceTable) {
        this.sym = globalSymbols;
        this.globals = globalSymbols;
        this.errors = errors0;

        this.inheritanceTable = inheritanceTable;

        this.classHierarchy = null;
        this.atClassLevel = false;
        this.currentReturnType = null;
        this.currentMethodCallExpr = null;
    }

    @Override
    public SymbolType analyze(Program program) {
        for (Declaration decl : program.declarations) {
            decl.dispatch(this);
        }
        for (Stmt stmt : program.statements) {
            stmt.dispatch(this);
        }
        return null;
    }

    @Override
    public SymbolType analyze(ExprStmt s) {
        s.expr.dispatch(this);
        return null;
    }

    @Override
    public SymbolType analyze(AssignStmt s) {
        SymbolType valueType = s.value.dispatch(this);

        if (valueType == null) {
            return null;
        }

        if (s.targets.size() == 1) {
            // The rule ASSIGN-STMT
            Expr target = s.targets.get(0);
            this.analyzeTarget(s, valueType, target);
            return null;
        } else {
            // The rule MULTI-ASSIGN-STMT
            if (valueType.isListType() && NONE_TYPE.equals(valueType.elementType())) {
                this.err(s, TypeErrorMessages.MULTIASSIGN_NONE_LIST);
            }
            for (Expr target : s.targets) {
                this.analyzeTarget(s, valueType, target);
            }
        }

        return null;
    }

    private void analyzeTarget(AssignStmt n, SymbolType valueType, Expr target) {
        SymbolType targetElementType = target.dispatch(this);
        if (target instanceof IndexExpr) {
            SymbolType targetType = ((IndexExpr) target).list.getInferredType();
            if (!targetType.isListType()) {
                this.err(target, TypeErrorMessages.NOT_LIST_TYPE, targetType);
            }
        }

        if (!this.inheritanceTable.isCompatible((ValueType) valueType,
                (ValueType) targetElementType)) {
            this.err(n, TypeErrorMessages.NOT_EXPECTED_TYPE, targetElementType, valueType);
        }
    }

    @Override
    public SymbolType analyze(IntegerLiteral i) {
        return i.setInferredType(SymbolType.INT_TYPE);
    }

    @Override
    public SymbolType analyze(StringLiteral s) {
        return s.setInferredType(SymbolType.STR_TYPE);
    }

    @Override
    public SymbolType analyze(BooleanLiteral b) {
        return b.setInferredType(SymbolType.BOOL_TYPE);
    }

    @Override
    public SymbolType analyze(NoneLiteral n) {
        return n.setInferredType(SymbolType.NONE_TYPE);
    }

    @Override
    public SymbolType analyze(UnaryExpr n) {
        SymbolType operandType = n.operand.dispatch(this);

        switch (n.operator) {
            case "-":
                if (!INT_TYPE.equals(operandType)) {
                    this.err(n, TypeErrorMessages.CANNOT_APPLY_UNOP,
                            n.operator, operandType);
                }
                return n.setInferredType(INT_TYPE);
            case "not":
                if (!BOOL_TYPE.equals(operandType)) {
                    this.err(n, TypeErrorMessages.CANNOT_APPLY_UNOP,
                            n.operator, operandType);
                }
                return n.setInferredType(BOOL_TYPE);
            default:
                return n.setInferredType(OBJECT_TYPE);
        }
    }

    @Override
    public SymbolType analyze(ListExpr n) {
        if (n.elements.isEmpty()) {
            return n.setInferredType(SymbolType.EMPTY_TYPE);
        } else {
            Expr baseElement = n.elements.get(0);
            SymbolType baseJoin = baseElement.dispatch(this);
            for (Expr elem : n.elements) {
                SymbolType t = elem.dispatch(this);
                baseJoin = this.inheritanceTable.join((ValueType) baseJoin, (ValueType) t);
            }
            return n.setInferredType(new ListValueType(baseJoin));
        }
    }

    @Override
    public SymbolType analyze(BinaryExpr e) {
        SymbolType t1 = e.left.dispatch(this);
        SymbolType t2 = e.right.dispatch(this);

        switch (e.operator) {
            case "-":
            case "*":
            case "//":
            case "%":
                return this.analyzeUniformBinaryOperands(e, t1, t2, INT_TYPE, INT_TYPE);
            case "+":
                if (INT_TYPE.equals(t1) && INT_TYPE.equals(t2)) {
                    return e.setInferredType(INT_TYPE);
                } else if (STR_TYPE.equals(t1) && STR_TYPE.equals(t2)) {
                    return e.setInferredType(STR_TYPE);
                } else if (t1 != null && t2 != null &&
                        t1.isListType() && t2.isListType()) {
                    SymbolType joinedType =
                            this.inheritanceTable.join(t1.elementType(),
                                    t2.elementType());
                    return e.setInferredType(new ListValueType(joinedType));
                } else if (INT_TYPE.equals(t1) || INT_TYPE.equals(t2)) {
                    // result is inferred as INT, as long as one of them is
                    this.setBinaryError(e, t1, t2);
                    return e.setInferredType(INT_TYPE);
                } else {
                    // default is object type
                    this.setBinaryError(e, t1, t2);
                    return e.setInferredType(OBJECT_TYPE);
                }
            case "<":
            case "<=":
            case ">":
            case ">=":
                return this.analyzeUniformBinaryOperands(e, t1, t2, INT_TYPE, BOOL_TYPE);
            case "==":
            case "!=":
                if (t1 == null || !t1.equals(t2) ||
                        !t1.isSpecialType() || !t2.isSpecialType()) {
                    // we only allow == and != for special type: INT, BOOL, STR
                    this.setBinaryError(e, t1, t2);
                }
                return e.setInferredType(BOOL_TYPE);
            case "and":
            case "or":
                return this.analyzeUniformBinaryOperands(e, t1, t2, BOOL_TYPE, BOOL_TYPE);
            case "is":
                if (t1 == null || t2 == null || t1.isSpecialType() || t2.isSpecialType()) {
                    this.setBinaryError(e, t1, t2);
                }
                return e.setInferredType(BOOL_TYPE);
            default:
                return e.setInferredType(OBJECT_TYPE);
        }
    }

    @Override
    public SymbolType analyze(IfExpr e) {
        SymbolType condType = e.condition.dispatch(this);
        SymbolType thenType = e.thenExpr.dispatch(this);
        SymbolType elseType = e.elseExpr.dispatch(this);

        if (!BOOL_TYPE.equals(condType)) {
            this.err(e, TypeErrorMessages.CONDITION_EXPR_TYPE, condType);
        }

        ValueType bodyType = this.inheritanceTable.join((ValueType) thenType, (ValueType) elseType);
        return e.setInferredType(bodyType);
    }

    @Override
    public SymbolType analyze(IfStmt e) {
        SymbolType condType = e.condition.dispatch(this);

        if (!BOOL_TYPE.equals(condType)) {
            this.err(e, TypeErrorMessages.CONDITION_EXPR_TYPE, condType);
        }

        // then body is stmt list
        for (Stmt s : e.thenBody) {
            s.dispatch(this);
        }

        // else body is also a stmt list with possible nested if-stmts
        for (Stmt s : e.elseBody) {
            s.dispatch(this);
        }

        return null;
    }

    @Override
    public SymbolType analyze(WhileStmt e) {
        SymbolType condType = e.condition.dispatch(this);
        if (!BOOL_TYPE.equals(condType)) {
            this.err(e, TypeErrorMessages.CONDITION_EXPR_TYPE, condType);
        }
        for (Stmt s : e.body) {
            s.dispatch(this);
        }
        return null;
    }

     @Override
    public SymbolType analyze(ForStmt s) {
        SymbolType idValueType = s.identifier.dispatch(this);
        SymbolType iterableType = s.iterable.dispatch(this);

        if (s.identifier.hasError()) {
            // since now we can output multiple error messages, and
            // the new skeleton reports error on identifier on ForStmt itself
            // we report this error again, on this node, to minimize the
            // changes required
            this.err(s, s.identifier.getErrorMsg());
        }

        if (iterableType != null) {
            s.iterable.setInferredType(iterableType);
        }

        // if s.iterable is of type string
        if (STR_TYPE.equals(iterableType)) {
            if (!this.inheritanceTable.isCompatible(STR_TYPE, (ValueType) idValueType)) {
                this.err(s, TypeErrorMessages.NOT_EXPECTED_TYPE, idValueType, STR_TYPE);
            } else {
                s.identifier.setInferredType(STR_TYPE);
            }
        } else if (iterableType != null && iterableType.isListType()) {
            if (!this.inheritanceTable.isCompatible(iterableType.elementType(),
                    (ValueType) idValueType)) {
                this.err(s, TypeErrorMessages.NOT_EXPECTED_TYPE,
                        idValueType, iterableType.elementType());
            } else {
                s.identifier.setInferredType(iterableType.elementType());
            }
        } else {
            this.err(s, TypeErrorMessages.CANNOT_ITERATE_TYPE, iterableType);
        }

        // Type check the body of the for stmt
        for (Stmt st : s.body) {
            st.dispatch(this);
        }

        return null;
    }

    private SymbolType analyzeUniformBinaryOperands(BinaryExpr e,
                                                    SymbolType t1,
                                                    SymbolType t2,
                                                    ClassValueType expectedType,
                                                    ClassValueType returnType) {
        if (!expectedType.equals(t1) || !expectedType.equals(t2)) {
            this.setBinaryError(e, t1, t2);
        }
        return e.setInferredType(returnType);
    }

    private void setBinaryError(BinaryExpr e, SymbolType t1, SymbolType t2) {
        this.err(e, TypeErrorMessages.CANNOT_APPLY_BINOP, e.operator, t1, t2);
    }

    @Override
    public SymbolType analyze(Identifier id) {
        String varName = id.name;
        DeclarationNode varDecl = this.sym.get(varName);

        if (varDecl != null && varDecl.isValueType()) {
            return id.setInferredType(varDecl.getType());
        }

        this.err(id, TypeErrorMessages.NOT_A_VAR, varName);
        return id.setInferredType(ValueType.OBJECT_TYPE);
    }

    @Override
    public SymbolType analyze(IndexExpr n) {
        SymbolType indexee = n.list.dispatch(this);
        SymbolType indexer = n.index.dispatch(this);

        if (indexee == null) {
            return null;
        }

        SymbolType inferredType;

        if (indexee.isListType()) {
            inferredType = n.setInferredType(indexee.elementType());
        } else if (STR_TYPE.equals(indexee)) {
            inferredType = n.setInferredType(STR_TYPE);
        } else {
            this.err(n, TypeErrorMessages.CANNOT_INDEX_INTO_TYPE, indexee);
            inferredType = n.setInferredType(OBJECT_TYPE);
        }

        if (!INT_TYPE.equals(indexer)) {
            this.err(n, TypeErrorMessages.NONINTEGER_INDEX, indexer);
        }

        return inferredType;
    }

    @Override
    public SymbolType analyze(VarDef n) {
        return this.atClassLevel ? this.analyzeClassAttribute(n) : this.analyzeVarDef(n);
    }

    private SymbolType analyzeClassAttribute(VarDef n) {
        Identifier id = n.getIdentifier();
        SymbolType attributeType = this.classHierarchy.get(id.name).getType();
        SymbolType assignmentType = n.value.dispatch(this);
        if (!this.inheritanceTable.isCompatible((ValueType) assignmentType,
                (ValueType) attributeType)) {
            this.err(n, TypeErrorMessages.NOT_EXPECTED_TYPE, attributeType, assignmentType);
        }
        return null;
    }

    private SymbolType analyzeVarDef(VarDef n) {
        Identifier id = n.getIdentifier();
        String varName = id.name;
        SymbolType assignmentType = n.value.dispatch(this);

        DeclarationNode varDecl = this.sym.get(varName);
        SymbolType declarationType = varDecl.getType();
        if (!this.inheritanceTable.isCompatible((ValueType) assignmentType,
                (ValueType) declarationType)) {
            this.err(n, TypeErrorMessages.NOT_EXPECTED_TYPE, declarationType, assignmentType);
        }
        return null;
    }

    @Override
    public SymbolType analyze(ReturnStmt n) {
        SymbolType actualReturnType = null;
        if (n.value != null) {
            actualReturnType = n.value.dispatch(this);
        }

        if (this.currentReturnType == null) {
            return null;
        }

        if (actualReturnType == null) {
            if (!NONE_TYPE.equals(this.currentReturnType) && !OBJECT_TYPE.equals(this.currentReturnType)) {
                this.err(n, TypeErrorMessages.NO_RETURN, this.currentReturnType);
                return null;
            }
            actualReturnType = NONE_TYPE;
        }

        if (!actualReturnType.isValueType() || // cannot return functions, etc.
                !this.inheritanceTable.isCompatible((ValueType) actualReturnType, this.currentReturnType)) {
            this.err(n, TypeErrorMessages.NOT_EXPECTED_TYPE, this.currentReturnType, actualReturnType);
        }

        return null;

    }

    @Override
    public SymbolType analyze(FuncDef f) {
        String functionName = f.getIdentifier().name;

        DeclarationNode functionDecl = this.sym.get(functionName);
        // this ensures correct function overloading
        if (functionDecl == null && this.atClassLevel) {
            functionDecl = this.classHierarchy.get(functionName);
        }

        FuncType funcType = (FuncType) functionDecl.getType();
        ValueType funcReturnType = funcType.returnType;
        if (funcReturnType == null) {
            funcReturnType = NONE_TYPE;
        }

        // switch declarative region
        ValueType parentReturnType = this.currentReturnType;
        SymbolTable<DeclarationNode> parentSym = this.sym;
        boolean parentAtClassLevel = this.atClassLevel;

        this.currentReturnType = funcReturnType;
        this.sym = functionDecl.sym;
        this.atClassLevel = false;

        // recursively analyze function declarations and statements
        for (Declaration d : f.declarations) {
            d.dispatch(this);
        }

        for (Stmt s : f.statements) {
            s.dispatch(this);
        }

        // switch back to parent declarative region
        this.sym = parentSym;
        this.currentReturnType = parentReturnType;
        this.atClassLevel = parentAtClassLevel;

        return null;
    }

    @Override
    public SymbolType analyze(CallExpr n) {
        DeclarationNode callee = this.sym.get(n.function.name);

        List<Expr> passedParams = n.args;
        for (Expr passedParam : passedParams) {
            SymbolType passedType = passedParam.dispatch(this);
            passedParam.setInferredType(passedType);
        }

        if (callee == null) {
            this.err(n, TypeErrorMessages.NOT_A_FUNC_OR_CLASS, n.function.name);
            return n.setInferredType(OBJECT_TYPE);
        }

        // Class instantiation
        if (callee.isClassDef()) {
            ClassValueType classDefType = callee.classValueType();

            if (n.args.size() != 0) {
                this.err(n, TypeErrorMessages.WRONG_ARITY, 0, n.args.size());
            }

            return n.setInferredType(classDefType);
        } else if (callee.isFuncType()) {
            FuncType calleeType = (FuncType) callee.getType();
            n.function.setInferredType(calleeType);

            List<ValueType> params = calleeType.parameters;
            if (params.size() != passedParams.size()) {
                this.err(n, TypeErrorMessages.WRONG_ARITY, params.size(), passedParams.size());
            }

            for (int i = 0; i < passedParams.size(); i++) {
                SymbolType passedType = passedParams.get(i).getInferredType();
                if (i < params.size() &&
                        !this.inheritanceTable.isCompatible((ValueType) passedType,
                                params.get(i))) {
                    this.err(n, TypeErrorMessages.NOT_EXPECTED_TYPE_IN_PARAM,
                            params.get(i), passedType, i);
                }
            }

            return n.setInferredType(calleeType.returnType);
        } else {
            this.err(n, TypeErrorMessages.NOT_A_FUNC_OR_CLASS, n.function.name);
        }

        return n.setInferredType(OBJECT_TYPE);
    }

    @Override
    public SymbolType analyze(ClassDef n) {
        DeclarationNode classDecl = this.globals.get(n.getIdentifier().name);
        SymbolTable<DeclarationNode> classHierarchy =
                ClassUtilities.getBaseClassDeclaration(classDecl.classValueType(),
                        this.inheritanceTable,
                        this.globals);

        // switch declarative region
        SymbolTable<DeclarationNode> parentSym = this.sym;
        this.classHierarchy = classHierarchy;
        this.atClassLevel = true;
        this.sym = classDecl.sym;

        for (Declaration s : n.declarations) {
            s.dispatch(this);
        }

        this.atClassLevel = false;
        this.classHierarchy = null;
        this.sym = parentSym;

        return null;
    }

    @Override
    public SymbolType analyze(MemberExpr n) {
        // For MemberExpr inside MethodCallExpr, the errors should be reported
        // at MethodCallExpr Node.

        Node errorNode = n;
        boolean partOfMethodCall = (this.currentMethodCallExpr != null);

        if (partOfMethodCall) {
            errorNode = this.currentMethodCallExpr;
        }

        // clear currentMethodCallExpr, so if `object` is a `MemberExpr`
        // it won't be incorrectly affected by this MethodCallExpr
        this.currentMethodCallExpr = null;

        SymbolType objectType = n.object.dispatch(this);

        if (!(objectType instanceof ClassValueType)) {
            this.err(errorNode, TypeErrorMessages.ACCESS_MEMBER_OF_NONCLASS, objectType);
            return n.setInferredType(OBJECT_TYPE);
        }

        SymbolTable<DeclarationNode> classHierarchy =
                ClassUtilities.getBaseClassDeclaration((ClassValueType) objectType,
                        this.inheritanceTable,
                        this.globals);
        String memberName = n.member.name;
        DeclarationNode memberType = classHierarchy.get(memberName);


        if (partOfMethodCall) {
            if (memberType == null || !memberType.isFuncDef()) {
                // let the caller MethodCallExpr handles the error
                return n.setInferredType(OBJECT_TYPE);
            }
        } else {
            if (memberType == null || !memberType.isVarDef()) {
                this.err(errorNode, TypeErrorMessages.NO_SUCH_ATTRIBUTE, memberName, objectType.className());
                return n.setInferredType(OBJECT_TYPE);
            }
        }

        return n.setInferredType(memberType.getType());
    }


    @Override
    public SymbolType analyze(MethodCallExpr n) {
        this.currentMethodCallExpr = n;
        SymbolType calleeType = n.method.dispatch(this);

        // technically, MemberExpr already cleared it, but just to be safe
        this.currentMethodCallExpr = null;

        // we set inferred type for all arguments, regardless existences of errors
        for (int i = 0; i < n.args.size(); i++) {
            n.args.get(i).dispatch(this);
        }

        if (calleeType == null || !calleeType.isFuncType()) {
            this.err(n, TypeErrorMessages.NO_SUCH_METHOD, n.method.member.name, n.method.object.getInferredType());
            return n.setInferredType(OBJECT_TYPE);
        }

        if (n.hasError()) {
            return n.setInferredType(calleeType);
        }

        FuncType calleeFuncType = (FuncType) calleeType;
        int expectedArgSize = calleeFuncType.parameters.size() - 1;
        int providedArgSize = n.args.size();

        if (expectedArgSize != providedArgSize) {
            this.err(n, TypeErrorMessages.WRONG_ARITY, expectedArgSize, providedArgSize);
        }

        for (int i = 0; i < providedArgSize; i++) {
            SymbolType passedArgType = n.args.get(i).getInferredType();

            if (i < expectedArgSize) {
                ValueType expectedType = calleeFuncType.parameters.get(i + 1);
                if (!this.inheritanceTable.isCompatible((ValueType) passedArgType, expectedType)) {
                    this.err(n, TypeErrorMessages.NOT_EXPECTED_TYPE_IN_PARAM, expectedType, passedArgType, i + 1);
                }
            }
        }

        return n.setInferredType(calleeFuncType.returnType);

    }
}
