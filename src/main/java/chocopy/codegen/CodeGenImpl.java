package chocopy.codegen;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.SymbolType;
import chocopy.common.analysis.types.ClassValueType;
import chocopy.common.astnodes.AssignStmt;
import chocopy.common.astnodes.BinaryExpr;
import chocopy.common.astnodes.BooleanLiteral;
import chocopy.common.astnodes.CallExpr;
import chocopy.common.astnodes.Expr;
import chocopy.common.astnodes.ExprStmt;
import chocopy.common.astnodes.ForStmt;
import chocopy.common.astnodes.IfExpr;
import chocopy.common.astnodes.IndexExpr;
import chocopy.common.astnodes.MemberExpr;
import chocopy.common.astnodes.MethodCallExpr;
import chocopy.common.astnodes.ListExpr;
import chocopy.common.astnodes.Identifier;
import chocopy.common.astnodes.IfStmt;
import chocopy.common.astnodes.IntegerLiteral;
import chocopy.common.astnodes.Literal;
import chocopy.common.astnodes.NoneLiteral;
import chocopy.common.astnodes.ReturnStmt;
import chocopy.common.astnodes.Stmt;
import chocopy.common.astnodes.StringLiteral;
import chocopy.common.astnodes.UnaryExpr;
import chocopy.common.astnodes.WhileStmt;
import chocopy.common.codegen.ClassInfo;
import chocopy.common.codegen.CodeGenBase;
import chocopy.common.codegen.FuncInfo;
import chocopy.common.codegen.Label;
import chocopy.common.codegen.RiscVBackend;
import chocopy.common.codegen.RiscVBackend.Register;
import chocopy.common.codegen.StackVarInfo;
import chocopy.common.codegen.SymbolInfo;

import java.util.List;
import java.util.ArrayList;

import static chocopy.common.analysis.types.SymbolType.BOOL_TYPE;
import static chocopy.common.analysis.types.SymbolType.EMPTY_TYPE;
import static chocopy.common.analysis.types.SymbolType.INT_TYPE;
import static chocopy.common.analysis.types.SymbolType.NONE_TYPE;
import static chocopy.common.analysis.types.SymbolType.OBJECT_TYPE;
import static chocopy.common.analysis.types.SymbolType.STR_TYPE;
import static chocopy.common.codegen.RiscVBackend.Register.A0;
import static chocopy.common.codegen.RiscVBackend.Register.A1;
import static chocopy.common.codegen.RiscVBackend.Register.FP;
import static chocopy.common.codegen.RiscVBackend.Register.RA;
import static chocopy.common.codegen.RiscVBackend.Register.SP;
import static chocopy.common.codegen.RiscVBackend.Register.T0;
import static chocopy.common.codegen.RiscVBackend.Register.T1;
import static chocopy.common.codegen.RiscVBackend.Register.T2;
import static chocopy.common.codegen.RiscVBackend.Register.T3;
import static chocopy.common.codegen.RiscVBackend.Register.T4;
import static chocopy.common.codegen.RiscVBackend.Register.T5;
import static chocopy.common.codegen.RiscVBackend.Register.ZERO;

/**
 * This is where the main implementation of PA3 will live.
 *
 * A large part of the functionality has already been implemented
 * in the base class, CodeGenBase. Make sure to read through that
 * class, since you will want to use many of its fields
 * and utility methods in this class when emitting code.
 *
 * Also read the PDF spec for details on what the base class does and
 * what APIs it exposes for its sub-class (this one). Of particular
 * importance is knowing what all the SymbolInfo classes contain.
 */
public class CodeGenImpl extends CodeGenBase {

    public final int WORD_SIZE = backend.getWordSize();
    public final int MAX_CHAR_DECIMAL = 256;
    public final int ONE_CHAR_STR_OBJECT_SIZE = 20;

    /** An activation record for reading from and writing to stack. */
    protected final StackRecord record;

    /** A code generator emitting instructions to BACKEND. */
    public CodeGenImpl(RiscVBackend backend) {
        super(backend);
        record = new StackRecord(backend, globalSymbols);
    }

    /** Bad argument. */
    private final Label errorArg = new Label("error.ARG");
    /** Operation on None. */
    private final Label errorNone = new Label("error.None");
    /** Division by zero. */
    private final Label errorDiv = new Label("error.Div");
    /** Index out of bounds. */
    private final Label errorOob = new Label("error.OOB");
    /** Out of memory. */
    private final Label errorOom = new Label("error.OOM");
    /** Unimplemented operation. */
    private final Label errorNyi = new Label("error.NYI");

    /** List construction. */
    private final Label constlistLabel = new Label("conslist");
    /** List concatenation. */
    private final Label listconcatLabel = new Label("listcat");
    /** String comparison. */
    private final Label streqLabel = new Label("streql");
    /** String concatenation. */
    private final Label strcatLabel = new Label("strcat");
    /** Character Initialization. */
    private final Label charInitLabel = new Label("initchars");
    /** All one-character singles. */
    private final Label allChars = new Label("allChars");

    @Override
    protected void initAsmConstants() {
        super.initAsmConstants();
        backend.defineSym(".__list_header_words__", 4);
        backend.defineSym(".__string_header_words__", 4);
    }

    /**
     * Emits the top level of the program.
     *
     * This method is invoked exactly once, and is surrounded
     * by some boilerplate code that: (1) initializes the heap
     * before the top-level begins and (2) exits after the top-level
     * ends.
     *
     * You only need to generate code for statements.
     *
     * @param statements top level statements
     */
    protected void emitTopLevel(List<Stmt> statements) {
        StmtAnalyzer stmtAnalyzer = new StmtAnalyzer(null);

        backend.emitADDI(SP, SP, -2 * WORD_SIZE,
                         "Saved FP and saved RA (unused at top level).");

        backend.emitSW(ZERO, SP, 0, "Top saved FP is 0.");
        record.upSlot(); // slot 0 is saved RA

        backend.emitSW(ZERO, SP, 4, "Top saved RA is 0.");
        record.upSlot(); // slot 1 is saved dynamic lin

        backend.emitADDI(FP, SP, 2 * WORD_SIZE, "Set FP to previous SP.");

        Label exitLabel = this.generateLocalLabel();

        // initialize one character strings
        backend.emitJAL(charInitLabel, "Initialize one-character strings");

        for (Stmt stmt : statements) {
            stmt.dispatch(stmtAnalyzer);
        }

        backend.emitLocalLabel(exitLabel, "End of Program");
        backend.emitLI(A0, EXIT_ECALL, "Code for ecall: exit");
        backend.emitEcall(null);
    }

    /**
     * Emits the code for a function described by FUNCINFO.
     *
     * This method is invoked once per function and method definition.
     * At the code generation stage, nested functions are emitted as
     * separate functions of their own. So if function `bar` is nested within
     * function `foo`, you only emit `foo`'s code for `foo` and only emit
     * `bar`'s code for `bar`.
     */
    protected void emitUserDefinedFunction(FuncInfo funcInfo) {
        backend.emitGlobalLabel(funcInfo.getCodeLabel());
        StmtAnalyzer stmtAnalyzer = new StmtAnalyzer(funcInfo);

        // function prologue
        this.emitFunctionPrologue();
        record.upSlot(); // slot 0 is saved RA
        record.upSlot(); // slot 1 is saved dynamic link

        // reserve space on stack for locals
        int localsSize = funcInfo.getLocals().size()  * WORD_SIZE;
        if (localsSize != 0) {
            this.allocateStackSpace(localsSize, "Reserve space for locals");
        }

         // emit code to save local variables' literal values on stack
        for (StackVarInfo var : funcInfo.getLocals()) {
            String varName = var.getVarName();
            String pushComment = String.format("assign to local variable: %s", varName);

            // load variable value
            this.emitLoadLiteral(A0, var.getInitialValue());

            // save value on stack
            record.upSlot();
            int offset = this.getVarOffset(funcInfo, varName);
            backend.emitSW(A0, FP, offset, pushComment);
        }

        // generate code for function statements
        for (Stmt stmt : funcInfo.getStatements()) {
            stmt.dispatch(stmtAnalyzer);
        }

        // function epilogue
        backend.emitLocalLabel(stmtAnalyzer.epilogue, "Epilogue");
        this.emitFunctionEpilogue();

        // return to caller
        backend.emitJR(RA, "Return to caller");
    }

    /** An analyzer that encapsulates code generation for statments. */
    private class StmtAnalyzer extends AbstractNodeAnalyzer<Void> {
        /*
         * The symbol table has all the info you need to determine
         * what a given identifier 'x' in the current scope is. You can
         * use it as follows:
         *   SymbolInfo x = sym.get("x");
         *
         * A SymbolInfo can be one the following:
         * - ClassInfo: a descriptor for classes
         * - FuncInfo: a descriptor for functions/methods
         * - AttrInfo: a descriptor for attributes
         * - GlobalVarInfo: a descriptor for global variables
         * - StackVarInfo: a descriptor for variables allocated on the stack,
         *      such as locals and parameters
         *
         * Since the input program is assumed to be semantically
         * valid and well-typed at this stage, you can always assume that
         * the symbol table contains valid information. For example, in
         * an expression `foo()` you KNOW that sym.get("foo") will either be
         * a FuncInfo or ClassInfo, but not any of the other infos
         * and never null.
         *
         * The symbol table in funcInfo has already been populated in
         * the base class: CodeGenBase. You do not need to add anything to
         * the symbol table. Simply query it with an identifier name to
         * get a descriptor for a function, class, variable, etc.
         *
         * The symbol table also maps nonlocal and global vars, so you
         * only need to lookup one symbol table and it will fetch the
         * appropriate info for the var that is currently in scope.
         */

        /** Symbol table for my statements. */
        private SymbolTable<SymbolInfo> sym;

        /** Label of code that exits from procedure. */
        protected Label epilogue;

        /** The descriptor for the current function, or null at the top level. */
        private FuncInfo funcInfo;

