package chocopy.semantic;

import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.SymbolType;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.analysis.types.ClassValueType;
import chocopy.common.analysis.types.FuncType;
import chocopy.common.analysis.types.ListValueType;
import chocopy.semantic.ClassDefType;

public class DeclarationNode {

    private final SymbolType declarationType;
    public SymbolTable<DeclarationNode> sym;

    public DeclarationNode(SymbolType type,
                           SymbolTable<DeclarationNode> sym) {
        this.declarationType = type;
        this.sym = sym;
    }

    public DeclarationNode(SymbolType type) {
        this(type, new SymbolTable<>());
    }

    public SymbolType getType() {
        return this.declarationType;
    }

    public boolean isVarDef() {
        return this.isClassValueType() || this.isListType();
    }

    public boolean isClassDef() {
        return this.declarationType instanceof ClassDefType;
    }

    public boolean isFuncDef() {
        return this.isFuncType();
    }

    public boolean isClassValueType() {
        return this.declarationType instanceof ClassValueType;
    }

    public boolean isFuncType() {
        return this.declarationType instanceof FuncType;
    }

    /**
     * Return true iff this is a type that does not include the value None.
     */
    public boolean isSpecialType() {
        return this.declarationType.isSpecialType();
    }

    public boolean isSpecialClass() {
        if (this.isClassDef()) {
            ClassDefType classDefType = (ClassDefType) this.declarationType;
            return classDefType.classValueType().isSpecialType();
        }
        return false;
    }

    public boolean isListType() {
        return this.declarationType instanceof ListValueType;
    }

    public boolean isValueType() {
        return this.declarationType instanceof ValueType;
    }

    public ValueType elementType() {
        ListValueType listType = (ListValueType) this.declarationType;
        return listType.elementType();
    }

    public String className() {
        return this.declarationType.className();
    }

    public ClassValueType classValueType() {
        ClassDefType classValueType = (ClassDefType) this.declarationType;
        return classValueType.classValueType();
    }

}
