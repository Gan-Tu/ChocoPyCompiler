package chocopy.codegen;

import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.ClassValueType;
import chocopy.common.analysis.types.SymbolType;
import chocopy.common.codegen.AttrInfo;
import chocopy.common.codegen.FuncInfo;
import chocopy.common.codegen.GlobalVarInfo;
import chocopy.common.codegen.RiscVBackend;
import chocopy.common.codegen.StackVarInfo;
import chocopy.common.codegen.SymbolInfo;
import chocopy.common.codegen.ClassInfo;
import chocopy.common.codegen.Label;
import chocopy.common.codegen.RiscVBackend.Register;
import chocopy.common.astnodes.MemberExpr;


import static chocopy.common.codegen.RiscVBackend.Register.FP;
import static chocopy.common.codegen.RiscVBackend.Register.T0;

/**
 * The variable accessors is a class that provides an interface
 * allows the value, or address, of a named variable to be read
 * into a specified register.
 */
public abstract class VariableAccessor {

    /** An activation record for reading from and writing to stack. */
    protected StackRecord record;
    /** The current function for which the accessor tries to read variables from. */
    protected FuncInfo funcInfo;
    /** The global symbol table. */
    protected final SymbolTable<SymbolInfo> globalSymbols;
    /** The backend that emits assembly. */
    protected final RiscVBackend backend;
    /** Given offset for the header of a class. */
    protected final int HEADER_OFFSET = 12;

    public VariableAccessor(StackRecord record,
                            FuncInfo funcInfo,
                            SymbolTable<SymbolInfo> globalSymbols,
                            RiscVBackend backend) {
        this.record = record;
        this.funcInfo = funcInfo;
        this.globalSymbols = globalSymbols;
        this.backend = backend;
    }

    /** Set current function. */
    public void setFunctionInfo(FuncInfo funcInfo) {
        this.funcInfo = funcInfo;
    }

    /** Get current function. */
    public FuncInfo getFunctionInfo(FuncInfo funcInfo) {
        return this.funcInfo;
    }

    /** Read value of a variable NAME from current function into register RD.
     *  The value can be a parameter, local, inherited, or global variable. */
    public void readVariable(Register rd, String name) {
        // get symbol table
        SymbolTable<SymbolInfo> sym = globalSymbols;
        if (this.funcInfo != null) {
            sym = this.funcInfo.getSymbolTable();
        }

        // get variable info
        SymbolInfo symInfo = sym.get(name);
        if (symInfo == null) {
            throw new IllegalArgumentException(
                "static analysis should ensure variable exists."
            );
        }

        if (symInfo instanceof GlobalVarInfo) {
            this.readGlobalVariable(rd, name);
        } else if (symInfo instanceof StackVarInfo) {
            StackVarInfo varInfo = (StackVarInfo) symInfo;
            if (sym.declares(name)) {
                this.readLocalVariable(rd, name);
            } else {
                this.readInheritedVariable(rd, varInfo);
            }
        } else {
            assert symInfo instanceof AttrInfo;
            throw new IllegalArgumentException(
                "Do not support accessing attributes using only a name."
            );
        }
    }

    /**
     * Load value, or address, of an attribute from an object instance pointer
     * located in register RS and stores it in register RD.
     * @param rd Destination for the attribute.
     * @param rs Register in which the address for the object instance is located.
     * @param expr The member expression.
     */
    public void readAttribute(Register rd, Register rs, MemberExpr expr) {
        SymbolType objectType = expr.object.getInferredType();
        assert objectType instanceof ClassValueType
            : "static analysis should ensure member expressions can access class types.";

        String className = objectType.className();
        ClassInfo classInfo = (ClassInfo) globalSymbols.get(className);

        String attributeName = expr.member.name;
        int offset = this.getAttrOffset(classInfo, attributeName);

        String comment = String.format("Load attribute %s.%s", className, attributeName);
        this.emitFromOffset(rd, rs, offset, comment);
    }


    /*--------------------------------------------------------------*/
    /*                                                              */
    /*               HELPER READ VALUE OPERATIONS                   */
    /*                                                              */
    /*--------------------------------------------------------------*/

    protected abstract void emitFromOffset(Register rd, Register rs, int offset, String comment);
    protected abstract void emitFromLabel(Register rd, Label label, String comment);