        /** An analyzer for the function described by FUNCINFO0, which is null
         *  for the top level. */
        StmtAnalyzer(FuncInfo funcInfo0) {
            this.funcInfo = funcInfo0;
            if (funcInfo == null) {
                this.sym = globalSymbols;
            } else {
                this.sym = funcInfo.getSymbolTable();
            }
            this.epilogue = generateLocalLabel();
            record.setFunctionInfo(this.funcInfo); // set function
            record.setSlot(0); // reset activation record slot
        }

        @Override
        public Void analyze(ReturnStmt stmt) {
            if (stmt.value == null) {
                backend.emitLI(A0, 0, "No return value specified. Return NONE implicitly");
            } else {
                stmt.value.dispatch(this);
            }

            backend.emitJ(this.epilogue, "Encounter RETURN! Jump to function epilogue.");
            return null;
        }

        @Override
        public Void analyze(ExprStmt stmt) {
            stmt.expr.dispatch(this);
            return null;
        }

        @Override
        public Void analyze(BooleanLiteral literal) {
            emitLoadLiteral(A0, literal);
            return null;
        }

        @Override
        public Void analyze(IntegerLiteral literal) {
            emitLoadLiteral(A0, literal);
            return null;
        }

        @Override
        public Void analyze(NoneLiteral literal) {
            emitLoadLiteral(A0, literal);
            return null;
        }

        @Override
        public Void analyze(StringLiteral literal) {
            emitLoadLiteral(A0, literal);
            return null;
        }

        @Override
        public Void analyze(BinaryExpr expr) {
            if (expr == null) {
                return null;
            }

            if (this.isIntegerType(expr.getInferredType())) {
                this.emitIntegerBinaryExpr(expr);
            } else if (this.isBooleanType(expr.getInferredType())) {
                this.emitBooleanBinaryExpr(expr);
            } else if (this.isStringType(expr.getInferredType())) {
                this.emitStringBinaryExpr(expr);
            } else if (expr.getInferredType() != null && expr.getInferredType().isListType()) {
                this.emitListBinaryExpr(expr);
            }

            return null;
        }

        @Override
        public Void analyze(UnaryExpr expr) {
            SymbolType inferredType = expr.operand.getInferredType();
            if (inferredType.isSpecialType()) {
                if (INT_TYPE.equals(inferredType)) {
                    if (expr.operator.equals("-")) {
                        expr.operand.dispatch(this);
                        backend.emitSUB(A0, ZERO, A0, "Unary negation");
                    }
                } else if (BOOL_TYPE.equals(inferredType)) {
                    if (expr.operator.equals("not")) {
                        expr.operand.dispatch(this);
                        backend.emitXORI(A0, A0, 1, "Logical not");
                    }
                }
            }

            return null;
        }

        @Override
        public Void analyze(CallExpr expr) {
            SymbolInfo symInfo = this.sym.get(expr.function.name);
            if (symInfo instanceof ClassInfo) {
                this.emitNewClassInstance((ClassInfo) symInfo, expr);
            } else {
                assert symInfo instanceof FuncInfo;
                this.emitFunctionCall((FuncInfo) symInfo, expr.args);
            }
            return null;
        }

        @Override
        public Void analyze(IfStmt ifStmt) {
            Label elseLabel = generateLocalLabel();
            Label endLabel = generateLocalLabel();

            // evaluate condition expression
            ifStmt.condition.dispatch(this);
            backend.emitBEQZ(A0, elseLabel, "Branch to else if the condition is false.");

            // emit code for thenBody
            for (Stmt thenStmt : ifStmt.thenBody) {
                thenStmt.dispatch(this);
            }
            backend.emitJ(endLabel, "Jump to the end of the statement when the then body is complete.");

            // emit code for elseBody
            backend.emitLocalLabel(elseLabel, "Labels the start of the else body");
            for (Stmt elseStmt : ifStmt.elseBody) {
                elseStmt.dispatch(this);
            }

            // end of if-else statement
            backend.emitLocalLabel(endLabel, "Labels the end of if-else statement");

            return null;
        }

        @Override
        public Void analyze(IfExpr ifExpr) {
            Label elseLabel = generateLocalLabel();
            Label endLabel = generateLocalLabel();

            ifExpr.condition.dispatch(this);
            backend.emitBEQZ(A0, elseLabel, "Branch to else if the condition is false.");

            ifExpr.thenExpr.dispatch(this);
            backend.emitJ(endLabel, "Jump to the end of the statement when the then body is complete.");

            backend.emitLocalLabel(elseLabel, "Labels the start of the else expression.");
            ifExpr.elseExpr.dispatch(this);

            backend.emitLocalLabel(endLabel, "Labels the end of the if-else expression.");

            return null;
        }

        @Override
        public Void analyze(WhileStmt whileStmt) {
            Label startLoop = generateLocalLabel();
            Label endLoop = generateLocalLabel();

            backend.emitLocalLabel(startLoop, "Begin while-loop");

            // emit code for evaluation of condition expression
            whileStmt.condition.dispatch(this);

            // branch to the end of while loop, if condition becomes false
            backend.emitBEQZ(A0, endLoop, "If condition becomes false, exit while-loop.");

            // emit code for while body
            for (Stmt stmt : whileStmt.body) {
                stmt.dispatch(this);
            }

            // reevaluate condition
            backend.emitJ(startLoop, "Jump back to re-evaluate while-loop condition.");

            // end of while loop
            backend.emitLocalLabel(endLoop, "End of while loop");

            return null;
        }

        @Override
        public Void analyze(ForStmt forStmt) {
            SymbolType iterableType = forStmt.iterable.getInferredType();

            if (iterableType.isListType()) {
                this.emitForList(forStmt);
            } else if (this.isStringType(iterableType)) {
                this.emitForStr(forStmt);
            } else {
                throw new IllegalArgumentException(
                    "static analysis should ensure for loop iterable is list or string"
                );
            }

            return null;
        }

        @Override
        public Void analyze(AssignStmt stmt) {
            SymbolType rhsValueType = stmt.value.getInferredType();

            // evaluate RHS expression and store in A0
            stmt.value.dispatch(this);
            // save evaluated RHS value on stack
            record.upSlot();
            record.pushToStack(A0, "Push result of assignment RHS value to stack.");
            pushStackSpace("Reserve space for saved RHS value");

            /* Assignments can only be made to identifiers, index expressions,
             * and instance attributes */
            for (Expr target : stmt.targets) {
                String comment = "";

                // put address of target in T0
                if (target instanceof Identifier) {
                    Identifier id = (Identifier) target;
                    comment = String.format("Assign to var: %s", id.name);
                    // read address to identifier in T0
                    record.readVariableAddress(T0, id.name);
                } else if (target instanceof IndexExpr) {
                    IndexExpr indexExpr = (IndexExpr) target;
                    assert indexExpr.getInferredType().isListType()
                        : "static analysis must ensure only assignment to list index is allowed";
                    comment = "Set list element";
                    // get address of the list element in A0
                    this.emitGetListElement(indexExpr);
                    // move address to T0
                    backend.emitMV(T0, A0, "Move address to list element to A0");
                } else if (target instanceof MemberExpr) {
                    MemberExpr member = (MemberExpr) target;

                    // evaluate object expressions, and save result in A0
                    member.object.dispatch(this);
                    // advance to the address of the specific attribute, save in T0
                    record.readAttributeAddress(T0, A0, member);

                    // sanity check
                    if (member.object == null ||
                        member.object.getInferredType() == null ||
                        member.object.getInferredType().className() == null) {
                        throw new IllegalArgumentException(
                            "static analysis should ensure member expr works on object type"
                        );
                    }

                    String className = member.object.getInferredType().className();
                    comment = String.format("Set attribute: %s.$s",
                                             className, member.member.name);
                } else {
                    throw new IllegalArgumentException("invalid assignment target");
                }

                // retrieve RHS value
                record.readFromStack(A0, "Retrieve result of assignment RHS value.");
                // box the value in A0, if needed
                if (this.isObjectType(target.getInferredType())) {
                    // save T0
                    record.upSlot();
                    pushStackSpace("Reserve space for address pointer");
                    record.pushToStack(T0, "Push address of target to stack");
                    // objectify
                    this.emitObjectifyType(rhsValueType);
                    // restore T0
                    record.readFromStack(T0, "Read address of target from stack");
                    record.downSlot();
                    popStackSpace("Pop stack space for address pointer");
                }
                // assign value
                backend.emitSW(A0, T0, 0, comment);
            }

            // pop up saved RHS value from stack
            record.downSlot();
            popStackSpace("Pop off saved assignment RHS value");

            return null;
        }

        @Override
        public Void analyze(Identifier id) {
            assert this.sym.get(id.name) != null
                : "semantic analysis should ensure identifier exists";
            record.readVariable(A0, id.name);
            return null;
        }

        @Override
        public Void analyze(MemberExpr memberExpr) {
            Label nonNoneClassLabel = generateLocalLabel();

            // emit code to save address of the object in A0
            memberExpr.object.dispatch(this);

            // check against None
            backend.emitBNEZ(A0, nonNoneClassLabel, "Ensure class reference is not None");
            backend.emitJ(errorNone, "Cannot access members in NONE object instance");
            backend.emitLocalLabel(nonNoneClassLabel, "Proceed if the class is not None.");

            record.readAttribute(A0, A0, memberExpr);
            return null;
        }

        @Override
        public Void analyze(MethodCallExpr methodCall) {
            MemberExpr method = methodCall.method;

            if (method == null || method.object == null) {
                return null;
            } else if (!(method.object.getInferredType() instanceof ClassValueType)) {
                throw new IllegalArgumentException(
                    "static analysis should check that MethodCallExpr acts on object type"
                );
            }

            ClassValueType methodType = (ClassValueType) method.object.getInferredType();
            String className = methodType.className();
            ClassInfo classInfo = (ClassInfo) globalSymbols.get(className);

            if (classInfo == null) {
                throw new IllegalArgumentException(
                    "static analysis should ensure MethodCallExpr acts on existing objects"
                );
            }

            // load address to object into A0
            methodCall.method.object.dispatch(this);

            // get method's information
            String methodName = method.member.name;
            int methodInfoIndex = classInfo.getMethodIndex(methodName);
            FuncInfo methodInfo = classInfo.getMethods().get(methodInfoIndex);

            this.emitFunctionCall(methodInfo, A0, methodCall.args);
            return null;
        }

