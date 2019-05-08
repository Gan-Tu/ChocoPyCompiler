package chocopy.codegen;

import chocopy.common.analysis.SymbolTable;
import chocopy.common.astnodes.MemberExpr;
import chocopy.common.codegen.FuncInfo;
import chocopy.common.codegen.RiscVBackend;
import chocopy.common.codegen.RiscVBackend.Register;
import chocopy.common.codegen.SymbolInfo;

import static chocopy.common.codegen.RiscVBackend.Register.FP;

public class StackRecord {

    /**
     * The backend that emits assembly.
     */
    private final RiscVBackend backend;

    /**
     * Convenience variable: the word size for the current backend.
     */
    public final int WORD_SIZE;

    /**
     * Current slot our internal SP points at.
     * - ADDR = -(SLOT * WORD_SIZE) (FP).
     */
    private int slot;

    /**
     * The symbol table that maps global names to information about
     * the bound global variables, global functions, or classes.
     */
    protected final SymbolTable<SymbolInfo> globalSymbols;

    /**
     * The variable accessors for variable values
     */
    private VariableValueAccessor valueAccessor;

    /**
     * The variable accessors for variable addresses
     */
    private VariableAddressAccessor addressAccessor;


    public StackRecord(RiscVBackend backend,
                       SymbolTable<SymbolInfo> globalSymbols) {
        this.backend = backend;
        this.WORD_SIZE = backend.getWordSize();
        this.slot = 0;
        this.globalSymbols = globalSymbols;
        this.valueAccessor = null;
        this.addressAccessor = null;
    }

    /**
     * Set the current variable accessor for FUNCINFO.
     */
    public void setFunctionInfo(FuncInfo funcInfo) {
        if (this.valueAccessor == null) {
            this.valueAccessor =
                    new VariableValueAccessor(funcInfo, this.globalSymbols, this.backend);
        } else {
            this.valueAccessor.setFunctionInfo(funcInfo);
        }

        if (this.addressAccessor == null) {
            this.addressAccessor =
                    new VariableAddressAccessor(funcInfo, this.globalSymbols, this.backend);
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

    /**
     * Increment internal SP by one.
     */
    public void upSlot() {
        this.slot += 1;
    }

    /**
     * Decrement internal SP by one.
     */
    public void downSlot() {
        this.slot -= 1;
    }

    /**
     * Increment internal SP by SLOTS.
     */
    public void upSlot(int slots) {
        this.slot += slots;
    }

    /**
     * Decrement internal SP by SLOTS.
     */
    public void downSlot(int slots) {
        this.slot -= slots;
    }

    /**
     * Set internal SP to given SLOT.
     */
    public void setSlot(int slot) {
        this.slot = slot;
    }

    /**
     * Return internal SP to given SLOT.
     */
    public int getSlot(int slot) {
        return this.slot;
    }


    /*--------------------------------------------------------------*/
    /*                                                              */
    /*                  VARIABLE ACCESS OPERATIONS                  */
    /*                                                              */
    /*--------------------------------------------------------------*/

    /**
     * Read value of a variable NAME from current function into register RD.
     * The value can be a parameter, local, inherited, or global variable.
     */
    public void readVariable(Register rd, String name) {
        assert this.valueAccessor != null : "value variable accessors hasn't been set";
        this.valueAccessor.readVariable(rd, name);
    }

    /**
     * Load address of a variable NAME from current function into register RD.
     * The value can be a parameter, local, inherited, or global variable.
     */
    public void readVariableAddress(Register rd, String name) {
        assert this.addressAccessor != null : "address variable accessors hasn't been set";
        this.addressAccessor.readVariable(rd, name);
    }

    /**
     * Load value of an attribute from an object instance pointer
     * located in register RS and stores it in register RD.
     *
     * @param rd   Destination for the attribute.
     * @param rs   Register in which the address for the object instance is located.
     * @param expr The member expression.
     */
    public void readAttribute(Register rd, Register rs, MemberExpr expr) {
        assert this.valueAccessor != null : "value variable accessors hasn't been set";
        this.valueAccessor.readAttribute(rd, rs, expr);
    }

    /**
     * Load  address, of an attribute from an object instance pointer
     * located in register RS and stores it in register RD.
     *
     * @param rd   Destination for the attribute.
     * @param rs   Register in which the address for the object instance is located.
     * @param expr The member expression.
     */
    public void readAttributeAddress(Register rd, Register rs, MemberExpr expr) {
        assert this.addressAccessor != null : "address variable accessors hasn't been set";
        this.addressAccessor.readAttribute(rd, rs, expr);
    }

    /**
     * Read value from top of stack to register RD.
     */
    public void peekFromStack(Register rd, String comment) {
        backend.emitLW(rd, FP, -this.slot * WORD_SIZE, comment);
    }

    /**
     * Pop value from top of stack to register RD.
     */
    public void popFromStack(Register rd, String comment) {
        backend.emitLW(rd, FP, -this.slot * WORD_SIZE, comment);
        this.downSlot();
    }

    /**
     * Equivalent of calling `pushToStack` on top of stack.
     */
    public void pushToStack(Register rd, String comment) {
        this.upSlot();
        backend.emitSW(rd, FP, -this.slot * WORD_SIZE, comment);
    }

    /**
     * Equivalent of calling `pushToStack` on top of stack, with default comment.
     */
    public void pushToStack(Register rd) {
        this.pushToStack(rd, String.format("Push to stack slot %d", this.slot));
    }

    /**
     * Equivalent of calling `popFromStack` from top of stack, with default comment.
     */
    public void popFromStack(Register rd) {
        this.popFromStack(rd, String.format("Pop from stack slot %d", this.slot));
    }
}
