package chocopy.codegen;

import chocopy.common.codegen.Label;
import chocopy.common.codegen.RiscVBackend;
import chocopy.common.codegen.RiscVBackend.Register;
import chocopy.common.codegen.SymbolInfo;
import chocopy.common.codegen.ClassInfo;
import chocopy.common.codegen.FuncInfo;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.ClassValueType;
import chocopy.common.analysis.types.SymbolType;
import chocopy.common.astnodes.MemberExpr;

import static chocopy.common.codegen.RiscVBackend.Register.A0;
import static chocopy.common.codegen.RiscVBackend.Register.FP;

public class StackRecord {

    /** The backend that emits assembly. */
    private final RiscVBackend backend;

    /** Convenience variable: the word size for the current backend. */
    public final int WORD_SIZE;

    /**
     * Current slot our internal SP points at.
     * - ADDR = -(SLOT * WORD_SIZE) (FP).
     */
    private int _slot;

    /**
     * The symbol table that maps global names to information about
     * the bound global variables, global functions, or classes.
     */
    protected final SymbolTable<SymbolInfo> globalSymbols;

    /** The variable accessors for variable values */
    private VariableValueAccessor valueAccessor;

    /** The variable accessors for variable addresses */
    private VariableAddressAccessor addressAccessor;


    public StackRecord(RiscVBackend backend,
                       SymbolTable<SymbolInfo> globalSymbols) {
        this.backend = backend;
        this.WORD_SIZE = backend.getWordSize();
        this._slot = 0;
        this.globalSymbols = globalSymbols;
        this.valueAccessor = null;
        this.addressAccessor = null;
    }

    /** Set the current variable accessor for FUNCINFO. */
    public void setFunctionInfo(FuncInfo funcInfo) {
        if (this.valueAccessor == null) {
            this.valueAccessor =
                new VariableValueAccessor(this,
                                          funcInfo,
                                          this.globalSymbols,
                                          this.backend);
        } else {
            this.valueAccessor.setFunctionInfo(funcInfo);
        }

        if (this.addressAccessor == null) {
            this.addressAccessor =
                new VariableAddressAccessor(this,
                                          funcInfo,
                                          this.globalSymbols,
                                          this.backend);
        } else {
            this.addressAccessor.setFunctionInfo(funcInfo);
        }
    }

    /*--------------------------------------------------------------*/
    /*                                                              */
    /*                       SLOT OPERATIONS                        */
    /*                                                              */
    /*                                                              */
    /*    Increment the internal stack pointer, and not the real    */
    /*    stack pointer. We maintain two different representations  */
    /*    so that. the stack can be used for quick operations.      */
    /*    In order to persist this change to the real stack it is   */
    /*    necessary to call pushToStack()                           */
    /*                                                              */
    /*                                                              */
    /*--------------------------------------------------------------*/

    /** Increment internal SP by one. */
    public void upSlot() {
        this._slot += 1;
    }

    /** Decrement internal SP by one. */
    public void downSlot() {
        this._slot -= 1;
    }

    /** Increment internal SP by SLOTS. */
    private void upSlot(int slots) {
        this._slot += slots;
    }

    /** Decrement internal SP by SLOTS. */
    public void downSlot(int slots) {
        this._slot -= slots;
    }

    /** Set internal SP to given SLOT. */
    public void setSlot(int slot) {
        this._slot = slot;
    }

    /** Return internal SP to given SLOT. */
    public int getSlot(int slot) {
        return this._slot;
    }


    /*--------------------------------------------------------------*/
    /*                                                              */
    /*                  VARIABLE ACCESS OPERATIONS                  */
    /*                                                              */
    /*--------------------------------------------------------------*/

    /** Read value of a variable NAME from current function into register RD.
     *  The value can be a parameter, local, inherited, or global variable. */
    public void readVariable(Register rd, String name) {
        assert this.valueAccessor != null : "value variable accessors hasn't been set";
        this.valueAccessor.readVariable(rd, name);
    }

    /** Load address of a variable NAME from current function into register RD.
     *  The value can be a parameter, local, inherited, or global variable. */
    public void readVariableAddress(Register rd, String name) {
        assert this.addressAccessor != null : "address variable accessors hasn't been set";
        this.addressAccessor.readVariable(rd, name);
    }