        @Override
        public Void analyze(ListExpr expr) {
            int listSize = expr.elements.size();
            int rewindSlots = 0;

            // push each list item to stack
            for (int i = 0; i < listSize; i++) {
                // generate code for list element
                expr.elements.get(i).dispatch(this);
                // push to stack
                record.upSlot();
                rewindSlots++;
                pushStackSpace(
                    String.format("Reserve stack space for list element %d", i)
                );
                record.pushToStack(A0, String.format("Push list element %d to stack", i));
            }

            // push list length
            record.upSlot();
            rewindSlots++;
            pushStackSpace("Reserve stack space for argument: list length");
            backend.emitLI(A0, listSize, "Load list length");
            record.pushToStack(A0, "Push list length to stack");

            // construct list
            backend.emitJAL(constlistLabel, "Construct list");

            // pop arguments
            if (rewindSlots != 0) {
                deallocateStackSpace(rewindSlots * WORD_SIZE, "Pop arguments off the stack");
                record.downSlot(rewindSlots);
            }

            return null;
        }

        @Override
        public Void analyze(IndexExpr expr) {
            if (expr == null || expr.list == null || expr.index == null) {
                return null;
            }

            SymbolType exprListType = expr.list.getInferredType();
            assert exprListType.isListType() || this.isStringType(exprListType)
                : "static analysis should ensure index expression acts on lists or strings";
            assert this.isIntegerType(expr.index.getInferredType())
                : "static analysis should ensure index is integer";

            if (exprListType.isListType()) {
                // get address of the list element
                this.emitGetListElement(expr);
                // read value of list element
                backend.emitLW(A0, A0, 0, "Get indexed list element");
            } else {
                // get the selected character
                this.emitGetStringElement(expr);
            }

            return null;
        }

        /*-----------------------------------------------------------*/
        /*                                                           */
        /*.                    MAJOR PROCEDURES                      */
        /*                                                           */
        /*-----------------------------------------------------------*/

        /*---------------- FUNCTION & METHOD CALLS  ------------------*/

        /** Emit code for a new class construction: A(). */
        private void emitNewClassInstance(ClassInfo classInfo, CallExpr expr) {
            if (classInfo.getClassName() == intClass.getClassName() ||
                classInfo.getClassName() == boolClass.getClassName()) {
                backend.emitMV(A0, ZERO, "Special cases: int and bool unboxed");
                return;
            } else if (classInfo.getClassName() == strClass.getClassName()) {
                backend.emitLA(A0, constants.getStrConstant(""), "Default string is empty");
                return;
            }

            // allocate a prototype for the class, and save its address in A0
            allocatePrototype(classInfo);

            // get offset to class initialization method
            int initMethodIndex = classInfo.getMethodIndex("__init__");
            FuncInfo initMethod = classInfo.getMethods().get(initMethodIndex);

            // save address to the allocated object instance on stack
            record.upSlot();
            record.pushToStack(A0, "Push pointer to object instance on stack.");
            pushStackSpace("Reserve space for the object pointer");

            // call __init__ method
            this.emitFunctionCall(initMethod, A0, new ArrayList<>());

            // restore pointer to the object instance
            record.readFromStack(A0, "Restore pointer to newly created object instance.");
            record.downSlot();
            popStackSpace("Pop the object pointer of the stack");
        }

        /** Emit code for a function call: foo(...), with function FUNCINFO
         *  and given lists of ARGS. */
        private void emitFunctionCall(FuncInfo funcInfo, List<Expr> args) {
            this.emitFunctionCall(funcInfo, null, args);
        }

        /**
         * Emit code for a function call: foo(...), with function FUNCINFO
         * and given lists of ARGS, and an optional register SELFRS containing
         * the pointer to the object for which the methods belong to.
         */
        private void emitFunctionCall(FuncInfo funcInfo, Register selfRs, List<Expr> args) {
            String funcName = funcInfo.getFuncName();

            List<String> params = funcInfo.getParams();
            assert params.size() == args.size()
                : "static analysis should ensure passed arguments are correct";

            int rewindSlots = 0;

            // push static link, if needed
            if (!this.isTopLevel(funcInfo)) {
                record.upSlot();
                rewindSlots++;

                this.emitStaticLink(T0, funcInfo);
                pushStackSpace("Move SP to reserve space for static link");
                record.pushToStack(T0, "Push static link on stack");
            }

            // arguments passed to method call contains implicit `self` already
            if (selfRs != null) {
                record.upSlot();
                rewindSlots++;
                pushStackSpace("Move SP to reserve space for `self` argument");
                record.pushToStack(selfRs, "Push implicit `self` argument to stack.");
                params = params.subList(1, params.size());
            }

            // push parameters for callee
            for (int i = 0; i < params.size(); i++) {
                /* RECURSION HYPOTHESIS
                 * ---------------------
                 * Recursive evaluations of expressions (and sub-expressions)
                 * will ensure the following invariants:
                 *
                 * 1. final result of expression is saved at $A0
                 * 2. the evaluation functions as basic stack machines and will
                 *    not modify values above the current SP
                 */

                // generate code for expression evaluation
                args.get(i).dispatch(this);

                // wrap/box int and boolean values if param is object type
                if (this.isObjectParam(funcInfo, params.get(i))) {
                    this.emitObjectifyType(args.get(i).getInferredType());
                }

                record.upSlot();
                rewindSlots++;
                pushStackSpace(String.format("Reserve space for argument %d on stack", i));
                record.pushToStack(A0, String.format("Push argument %d to stack", i));
            }

            // call function
            Label calleeLabel = funcInfo.getCodeLabel();
            backend.emitJAL(calleeLabel, String.format("Invoke function: %s", funcName));

            // pop arguments and static link
            if (rewindSlots != 0) {
                deallocateStackSpace(rewindSlots * WORD_SIZE,
                                "Pop arguments and static link off the stack");
                record.downSlot(rewindSlots);
            }

            if (this.isNoneType(funcInfo.getReturnType())) {
                backend.emitMV(A0, ZERO, "Return NONE implicitly");
            }

        }

        /*---------------- BINARY EXPRESSIONS  ------------------*/

        /** Emit code for arithmetic expression. */
        private void emitIntegerBinaryExpr(BinaryExpr expr) {
            /* There is no short-circuiting for integers. */

            // evaluate left expression, and save it on stack
            expr.left.dispatch(this);
            record.upSlot();
            record.pushToStack(A0, "Save result of left operand to stack.");
            pushStackSpace("Reserve space for saved left operand result");

            // evaluate right expression, and save result in A0
            expr.right.dispatch(this);
            record.readFromStack(T0, "Retrieve result of left operand.");
            record.downSlot();
            popStackSpace("Pop off saved left operand result");

            Label nonzeroDivisorLabel = generateLocalLabel();

            switch (expr.operator) {
                case "+":
                    backend.emitADD(A0, T0, A0, "Operator: addition");
                    break;
                case "-":
                    backend.emitSUB(A0, T0, A0, "Operator: subtraction");
                    break;
                case "*":
                    backend.emitMUL(A0, T0, A0, "Operator: multiplication");
                    break;
                case "//":
                    // we cannot use conditional jump as errorDiv code is too far
                    backend.emitBNEZ(A0, nonzeroDivisorLabel, "Ensure non-zero divisor");
                    backend.emitJ(errorDiv, "Go to error handler");

                    // perform division
                    backend.emitLocalLabel(nonzeroDivisorLabel, "Divisor is non-zero");

                    Label adjustSignLabel = generateLocalLabel();
                    Label endDivLabel = generateLocalLabel();

                    // normal division if two operands have same sign
                    backend.emitXOR(T1, T0, A0, "Check for same sign");
                    backend.emitBLTZ(T1, adjustSignLabel, "Adjust left operand if differing sign");
                    backend.emitDIV(A0, T0, A0, "Operator: division");
                    backend.emitJ(endDivLabel, null);

                    // adjust left operant if necessary
                    // this procedure is based on the reference implementation
                    backend.emitLocalLabel(adjustSignLabel, null);
                    backend.emitSLT(T1, ZERO, A0, "T1 = 1 if right > 0 else 0");
                    backend.emitADD(T1, T1, T1, "T1 = T1 * 2");
                    backend.emitADDI(T1, T1, -1, "T1 = 1 if right >=0 else -1");
                    backend.emitADD(T1, T0, T1, "Adjust left operand");
                    backend.emitDIV(T1, T1, A0, "Adjusted division, toward 0");
                    backend.emitADDI(A0, T1, -1, "Complete division for differing sign");

                    backend.emitLocalLabel(endDivLabel, "End of division");

                    break;
                case "%":
                    // we cannot use conditional jump as errorDiv code is too far
                    backend.emitBNEZ(A0, nonzeroDivisorLabel, "Ensure non-zero divisor");
                    backend.emitJ(errorDiv, "Go to error handler");

                    // perform modular math
                    backend.emitLocalLabel(nonzeroDivisorLabel, "Divisor is non-zero");
                    backend.emitREM(T1, T0, A0, "Operator: modulus/remainder");

                    // sign flip if necessary
                    Label noFlipLabel = generateLocalLabel();
                    backend.emitBEQZ(T1, noFlipLabel, "If no remainder, no adjustment");
                    backend.emitXOR(T2, T1, A0, "Check for differing sign");
                    backend.emitBGEZ(T2, noFlipLabel, "Don't adjust signs equal");
                    backend.emitADD(T1, T1, A0, "Adjust the sign");

                    // store result
                    backend.emitLocalLabel(noFlipLabel, "End of modulus operation");
                    backend.emitMV(A0, T1, "Store result");

                    break;
            }
        }

