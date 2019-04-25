package chocopy.semantic;

import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.ClassValueType;
import chocopy.common.analysis.types.ValueType;

import java.util.List;

public class ClassUtilities {
    /**
     * Traverses the class hierarchy and builds a list of all attributes
     * and methods contained within the hierarchy. The returned list
     * correctly handles function overloading as well.
     */
    public static SymbolTable<DeclarationNode> getBaseClassDeclaration(ClassValueType classType,
                                                                       InheritanceTable inheritanceTable,
                                                                       SymbolTable<DeclarationNode> globals) {
        SymbolTable<DeclarationNode> inherited = new SymbolTable<>();

        List<ValueType> superclassTypes = inheritanceTable.getHierarchy(classType);

        // traverse the ordered inheritance relationship to ensure correct function overloading
        for (int i = superclassTypes.size() - 1; i >= 0; i--) {
            ValueType type = superclassTypes.get(i);
            ClassValueType superclassValueType = (ClassValueType) type;
            DeclarationNode inheritedDecl = globals.get(superclassValueType.className());
            SymbolTable<DeclarationNode> superclassSym = inheritedDecl.sym;

            for (String name : superclassSym.getDeclaredSymbols()) {
                inherited.put(name, superclassSym.get(name));
            }
        }

        return inherited;
    }
}