    /**
     * Load value of an attribute from an object instance pointer
     * located in register RS and stores it in register RD.
     * @param rd Destination for the attribute.
     * @param rs Register in which the address for the object instance is located.
     * @param expr The member expression.
     */
    public void readAttribute(Register rd, Register rs, MemberExpr expr) {
        assert this.valueAccessor != null : "value variable accessors hasn't been set";
        this.valueAccessor.readAttribute(rd, rs, expr);
    }

    /**
     * Load  address, of an attribute from an object instance pointer
     * located in register RS and stores it in register RD.
     * @param rd Destination for the attribute.
     * @param rs Register in which the address for the object instance is located.
     * @param expr The member expression.
     */
    public void readAttributeAddress(Register rd, Register rs, MemberExpr expr) {
        assert this.addressAccessor != null : "address variable accessors hasn't been set";
        this.addressAccessor.readAttribute(rd, rs, expr);
    }

    /*--------------------------------------------------------------*/
    /*                                                              */
    /*                       READ OPERATIONS                        */
    /*                                                              */
    /*--------------------------------------------------------------*/

    /** Read value from top of stack to register RD, with default comment. */
    public void readFromStack(Register rd) {
        String comment = String.format("Read from stack slot %d", this._slot);
        this.readFromStack(rd, this._slot, comment);
    }

    /** Read value from top of stack to register RD. */
    public void readFromStack(Register rd, String comment) {
        this.readFromStack(rd, this._slot, comment);
    }

    /** Read value at -(SLOT * WORD_SIZE)(FP) to register RD. */
    public void readFromStack(Register rd, int slot, String comment) {
        backend.emitLW(rd, FP, -slot * WORD_SIZE, comment);
    }

    /** Read value at (OFFSET)(FP) to register RD. */
    public void readFromStackOffset(Register rd, int offset, String comment) {
        this.readFromOffset(rd, FP, offset, comment);
    }

    /** Read value at (OFFSET)(RS) to register RD. */
    public void readFromOffset(Register rd, Register rs, int offset, String comment) {
        backend.emitLW(rd, rs, offset, comment);
    }

    /** Read value of LABEL to register RD. */
    public void readFromLabel(Register rd, Label label, String comment) {
        backend.emitLW(rd, label, comment);
    }

    /*--------------------------------------------------------------*/
    /*                                                              */
    /*                        WRITE OPERATIONS                      */
    /*                                                              */
    /*--------------------------------------------------------------*/

    /** Equivalent of calling `pushToStack` on top of stack, and custom comment. */
    public void pushToStack(Register rd) {
        String comment = String.format("Push to stack slot %d", this._slot);
        this.pushToStack(rd, this._slot, comment);
    }

    /** Equivalent of calling `pushToStack` on top of stack. */
    public void pushToStack(Register rd, String comment) {
        this.pushToStack(rd, this._slot, comment);
    }

    /** Save value from register RD to -(SLOT * WORD_SIZE) (FP). */
    public void pushToStack(Register rd, int slot, String comment) {
        backend.emitSW(rd, FP, -slot * WORD_SIZE, comment);
    }

    /** Equivalent of calling `pushToStack` on top of stack. */
    public void pushToStack(int imm) {
        this.pushToStack(imm, this._slot);
    }

    /** Equivalent of calling `pushToStack` on top of stack. */
    public void pushToStack(boolean value) {
        this.pushToStack(value, this._slot);
    }

    /** Save immediate IMM to -(SLOT * WORD_SIZE) (FP).
     *  Register $A0 will be modified by this method. */
    public void pushToStack(int imm, int slot) {
        String loadComment = String.format("Load integer literal: %d", imm);
        String pushComment = String.format("Push to stack slot %d", slot);

        backend.emitLI(A0, imm, loadComment);
        backend.emitSW(A0, FP, slot * WORD_SIZE, pushComment);
    }

    /** Save boolean VALUE to -(SLOT * WORD_SIZE) (FP).
     *  Register $A0 will be modified by this method. */
    public void pushToStack(boolean value, int slot) {
        int imm = value ? 1 : 0;
        String loadComment = String.format("Load boolean literal: %b", value);
        String pushComment = String.format("Push to stack slot %d", slot);

        backend.emitLI(A0, imm, loadComment);
        backend.emitSW(A0, FP, slot * WORD_SIZE, pushComment);
    }
}