        /** Emit code for binary expression of boolean type. */
        private void emitBooleanBinaryExpr(BinaryExpr expr) {
            assert this.isBooleanType(expr.getInferredType())
                : "can only call boolean comparison routine for boolean expressions";

            // Always evaluate the left operand in booleans
            Label shortCircuitLabel = generateLocalLabel();

            // evaluate left expression, and save it in A0
            expr.left.dispatch(this);

            /* There is short-circuiting for boolean operators AND and OR. */
            switch (expr.operator) {
                case "and":
                    // short circuit if we have FALSE AND ___
                    backend.emitBEQZ(A0, shortCircuitLabel, "Short circuit 'and': left operand is False");
                    // evaluate right expression, and save result in A0
                    // note that, result of TRUE AND _X_ is always X
                    expr.right.dispatch(this);
                    // emit short circuit label
                    backend.emitLocalLabel(shortCircuitLabel, "Done evaluating 'and'");
                    return;
                case "or":
                    // short circuit if we have TRUE or  ___
                    backend.emitBNEZ(A0, shortCircuitLabel, "Short circuit 'or': left operand is True");
                    // evaluate right expression, and save result in A0
                    // note that, result of FALSE or  ___ is always X
                    expr.right.dispatch(this);
                    backend.emitLocalLabel(shortCircuitLabel, "Done evaluating 'or'");
                    return;
            }

            /* There is no short-circuiting for other boolean comparisons. */

            // save left expression result on stack
            record.upSlot();
            record.pushToStack(A0, "Save result of left operand to stack.");
            pushStackSpace("Reserve space for saved left operand result");

            // evaluate right expression, and save result in A0
            expr.right.dispatch(this);
            record.readFromStack(T0, "Retrieve result of left operand.");
            record.downSlot();
            popStackSpace("Pop off saved left operand result");

            // comparisons
            switch (expr.operator) {
                case "<":
                    backend.emitSLT(A0, T0, A0, "Operator: <"); // LEFT < RIGHT
                    break;
                case "<=":
                    // if RIGHT < LEFT is not true, then LEFT <= RIGHT
                    backend.emitSLT(A0, A0, T0, "Operator: <=");
                    backend.emitSEQZ(A0, A0, "Operator <= (..continued)");
                    break;
                case ">":
                    backend.emitSLT(A0, A0, T0, "Operator: >"); // RIGHT < LEFT
                    break;
                case ">=":
                    // if LEFT < RIGHT is not true, then LEFT >= RIGHT
                    backend.emitSLT(A0, T0, A0, "Operator: >=");
                    backend.emitSEQZ(A0, A0, "Operator: >= (..continued)");
                    break;
                case "==":
                    /* we only allow == and != for special type: INT, BOOL, STR */
                    assert expr.left.getInferredType().equals(expr.right.getInferredType())
                            : "static analysis should ensure boolean comparison is valid";

                    if (this.isBooleanType(expr.left.getInferredType())) {
                        backend.emitSNEZ(T1, T0, "Set T1 = 1 if T0 is 0");
                        backend.emitSEQZ(T2, A0, "Set T2 = 0 if A0 is 0");
                        // if T0 == A0, then after above two operations T1 != T2
                        // so T1 XOR T2 > 1, meaning they are equal
                        backend.emitXOR(A0, T1, T2, "Operator: ==");
                    } else if (this.isIntegerType(expr.left.getInferredType())) {
                        // if T0 == A0, then XOR == 0, so we negate it to 1 if XOR == 0
                        backend.emitXOR(A0, T0, A0, "Compare references");
                        backend.emitSEQZ(A0, A0, "Operator: ==");
                    } else if (this.isStringType(expr.left.getInferredType())) {
                        assert this.isStringType(expr.left.getInferredType()) &&
                               this.isStringType(expr.right.getInferredType())
                            : "static analysis should ensure string concatenation acts on strings";

                        // pass strings as arguments
                        allocateStackSpace(2 * WORD_SIZE, "Reserve space for two string pointers");
                        record.upSlot();
                        record.pushToStack(T0, "Save pointer to first string on stack.");
                        record.upSlot();
                        record.pushToStack(A0, "Save pointer to second string on stack.");

                        // call string comparison routine
                        backend.emitJAL(streqLabel, "Compare strings");

                        // restore stack
                        record.downSlot(2);
                        deallocateStackSpace(2 * WORD_SIZE, "Pop space for two string pointers");
                    } else {
                        throw new IllegalArgumentException("cannot apply != on non-special types");
                    }

                    break;
                case "!=":
                    /* we only allow == and != for special type: INT, BOOL, STR */
                    assert expr.left.getInferredType().equals(expr.right.getInferredType())
                            : "static analysis should ensure boolean comparison is valid";

                    if (this.isBooleanType(expr.left.getInferredType())) {
                        backend.emitSNEZ(T1, T0, "Set T1 = 1 if T0 is 0");
                        backend.emitSNEZ(T2, A0, "Set T2 = 1 if A0 is 0");
                        // if T0 != A0, then after above two operations T1 != T2 still
                        // so T1 XOR T2 > 1, meaning they are not equal
                        backend.emitXOR(A0, T1, T2, "Operator: !=");
                    } else if (this.isIntegerType(expr.left.getInferredType())) {
                        // if T0 != A0, then XOR > 0
                        backend.emitXOR(A0, T0, A0, "Compare references");
                        backend.emitSNEZ(A0, A0, "Operator: !=");
                    } else if (this.isStringType(expr.left.getInferredType())) {
                       assert this.isStringType(expr.left.getInferredType()) &&
                               this.isStringType(expr.right.getInferredType())
                            : "static analysis should ensure string concatenation acts on strings";

                        // pass strings as arguments
                        allocateStackSpace(2 * WORD_SIZE, "Reserve space for two string pointers");
                        record.upSlot();
                        record.pushToStack(T0, "Save pointer to first string on stack.");
                        record.upSlot();
                        record.pushToStack(A0, "Save pointer to second string on stack.");

                        // call string comparison routine
                        backend.emitJAL(streqLabel, "Compare strings");
                        // reverse value
                        backend.emitSEQZ(A0, A0, "If str1 == str2, then (str1 != str2) is false");

                        // restore stack
                        record.downSlot(2);
                        deallocateStackSpace(2 * WORD_SIZE, "Pop space for two string pointers");
                    } else {
                        throw new IllegalArgumentException("cannot apply != on non-special types");
                    }

                    break;
                case "is":
                    assert expr.left.getInferredType().equals(expr.right.getInferredType())
                            : "static analysis should ensure boolean comparison is valid";
                    assert !expr.left.getInferredType().isSpecialType()
                            : "static analysis should ensure `is` not applied on special types";

                    // if T0 == A0, then XOR == 0, so we negate it to 1 if XOR == 0
                    backend.emitXOR(A0, T0, A0, "Compare references");
                    backend.emitSEQZ(A0, A0, "Operator: is");

                    break;
                default:
                    break;
            }
        }

        /** Emit code for binary expression of string type. */
        private void emitStringBinaryExpr(BinaryExpr expr) {
            /* There is no short-circuiting for strings. */

            if (expr == null || expr.left == null || expr.right == null) {
                return;
            }

            assert this.isStringType(expr.left.getInferredType()) &&
                   this.isStringType(expr.right.getInferredType())
                : "static analysis should ensure string concatenation acts on strings";

            // evaluate left expression, and save it on stack
            expr.left.dispatch(this);
            record.upSlot();
            pushStackSpace("Reserve space for saved left operand result");
            record.pushToStack(A0, "Save result of left operand to stack.");

            // evaluate right expression, and save result in A0
            expr.right.dispatch(this);
            record.upSlot();
            pushStackSpace("Reserve space for saved right operand result");
            record.pushToStack(A0, "Save result of right operand to stack.");

            // call string concatenation routine
            backend.emitJAL(strcatLabel, "Concatenate the strings");

            // deallocate stack space
            deallocateStackSpace(2 * WORD_SIZE,
                                 "Pop left and right operand results from stack");
            record.downSlot(2);
        }

        /** Emit code for binary expression of list type. */
        private void emitListBinaryExpr(BinaryExpr expr) {
            /* There is no short-circuiting for lists. */

            if (expr == null || expr.left == null || expr.right == null) {
                return;
            }

            assert expr.left.getInferredType().isListType() &&
                   expr.right.getInferredType().isListType()
                : "static analysis should ensure list concatenation acts on lists";

            // evaluate left expression, and save it on stack
            expr.left.dispatch(this);
            record.upSlot();
            pushStackSpace("Reserve space for saved left operand result");
            record.pushToStack(A0, "Save result of left operand to stack.");

            // evaluate right expression, and save result in A0
            expr.right.dispatch(this);
            record.upSlot();
            pushStackSpace("Reserve space for saved right operand result");
            record.pushToStack(A0, "Save result of right operand to stack.");

            // call list concatenation routine
            backend.emitJAL(listconcatLabel, "Concatenate the lists");

            // deallocate stack space
            deallocateStackSpace(2 * WORD_SIZE,
                                 "Pop left and right operand results from stack");
            record.downSlot(2);
        }

        /*----------------------- INDEXING  -----------------------*/

        /** Emit code to fetch ADDRESS of the list element in an IndexExpr. */
        private void emitGetListElement(IndexExpr expr) {
            Label checkOOBLabel = generateLocalLabel();
            Label noErrorLabel = generateLocalLabel();

            // evaluate list expression, and save it on stack
            expr.list.dispatch(this);
            record.upSlot();
            record.pushToStack(A0, "Save list pointer on stack.");
            pushStackSpace("Reserve space for saved list pointer");

            // evaluate index expression, and save result in A0
            expr.index.dispatch(this);

            // restore list pointer
            record.readFromStack(A1, "Retrieve list pointer in A1.");
            record.downSlot();
            popStackSpace("Pop off saved list pointer from stack");

            // ensure list pointer is not None
            backend.emitBNEZ(A1, checkOOBLabel, "Ensure list is not None");
            backend.emitJ(errorNone, "Throw operation on None error.");

            // ensure index is within bound
            backend.emitLocalLabel(checkOOBLabel, "List is not none. Now check index bound");
            backend.emitLW(T0, A1, "@.__len__", "Load attribute: __len__");
            backend.emitBLTU(A0, T0, noErrorLabel, "Ensure 0 <= index < len");
            backend.emitJ(errorOob, "Throw index out of bound error");

            // list item selection
            backend.emitLocalLabel(noErrorLabel, "Index within bound, so compute list selection");
            backend.emitSLLI(A0, A0, 2, "Index in bytes");
            backend.emitADDI(T0, A1, "@.__elts__", "Set T0 to first list item in list object");
            backend.emitADD(A0, A0, T0, "Point A0 to the list element at given index");
        }