    /** Read value of local variable NAME of current funcInfo into register RD. */
    protected void readLocalVariable(Register rd, String name) {
        String comment = this.makeLocalVariableComment(name);
        int offset = this.getVarOffset(this.funcInfo, name);
        this.emitFromOffset(rd, FP, offset, comment);
    }

    /** Read value of a global variable NAME into register RD. */
    protected void readGlobalVariable(Register rd, String name) {
        assert globalSymbols.declares(name)
                : "static analysis should ensure global variable exists";
        GlobalVarInfo globalVarInfo = (GlobalVarInfo) this.globalSymbols.get(name);
        String comment = String.format("Load global: %s", name);
        this.emitFromLabel(rd, globalVarInfo.getLabel(), comment);
    }

    /**
     * Load value, or address, of a stack variable VARINFO inherited from
     * upper function scopes by following the static links, and put the
     * value in register RD.
     *
     * Register $T0 and $A0 will be modified by this method.
     */
    protected void readInheritedVariable(Register rd, StackVarInfo varInfo) {
        String varBaseName = varInfo.getVarName();
        String varFuncName = varInfo.getFuncInfo().getFuncName();
        String varFullName = varFuncName + "." + varBaseName;

        FuncInfo curFuncInfo = this.funcInfo;
        backend.emitMV(T0, FP, "Copy FP value to T0");


        // follow static links to the function scope in which the variable is defined
        while (!curFuncInfo.getFuncName().equals(varFuncName)) {
            String curFuncName = curFuncInfo.getFuncName();
            String parentFuncName = curFuncInfo.getParentFuncInfo().getFuncName();
            String comment = String.format("Load static link from %s to %s",
                    curFuncName, parentFuncName);
            backend.emitLW(T0, T0, this.getStaticLinkOffset(curFuncInfo), comment);
            curFuncInfo = curFuncInfo.getParentFuncInfo();
        }

        assert curFuncInfo.getSymbolTable().declares(varBaseName)
                : "StackVarInfo should ensure its enclosing function is correct.";

        String comment = String.format("Load var: %s", varFullName);
        int offset = this.getVarOffset(curFuncInfo, varBaseName);
        this.emitFromOffset(rd, T0, offset, comment);
    }


    /*--------------------------------------------------------------*/
    /*                                                              */
    /*                      UTILITY OPERATIONS                      */
    /*                                                              */
    /*--------------------------------------------------------------*/

    /** Return a comment printed in assembly code for accessing variable NAME. */
    protected String makeLocalVariableComment(String name) {
        String varFullname =
                String.format("%s.%s", funcInfo.getFuncName(), name);
        String comment = String.format("Load local variable: %s", varFullname);
        if (this.funcInfo.getParams().contains(name)) {
            comment = String.format("Load parameter: %s", varFullname);
        }
        return comment;
    }

    /**
     * Return the offset of the static link for function FUNCINFO.
     * The static link is thus at (OFFSET)(FP).
     */
    protected int getStaticLinkOffset(FuncInfo funcInfo) {
        assert !this.isTopLevel(funcInfo)
                : "No static link for top-level functions";
        return funcInfo.getParams().size() * backend.getWordSize();
    }

    /** Return true if a function FUNCINFO is a top level function. */
    protected boolean isTopLevel(FuncInfo funcInfo) {
        return  funcInfo == null ||
                funcInfo.getParentFuncInfo() == null;
    }

    /**
     * Return the offset with respect to FUNCINFO's own activation record,
     * for retrieving local variables/parameters of NAME.
     * Thus, the caller can fetch the result at (OFFSET)(FP).
     */
    protected int getVarOffset(FuncInfo funcInfo, String name) {
        int varIndex = funcInfo.getVarIndex(name);
        int paramSize = funcInfo.getParams().size();
        // the (N-1) shift makes sure the Nth parameter, with varIndex N-1
        // will be read from 0(FP), and everything is aligned accordingly.
        return -(varIndex - (paramSize - 1)) * this.backend.getWordSize();
    }

    /** Return the offset for a given attribute NAME of CLASSINFO. */
    private int getAttrOffset(ClassInfo classInfo, String name) {
        int attributeIndex = classInfo.getAttributeIndex(name);
        return HEADER_OFFSET + attributeIndex * this.backend.getWordSize();
    }

}
