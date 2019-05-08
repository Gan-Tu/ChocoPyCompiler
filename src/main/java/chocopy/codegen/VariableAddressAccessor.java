package chocopy.codegen;

import chocopy.common.codegen.Label;
import chocopy.common.codegen.RiscVBackend;
import chocopy.common.codegen.RiscVBackend.Register;
import chocopy.common.codegen.SymbolInfo;
import chocopy.common.codegen.FuncInfo;
import chocopy.common.analysis.SymbolTable;

/**
 * The variable accessors is a class that provides an interface
 * allows the address of a named variable to be read
 * into a specified register.
 */
public class VariableAddressAccessor extends VariableAccessor {

    public VariableAddressAccessor(FuncInfo funcInfo,
                                   SymbolTable<SymbolInfo> globalSymbols,
                                   RiscVBackend backend) {
        super(funcInfo, globalSymbols, backend);
    }

    protected void emitFromOffset(Register rd,
                                  Register rs,
                                  int offset,
                                  String comment) {
        this.backend.emitADDI(rd, rs, offset, comment);
    }

    protected void emitFromLabel(Register rd,
                                 Label label,
                                 String comment) {
        this.backend.emitLA(rd, label, comment);
    }
}