        /** Emit code to fetch ADDRESS of the string character in an IndexExpr. */
        private void emitGetStringElement(IndexExpr expr) {
            Label checkOOBLabel = generateLocalLabel();
            Label noErrorLabel = generateLocalLabel();

            // evaluate string expression, and save it on stack
            expr.list.dispatch(this);
            record.upSlot();
            record.pushToStack(A0, "Save string pointer on stack.");
            pushStackSpace("Reserve space for saved string pointer");

            // evaluate index expression, and save result in A0
            expr.index.dispatch(this);

            // restore string pointer to A1
            record.readFromStack(A1, "Retrieve string pointer in A1.");
            record.downSlot();
            popStackSpace("Pop off saved string pointer from stack");

            // ensure string pointer is not None
            backend.emitBNEZ(A1, checkOOBLabel, "Ensure string is not None");
            backend.emitJ(errorNone, "Throw operation on None error.");

            // ensure index is within bound
            backend.emitLocalLabel(checkOOBLabel, "string is not none. Now check index bound");
            backend.emitLW(T0, A1, "@.__len__", "Load attribute: __len__");
            backend.emitBLTU(A0, T0, noErrorLabel, "Ensure 0 <= index < len");
            backend.emitJ(errorOob, "Throw index out of bound error");

            // character selection
            backend.emitLocalLabel(noErrorLabel, "Index within bound, so compute string selection");
            backend.emitADDI(T1, A1, "@.__str__", "Point T1 at first byte of the string");
            backend.emitADD(T1, A0, T1, "Point T1 to selected character in the string");
            backend.emitLBU(T0, T1, 0, "Load indexed character (decimal value)");

            // obtain address to corresponding character object
            backend.emitLI(T1, ONE_CHAR_STR_OBJECT_SIZE, "Load one-char string object size");
            backend.emitMUL(T0, T0, T1, "Get offset into single-char table");
            backend.emitLA(A0, allChars, "Load address to single-char table");
            backend.emitADD(A0, A0, T0, "Index into single-char table");
        }


        /*----------------------- FOR LOOPS  -----------------------*/

        /** Emit code for looping a list. */
        public void emitForList(ForStmt forStmt) {
            /* CAUTION: concatenating to the loop iterable should not
             * have an effect within the loop, so we should not recompute
             * list length each time. */

            Label startLoopLabel = generateLocalLabel();
            Label endLoopLabel = generateLocalLabel();
            Label errorLabel = generateLocalLabel();

            // evaluate iterable expression, and save address in A0
            forStmt.iterable.dispatch(this);

            // iterable cannot be none
            backend.emitBEQZ(A0, errorLabel, "If the list is None, throw an error.");

            // prepare for-list loop values in T0-T3

            // fetch address to loop variable, we do it first, because it modifies T0-T2
            record.readVariableAddress(T3, forStmt.identifier.name);
            backend.emitLW(T0, A0, "@.__len__", "Store the list length in T0.");
            backend.emitMV(T1, ZERO, "The loop counter starts at 0. Store in T1.");
            backend.emitADDI(T2, A0, "@.__elts__", "Initially point T2 at first list element");

            // start loop: assign value to control variable
            backend.emitLocalLabel(startLoopLabel, "Start of the for-list loop.");

            // if loop_variable >= list_length, exit for-list loop
            backend.emitBGE(T1, T0, endLoopLabel, "Exit for loop, if loop_variable >= list_length");
            // Set loop variable
            backend.emitLW(T4, T2, 0, "Load value of current list element at T2.");
            backend.emitSW(T4, T3, 0, "Store value to loop variable, at address T3");

            // save values of T0-T3 registers
            allocateStackSpace(4 * WORD_SIZE, "Reserve space for T0-T3, used by for-list loop");
            record.upSlot();
            record.pushToStack(T0, "Store the value of T0, used by for-list loop");
            record.upSlot();
            record.pushToStack(T1, "Store the value of T1, used by for-list loop");
            record.upSlot();
            record.pushToStack(T2, "Store the value of T2, used by for-list loop");
            record.upSlot();
            record.pushToStack(T3, "Store the value of T3, used by for-list loop");


            // emit code for the body of for-list loop
            for (int i = 0; i < forStmt.body.size(); i++) {
                forStmt.body.get(i).dispatch(this);
            }

            // restore values of T0-T3 registers
            record.readFromStack(T3, "Restore the value of T3, used by for-list loop");
            record.downSlot();
            record.readFromStack(T2, "Restore the value of T2, used by for-list loop");
            record.downSlot();
            record.readFromStack(T1, "Restore the value of T1, used by for-list loop");
            record.downSlot();
            record.readFromStack(T0, "Restore the value of T0, used by for-list loop");
            record.downSlot();
            deallocateStackSpace(4 * WORD_SIZE, "Pop stack space for T0-T3, used by for-list loop");

            // end of one loop iteration
            backend.emitADDI(T1, T1, 1, "Increment loop counter by 1");
            backend.emitADDI(T2, T2, WORD_SIZE, "Point T2 at next list element");
            backend.emitJ(startLoopLabel, "Return to start of for-list loop.");

            // operation on None error
            backend.emitLocalLabel(errorLabel, "Error: iterable is None");
            backend.emitJ(errorNone, "Jump to error when list is None.");

            // end of loop
            backend.emitLocalLabel(endLoopLabel, "End of the for-list loop.");
        }


        /** Emit code for looping a string. */
        public void emitForStr(ForStmt forStmt) {
            /* CAUTION: concatenating to the loop iterable should not
             * have an effect within the loop, so we should not recompute
             * list length each time. */

            Label startLoopLabel = generateLocalLabel();
            Label endLoopLabel = generateLocalLabel();
            Label errorLabel = generateLocalLabel();

            // evaluate iterable expression, and save address in A0
            forStmt.iterable.dispatch(this);

            // iterable cannot be none
            backend.emitBEQZ(A0, errorLabel, "If the str is None, throw an error.");

            // prepare for-str loop values in T0-T3

            // fetch address to loop variable, we do it first, because it modifies T0-T2
            record.readVariableAddress(T3, forStmt.identifier.name);
            backend.emitLW(T0, A0, "@.__len__", "Store the str length in T0.");
            backend.emitMV(T1, ZERO, "The loop counter starts at 0. Store in T1.");
            backend.emitADDI(T2, A0, "@.__str__", "Initially point T2 at first str character");

            // start loop: assign value to control variable
            backend.emitLocalLabel(startLoopLabel, "Start of the for-str loop.");

            // if loop_variable >= str_length, exit for-str loop
            backend.emitBGE(T1, T0, endLoopLabel, "Exit for loop, if loop_variable >= str_length");

            // save values of T0-T3 registers
            // we do this BEFORE converting character object, to ensure T0-T3 haven't been modified
            allocateStackSpace(4 * WORD_SIZE, "Reserve space for T0-T3, used by for-str loop");
            record.upSlot();
            record.pushToStack(T0, "Store the value of T0, used by for-str loop");
            record.upSlot();
            record.pushToStack(T1, "Store the value of T1, used by for-str loop");
            record.upSlot();
            record.pushToStack(T2, "Store the value of T2, used by for-str loop");
            record.upSlot();
            record.pushToStack(T3, "Store the value of T3, used by for-str loop");

            // T0: str_length, T1: loop counter, T2: pointer at str, T3: address of control variable

            // read current str character object
            backend.emitLBU(T4, T2, 0, "Load indexed character (decimal value) at T2");
            backend.emitLI(T5, ONE_CHAR_STR_OBJECT_SIZE, "Load one-char string object size");
            backend.emitMUL(T5, T4, T5, "Get offset into single-char table");
            backend.emitLA(T2, allChars, "Load address to single-char table");
            backend.emitADD(T2, T2, T5, "Index into single-char table");

            // set loop variable
            backend.emitSW(T2, T3, 0, "Store value to loop variable, at address T5");

            // emit code for the body of for-str loop
            for (int i = 0; i < forStmt.body.size(); i++) {
                forStmt.body.get(i).dispatch(this);
            }

            // restore values of T0-T3 registers
            record.readFromStack(T3, "Restore the value of T3, used by for-str loop");
            record.downSlot();
            record.readFromStack(T2, "Restore the value of T2, used by for-str loop");
            record.downSlot();
            record.readFromStack(T1, "Restore the value of T1, used by for-str loop");
            record.downSlot();
            record.readFromStack(T0, "Restore the value of T0, used by for-str loop");
            record.downSlot();
            deallocateStackSpace(4 * WORD_SIZE, "Pop stack space for T0-T3, used by for-str loop");

            // end of one loop iteration
            backend.emitADDI(T1, T1, 1, "Increment loop counter by 1");
            backend.emitADDI(T2, T2, 1, "Point T2 at next str character");
            backend.emitJ(startLoopLabel, "Return to start of for-str loop.");

            // operation on None error
            backend.emitLocalLabel(errorLabel, "Error: iterable is None");
            backend.emitJ(errorNone, "Jump to error when str is None.");

            // end of loop
            backend.emitLocalLabel(endLoopLabel, "End of the for-str loop.");
        }

        /*-----------------------------------------------------------*/
        /*                                                           */
        /*              COMMON HELPER PROCEDURE METHODS              */
        /*                                                           */
        /*-----------------------------------------------------------*/


        /** Emit the address of static link for a calling function CALLEEINFO
         *  from current function into passed register Rd. */
        private void emitStaticLink(Register rd, FuncInfo calleeInfo) {
            FuncInfo parentInfo = calleeInfo.getParentFuncInfo();
            FuncInfo curFuncInfo = this.funcInfo;

            // static link is initially at current frame
            backend.emitMV(rd, FP, String.format("Get static link to %s",
                                                 curFuncInfo.getFuncName()));

            // follow static link to find the static link of the enclosing
            // function, for which the callee function is defined
            while (!curFuncInfo.getFuncName().equals(parentInfo.getFuncName())) {
                String comment =
                    String.format("Get static link to %s", curFuncInfo.getFuncName());
                backend.emitLW(rd, rd, this.getStaticLinkOffset(curFuncInfo), comment);
                curFuncInfo = curFuncInfo.getParentFuncInfo();
            }
        }

        /**
         * Turn boolean value inside register $A0 to corresponding boolean object.
         * The caller has to ensure it's indeed a boolean value, and is stored at $A0.
         * Register $T0 is modified by this call.
         */
        private void emitObjectifyBoolean() {
            Label falseObjectLabel = constants.getBoolConstant(false);
            backend.emitLA(T0, falseObjectLabel, "Fetch address of False object");
            backend.emitSLLI(A0, A0, 4, "Get offset of right bool object");
            backend.emitADD(A0, T0, A0, "Get correct boolean object");
        }

        /**
         * Turn integer value inside register $A0 to corresponding integer object.
         * The caller has to ensure it's indeed a integer value, and is stored at $A0.
         * Register $T0 is modified by this call.
         */
        private void emitObjectifyInteger() {
            Label intPrototypeLabel = intClass.getPrototypeLabel();

            // save current value in A0 to stack, in case RD = A0
            record.upSlot();
            record.pushToStack(A0,
                    "Save integer value on stack before allocating new object");

            // create integer object and save addr in $A0
            allocatePrototype(intClass);

            // set attribute __int__
            record.readFromStack(T0, "Load saved integer value from stack");
            record.downSlot();
            backend.emitSW(T0, A0, "@.__int__", "Set attribute: __int__");
        }

        /** Box the value in register A0, based on given EXPRTYPE. */
        private void emitObjectifyType(SymbolType exprType) {
            if (this.isIntegerType(exprType)) {
                this.emitObjectifyInteger();
            } else if (this.isBooleanType(exprType)) {
                this.emitObjectifyBoolean();
            }
        }

        /*-----------------------------------------------------------*/
        /*                                                           */
        /*.                   OTHER HELPER METHODS                   */
        /*                                                           */
        /*-----------------------------------------------------------*/


        /** Return the offset of the static link for function FUNCINFO */
        private int getStaticLinkOffset(FuncInfo funcInfo) {
            assert !this.isTopLevel(funcInfo)
                : "No static link for top-level functions";
            return funcInfo.getParams().size() * WORD_SIZE;
        }

        /** Return true if a function FUNCINFO is a top level function. */
        private boolean isTopLevel(FuncInfo funcInfo) {
            return funcInfo == null ||
                   funcInfo.getParentFuncInfo() == null;
        }

        /** Return paramInfo for a given parameter with NAME in FUNCINFO. */
        private StackVarInfo getParamInfo(FuncInfo funcInfo, String name) {
            SymbolInfo symInfo = funcInfo.getSymbolTable().get(name);
            assert symInfo instanceof StackVarInfo
                : "static analysis should ensure function parameter are variables";
            return (StackVarInfo) symInfo;
        }

        /** Return true for a given parameter NAME in FUNCINFO is object type.
         *  This is useful to determine when we should wrap int/bool values. */
        private boolean isObjectParam(FuncInfo funcInfo, String name) {
            StackVarInfo paramInfo = this.getParamInfo(funcInfo, name);
            return paramInfo.getVarType() == null ||
                   paramInfo.getVarType().className() == null ||
                   paramInfo.getVarType().className().equals("object");
        }

        /*---------------- SYMBOL TYPE COMPARISON  ------------------*/

        /** Return true if a symbol TYPE is BOOL_TYPE. */
        private boolean isBooleanType(SymbolType type) {
            return BOOL_TYPE.equals(type);
        }

        /** Return true if a symbol TYPE is INT_TYPE. */
        private boolean isIntegerType(SymbolType type) {
            return INT_TYPE.equals(type);
        }

        /*** Return true if a symbol TYPE is OBJECT_TYPE. */
        private boolean isObjectType(SymbolType type) {
            return OBJECT_TYPE.equals(type);
        }

        /*** Return true if a symbol TYPE is STR_TYPE. */
        private boolean isStringType(SymbolType type) {
            return STR_TYPE.equals(type);
        }

        /*** Return true if a symbol TYPE is NONE_TYPE. */
        private boolean isNoneType(SymbolType type) {
            return NONE_TYPE.equals(type);
        }

        /*** Return true if a symbol TYPE is EMPTY_TYPE. */
        private boolean isEmptyType(SymbolType type) {
            return EMPTY_TYPE.equals(type);
        }
    }

    /**
     * Emits custom code in the CODE segment.
     *
     * This method is called after emitting the top level and the
     * function bodies for each function.
     *
     * You can use this method to emit anything you want outside of the
     * top level or functions, e.g. custom routines that you may want to
     * call from within your code to do common tasks. This is not strictly
     * needed. You might not modify this at all and still complete
     * the assignment.
     *
     * To start you off, here is an implementation of three routines that
     * will be commonly needed from within the code you will generate
     * for statements.
     *
     * The routines are error handlers for operations on None, index out
     * of bounds, and division by zero. They never return to their caller.
     * Just jump to one of these routines to throw an error and
     * exit the program. For example, to throw an OOB error:
     *   backend.emitJ(errorOob, "Go to out-of-bounds error and abort");
     *
     */
    protected void emitCustomCode() {
        emitErrorFunc(errorArg, ERROR_ARG, "Bad argument.");
        // note, the reference implementation has this typo: Divisonp
        emitErrorFunc(errorDiv, ERROR_DIV_ZERO, "Divison by zero");
        emitErrorFunc(errorOob, ERROR_OOB, "Index out of bounds");
        emitErrorFunc(errorNone, ERROR_NONE, "Operation on None");
        emitErrorFunc(errorOom, ERROR_OOM, "Out of memory");
        emitErrorFunc(errorNyi, ERROR_NYI, "Unimplemented operation");

        emitListRoutine();
        emitStringRoutine();
    }

    /** Emit an error routine labeled ERRLABEL that aborts with message MSG. */
    private void emitErrorFunc(Label errLabel, int errorCode, String msg) {
        backend.emitGlobalLabel(errLabel);
        backend.emitLI(A0, errorCode, "Exit code for: " + msg);
        backend.emitLA(A1, constants.getStrConstant(msg),
                       "Load error message as str");
        backend.emitADDI(A1, A1, getAttrOffset(strClass, "__str__"),
                         "Load address of attribute __str__");
        backend.emitJ(abortLabel, "Abort");
    }

    /*-----------------------------------------------------------*/
    /*                                                           */
    /*                LIST ROUTINE CODE GENERATION               */
    /*                                                           */
    /*-----------------------------------------------------------*/

    /** Emit custom routines for list operations. */
    private void emitListRoutine() {
        // list construction
        emitListConstruction();
        // list concatenation
        emitListConcatenation();
    }

    /**
     * Emit list construction code routine.
     *
     * This routine behaves as a function.
     * To construct a list of N elements, N+1 parameters are passed
     * as arguments. The first N arguments are the list values, and
     * the last argument is the number of elements in the list.
     *
     * Register A0, A1, T0, T1, T2, T3 are modified in the routine.
     */
    private void emitListConstruction() {
        Label initLabel = new Label("conslist_initialization");
        Label endLabel = new Label("conslist_done");
        Label prototypeLabel = listClass.getPrototypeLabel();

        backend.emitGlobalLabel(constlistLabel);
        emitFunctionPrologue();

        // load information for list
        backend.emitLA(A0, prototypeLabel, "Load address to list prototype");
        backend.emitLW(A1, FP, 0, "Load list length");

        // allocate list object
        backend.emitBEQZ(A1, endLabel, "Empty list can use list prototype");
        backend.emitADDI(A1, A1, "@.__list_header_words__",
                                 "Total words needed for list object");
        backend.emitJAL(objectAllocResizeLabel, "allocate list object");

        // set length attribute
        backend.emitLW(T0, FP, 0, "Load list length");
        backend.emitSW(T0, A0, "@.__len__", "set __len__ attribute");

        // initialize pointers to list elements
        backend.emitSLLI(T1, T0, 2, "List length in bytes");
        backend.emitADD(T1, T1, FP, "Set T1 to address of first argument");
        backend.emitADDI(T2, A0, "@.__elts__", "Set T2 to first list item in list object");

        // set list elements
        backend.emitLocalLabel(initLabel, "Initialize list items");
        backend.emitLW(T3, T1, 0, "Read current list item from function arguments");
        backend.emitSW(T3, T2, 0, "Set current list item in list object");

        backend.emitADDI(T1, T1, -WORD_SIZE, "Point T1 to address of next argument");
        backend.emitADDI(T2, T2, WORD_SIZE, "Point T2 to address of next list item in list object");
        backend.emitADDI(T0, T0, -1, "Reduce counter: one less list item to initialize.");
        backend.emitBNEZ(T0, initLabel, "If counter != 0, continue list initialization.");

        // done
        backend.emitLocalLabel(endLabel, "List initialization done.");
        emitFunctionEpilogue();
        backend.emitJR(RA, "Return to caller");
    }

    /**
     * Emit list concatenation code routine.
     *
     * This routine behaves as a function.
     * To concatenate two lists, 2 arguments are passed, each being
     * the address of the two lists.
     *
     * Register A0, A1, T0, T1, T2, T3 are modified in the routine.
     */
    private void emitListConcatenation() {
        Label initLabel1 = new Label("concat_init_list_1");
        Label initLabel2 = new Label("concat_init_list_2");
        Label initPointer = new Label("concat_init_list_2_pointer");
        Label errorLabel = new Label("concat_none_error");
        Label endLabel = new Label("concat_done");
        Label prototypeLabel = listClass.getPrototypeLabel();

        backend.emitGlobalLabel(listconcatLabel);
        emitFunctionPrologue();

        // load pointers to lists
        backend.emitLW(A0, FP, 4, "Load pointer to first list");
        backend.emitLW(A1, FP, 0, "Load pointer to second list");

        // check against None
        backend.emitBEQZ(A0, errorLabel, "Check first list is not None");
        backend.emitBEQZ(A1, errorLabel, "Check second list is not None");

        // calculate length of concatenated list
        backend.emitLW(T0, A0, "@.__len__", "Load length of first list");
        backend.emitLW(T1, A1, "@.__len__", "Load length of second list");
        backend.emitADD(T2, T0, T1, "Calculate length of concatenated list");

        // save total length on stack
        record.upSlot();
        pushStackSpace("Reserve stack space for total length");
        record.pushToStack(T2, "Save total length of concatenated list on stack");

        // construct object for the concatenated list, save pointer in A0
        backend.emitLA(A0, prototypeLabel, "Load address to list prototype");
        backend.emitADDI(A1, T2, "@.__list_header_words__",
                                 "Total words needed for list object");
        backend.emitJAL(objectAllocResizeLabel, "Allocate new list object");

        // retrieve total length, save in A1
        record.readFromStack(A1, "Retrieve total length of concatenated list");
        record.downSlot();
        popStackSpace("Pop total length of concatenated list off stack");

        // set length attribute
        backend.emitSW(A1, A0, "@.__len__", "set __len__ attribute");

        // initialize pointers for appending elements of first list
        backend.emitADDI(T0, A0, "@.__elts__",
                                 "Point T0 to the fist element in concatenated list object");
        backend.emitLW(T1, FP, 4, "Load pointer to first list");
        backend.emitLW(T2, T1, "@.__len__", "Load length of first list");
        backend.emitADDI(T1, T1, "@.__elts__",
                                  "Point T1 to the first element of first list");

        // append elements of first list
        backend.emitLocalLabel(initLabel1, "Append list items of first list");
        backend.emitBEQZ(T2, initPointer, "No need to append elements if first list is empty");
        backend.emitLW(T3, T1, 0, "Read current list item from first list");
        backend.emitSW(T3, T0, 0, "Set current list item in concatenated list object");
        backend.emitADDI(T0, T0, WORD_SIZE, "Point T0 to next list item in concatenated list");
        backend.emitADDI(T1, T1, WORD_SIZE, "Point T1 to next list item in first list");
        backend.emitADDI(T2, T2, -1, "Reduce counter: one less list item to append.");
        backend.emitBNEZ(T2, initLabel1,
                         "If counter != 0, continue appending elements of first list.");

        // initialize pointers for appending elements of second list
        backend.emitLocalLabel(initPointer, "Prepare for appending elements of second list");
        backend.emitLW(T1, FP, 0, "Load pointer to second list");
        backend.emitLW(T2, T1, "@.__len__", "Load length of second list");
        backend.emitADDI(T1, T1, "@.__elts__",
                                  "Point T1 to the first element of second list");

        // append elements of second list
        backend.emitLocalLabel(initLabel2, "Append list items of second list");
        backend.emitBEQZ(T2, endLabel, "No need to append elements if second list is empty");
        backend.emitLW(T3, T1, 0, "Read current list item from second list");
        backend.emitSW(T3, T0, 0, "Set current list item in concatenated list object");
        backend.emitADDI(T0, T0, WORD_SIZE, "Point T0 to next list item in concatenated list");
        backend.emitADDI(T1, T1, WORD_SIZE, "Point T1 to next list item in second list");
        backend.emitADDI(T2, T2, -1, "Reduce counter: one less list item to append.");
        backend.emitBNEZ(T2, initLabel2,
                         "If counter != 0, continue appending elements of second list.");

        // done, if no error
        backend.emitLocalLabel(endLabel, "List concatenation done.");
        emitFunctionEpilogue();
        backend.emitJR(RA, "Return to caller");

        // Operation on None error
        backend.emitLocalLabel(errorLabel, "Error: at least one list is None");
        backend.emitJ(errorNone, "Throw Operation on None error");
    }

    /*-----------------------------------------------------------*/
    /*                                                           */
    /*              STRING ROUTINE CODE GENERATION               */
    /*                                                           */
    /*-----------------------------------------------------------*/

    /** Emit custom routines for string operations. */
    private void emitStringRoutine() {
        // initialize one character strings
        emitInitializeCharacters();
        // string concatenation
        emitStringConcatenation();
        // string comparison
        emitStringComparison();
    }

    /** Emit code for string comparison. */
    private void emitStringConcatenation() {
        Label endLabel = new Label("strcat_end");
        Label errorLabel = new Label("strcat_none_error");
        Label returnFirst = new Label("strcat_return_first");
        Label returnSecond = new Label("strcat_return_second");
        Label addNull = new Label("strcat_add_null");
        Label appendFirst = new Label("strcat_append_first");
        Label appendSecond = new Label("strcat_append_second");
        Label resetPointer = new Label("strcat_reset_pointer");
        Label prototypeLabel = strClass.getPrototypeLabel();

        backend.emitGlobalLabel(strcatLabel);
        emitFunctionPrologue();

        // load pointers to strings
        backend.emitLW(T0, FP, 4, "Load pointer to first string");
        backend.emitLW(T1, FP, 0, "Load pointer to second string");

        // check against None
        backend.emitBEQZ(T0, errorLabel, "Check first string is not None");
        backend.emitBEQZ(T1, errorLabel, "Check second string is not None");

        // special case management
        backend.emitLW(T0, T0, "@.__len__", "Load length of first string");
        backend.emitBEQZ(T0, returnSecond, "Return str_2, if str_1 is empty");
        backend.emitLW(T1, T1, "@.__len__", "Load length of second string");
        backend.emitBEQZ(T1, returnFirst, "Return str_1, if str_2 is empty");

        // calculate total length (in bytes, exclude null), and save on stack
        backend.emitADD(T1, T0, T1, "Calculate total length of concatenated string");
        record.upSlot();
        pushStackSpace("Reserve stack space for total length");
        record.pushToStack(T1, "Save total length of concatenated string on stack");

        // allocate new string object
        backend.emitADDI(T1, T1, 4, "Add 4 for ceiling operation");
        backend.emitSRLI(T1, T1, 2, "Convert length from bytes to words");
        backend.emitADDI(A1, T1, "@.__string_header_words__", "Add header size");
        backend.emitLA(A0, prototypeLabel, "Load prototype to string object");
        backend.emitJAL(objectAllocResizeLabel, "Allocate new string object");

        // retrieve total length (in bytes, exclude null) from stack
        record.readFromStack(T0, "Retrieve total length of concatenated string");
        record.downSlot();
        popStackSpace("Pop total length of concatenated string off stack");

        // set length attribute
        backend.emitSW(T0, A0, "@.__len__", "Set attribute: __len__");

        // initialize pointers for appending contents of first string
        backend.emitADDI(T2, A0, "@.__str__", "Point T2 at first byte in new string");
        backend.emitLW(T0, FP, 4, "Point T0 at first string");
        backend.emitLW(T1, T0, "@.__len__", "Load length of first string, in bytes");
        backend.emitADDI(T0, T0, "@.__str__", "Point T0 at first byte in first string");

        // append contents of first string
        backend.emitLocalLabel(appendFirst, "Append bytes of first string");
        backend.emitBEQZ(T1, resetPointer, "Done appending first string, if counter == 0");
        backend.emitLBU(T3, T0, 0, "Load current byte from first string");
        backend.emitSB(T3, T2, 0, "Save current byte to new string");
        backend.emitADDI(T1, T1, -1, "Reduce counter: one less byte to append");
        backend.emitADDI(T0, T0, 1, "Point T0 at next byte in first string");
        backend.emitADDI(T2, T2, 1, "Point T2 at next byte in new string");
        backend.emitJ(appendFirst, "Continue appending contents of first string");

        // initialize pointers for appending contents of second string
        backend.emitLocalLabel(resetPointer, "Prepare pointers for appending second string");
        backend.emitLW(T0, FP, 0, "Point T0 at second string");
        backend.emitLW(T1, T0, "@.__len__", "Load length of second string, in bytes");
        backend.emitADDI(T0, T0, "@.__str__", "Point T0 at first byte in second string");

        // append contents of second string
        backend.emitLocalLabel(appendSecond, "Prepare pointers for appending second string");
        backend.emitBEQZ(T1, addNull, "Done appending second string, if counter == 0");
        backend.emitLBU(T3, T0, 0, "Load current byte from second string");
        backend.emitSB(T3, T2, 0, "Save current byte to new string");
        backend.emitADDI(T1, T1, -1, "Reduce counter: one less byte to append");
        backend.emitADDI(T0, T0, 1, "Point T0 at next byte in second string");
        backend.emitADDI(T2, T2, 1, "Point T2 at next byte in new string");
        backend.emitJ(appendSecond, "Continue appending contents of second string");

        // special case: return string 1
        backend.emitLocalLabel(returnFirst, "special case: return first string");
        backend.emitLW(A0, FP, 4, "Load pointer to first string");
        backend.emitJ(endLabel, "Go to end function epilogue");

        // special case: return string 2
        backend.emitLocalLabel(returnSecond, "special case: return second string");
        backend.emitLW(A0, FP, 0, "Load pointer to second string");
        backend.emitJ(endLabel, "Go to end function epilogue");

        // add null byte
        backend.emitLocalLabel(addNull, null);
        backend.emitSB(ZERO, T2, 0, "Append null byte to new string");

        // done, if no error
        backend.emitLocalLabel(endLabel, "string concatenation done.");
        emitFunctionEpilogue();
        backend.emitJR(RA, "Return to caller");

        // Operation on None error
        backend.emitLocalLabel(errorLabel, "Error: at least one string is None");
        backend.emitJ(errorNone, "Throw Operation on None error");
    }

    /**
     * Emit string comparison check code.
     *
     * This routine behaves as a function.
     * To compare two strings, 2 arguments are passed, each being
     * the address of the two strings. A0 is 1 if strings are equal.
     *
     * Register A0, A1, T0, T1, T2, T3, T4 are modified in the routine.
     */
    private void emitStringComparison() {
        Label notEqualLabel = new Label("streql_not_equal");
        Label byteCompareLabel = new Label("streql_byte_compare");
        Label errorLabel = new Label("streql_none");
        Label endLabel = new Label("streql_done");

        backend.emitGlobalLabel(streqLabel);
        emitFunctionPrologue();

        // load strings
        backend.emitLW(A0, FP, 4, "Load pointer to first string");
        backend.emitLW(A1, FP, 0, "Load pointer to second string");

        // check against None
        backend.emitBEQZ(A0, errorLabel, "Check first string is not None");
        backend.emitBEQZ(A1, errorLabel, "Check second string is not None");

        // calculate string length
        backend.emitLW(T0, A0, "@.__len__", "Load length of first string");
        backend.emitLW(T1, A1, "@.__len__", "Load length of second string");

        // two strings are not equal, if they differ by length
        backend.emitBNE(T0, T1, notEqualLabel, "str1 != str2, if they differ in length");

        // prepare pointers for byte comparison
        backend.emitADDI(T1, A0, "@.__str__", "Point T1 at first byte of first string");
        backend.emitADDI(T2, A1, "@.__str__", "Point T2 at first byte of second string");

        // compare strings byte by byte
        backend.emitLocalLabel(byteCompareLabel, "Compare byte by byte");
        backend.emitLBU(T3, T1, 0, "Load current byte of first string");
        backend.emitLBU(T4, T2, 0, "Load current byte of second string");
        backend.emitBNE(T3, T4, notEqualLabel, "str1 != str2, if one byte is different");
        backend.emitADDI(T1, T1, 1, "Point T1 at next byte of first string");
        backend.emitADDI(T2, T2, 1, "Point T2 at next byte of second string");
        backend.emitADDI(T0, T0, -1, "Reduce counter: one less to compare");
        backend.emitBGTZ(T0, byteCompareLabel,  "If counter != 0, continue byte comparison.");
        backend.emitLI(A0, 1, "Load true literal");
        backend.emitJ(endLabel, "str1 == str2");

        // string is not equal
        backend.emitLocalLabel(notEqualLabel, "str1 != str2");
        backend.emitMV(A0, ZERO, "load false literal");

        // done
        backend.emitLocalLabel(endLabel, "String comparison done");
        emitFunctionEpilogue();
        backend.emitJR(RA, "Return to caller");

        // Operation on None error
        backend.emitLocalLabel(errorLabel, "Error: at least one string is None");
        backend.emitJ(errorNone, "Throw Operation on None error");
    }

    /** Initialize one-character strings, using the routine in reference. */
    private void emitInitializeCharacters() {
        Label prototypeLabel = strClass.getPrototypeLabel();
        Label initSingleChar = generateLocalLabel();


        // code to initialize one-character strings
        backend.emitGlobalLabel(charInitLabel);

        backend.emitLA(A0, prototypeLabel, "Load prototype to string object");
        backend.emitLW(T0, A0, 0 * WORD_SIZE,"Load type tag");
        backend.emitLW(T1, A0, 1 * WORD_SIZE ,"Load size in words");
        backend.emitLW(T2, A0, 2 * WORD_SIZE ,"Load pointer to dispatch table");
        backend.emitLI(T3, 1, "Load attribute __len__ for one-character string");

        backend.emitLA(A0, allChars, "Load address to data table: allChars");
        backend.emitLI(T4, MAX_CHAR_DECIMAL, "load max character decimal value");
        backend.emitMV(T5, ZERO, "Initialize counter to 0");

        backend.emitLocalLabel(initSingleChar, "Initialize single character string");
        backend.emitSW(T0, A0, 0 * WORD_SIZE, "Set type tag");
        backend.emitSW(T1, A0, 1 * WORD_SIZE ,"Set size in words");
        backend.emitSW(T2, A0, 2 * WORD_SIZE ,"Set pointer to dispatch table");
        backend.emitSW(T3, A0, 3 * WORD_SIZE ,"Set __len__ attribute");
        backend.emitSW(T5, A0, 4 * WORD_SIZE ,"Set character value");
        backend.emitADDI(A0, A0, ONE_CHAR_STR_OBJECT_SIZE, "Move pointer to next free space");
        backend.emitADDI(T5, T5, 1, "Increment counter");
        backend.emitBNE(T4, T5, initSingleChar, "Continue initializing, if counter <= max char");

        // done initializing one-character strings
        backend.emitJR(RA, "Return to caller");

        // start data table for one-character strings
        backend.startData();
        backend.alignNext(2); // align address to multiple of 2*2 = 4
        backend.emitGlobalLabel(allChars);
        backend.emitInsn(String.format(".space %d", ONE_CHAR_STR_OBJECT_SIZE * MAX_CHAR_DECIMAL),
                         "Allocate enough space for all one-character string objects");

        // restart code region
        backend.startCode();

    }


    /*-----------------------------------------------------------*/
    /*                                                           */
    /*                CODE GENERATION HELPER METHODS             */
    /*                                                           */
    /*-----------------------------------------------------------*/


    /*------------------------ FUNCTIONS  -------------------------*/

    /** Emit code for function prologues */
    private void emitFunctionPrologue() {
        backend.emitADDI(SP, SP, -2 * WORD_SIZE,
                         "Reserve space for caller's return addr, control link");
        backend.emitSW(FP, SP, 0, "saved caller's dynamic link");
        backend.emitSW(RA, SP, WORD_SIZE, "saved caller's return addr");
        backend.emitADDI(FP, SP, 2 *WORD_SIZE, "New FP is at old SP");
    }

    /**
     *  Emit code for function epilogue, with function full-qualified FUNCNAME
     *  and function activation record of SIZE bytes.
     */
    private void emitFunctionEpilogue() {
        backend.emitLW(RA, FP, -1 * WORD_SIZE, "Get return address");
        backend.emitMV(T0, FP, "load current FP/old SP address");
        backend.emitLW(FP, FP, -2 * WORD_SIZE, "Use control link to restore caller's FP");
        backend.emitMV(SP, T0, "Restore old stack pointer");
    }

    /** Return the offset with respect to FUNCINFO's own FP,
     *  for retrieving local variables/parameters of NAME. */
    private int getVarOffset(FuncInfo funcInfo, String name) {
        int varIndex = funcInfo.getVarIndex(name);
        int paramSize = funcInfo.getParams().size();
        // the (N-1) shift makes sure the Nth parameter, with varIndex N-1
        // will be read from 0(FP), and everything is aligned accordingly.
        return -(varIndex - (paramSize - 1)) * WORD_SIZE;
    }

    /*------------------------ STACK SPACE  -------------------------*/

    /** Explicitly allocate one slot of space on stack frame. */
    private void pushStackSpace() {
        this.allocateStackSpace(WORD_SIZE, "Reserve one slot on stack");
    }

    /** Explicitly deallocate one slot of space on stack frame. */
    private void popStackSpace() {
        this.deallocateStackSpace(WORD_SIZE, "Pop one slot from stack");
    }

    /** Equivalent to calling `pushStackSpace` with custom comment. */
    private void pushStackSpace(String comment) {
        this.allocateStackSpace(WORD_SIZE, comment);
    }

    /** Equivalent to calling `popStackSpace` with custom comment. */
    private void popStackSpace(String comment) {
        this.deallocateStackSpace(WORD_SIZE, comment);
    }

    /** Equivalent of calling `allocateStackSpace` with default comment. */
    private void allocateStackSpace(int size) {
        String comment = String.format("Reserve %d bytes on stack", size);
        this.allocateStackSpace(size, comment);
    }

    /** Equivalent of calling `deallocateStackSpace` with default comment. */
    private void deallocateStackSpace(int size) {
        String comment = String.format("Pop %d bytes from stack", size);
        this.deallocateStackSpace(size, comment);
    }

    /** Explicitly reserve space on stack frame. */
    private void allocateStackSpace(int size, String comment) {
        assert size > 0 : "this method can only be called with positive size";
        backend.emitADDI(SP, SP, -size, comment);
    }

    /** Explicitly deallocate space on stack frame. */
    private void deallocateStackSpace(int size, String comment) {
        assert size > 0 : "this method can only be called with positive size";
        backend.emitADDI(SP, SP, size, comment);
    }

    /***
     * Allocates a prototype for the class given, returning
     * the allocated value in register A0.
     * @param classInfo
     */
    private void allocatePrototype(ClassInfo classInfo) {
        Label prototypeLabel = classInfo.getPrototypeLabel();
        String comment = String.format(
            "Load pointer to prototype of: %s", classInfo.getClassName()
        );

        backend.emitLA(A0, prototypeLabel, comment);
        backend.emitJAL(objectAllocLabel, "Allocate new object");
    }

    /*------------------------ LITERALS  -------------------------*/

    /** Load unwrapped (if possible) value of a LITERAL to register RD. */
    private void emitLoadLiteral(Register rd, Literal literal) {
        if (literal instanceof IntegerLiteral) {
            int intValue = ((IntegerLiteral) literal).value;
            String comment = String.format("Load integer literal: %d", intValue);
            backend.emitLI(rd, intValue, comment);
        } else if (literal instanceof BooleanLiteral) {
            boolean boolValue = ((BooleanLiteral) literal).value;
            String comment = String.format("Load boolean literal: %b", boolValue);
            backend.emitLI(rd, boolValue ? 1 : 0, comment);
        } else if (literal instanceof StringLiteral) {
            String strValue = ((StringLiteral) literal).value;
            String comment = String.format("Load string literal: %s", strValue);
            backend.emitLA(rd, this.constants.getStrConstant(strValue), comment);
        } else {
            assert literal instanceof NoneLiteral;
            backend.emitMV(rd, ZERO, "Load none literal: None");
        }
    }
}
