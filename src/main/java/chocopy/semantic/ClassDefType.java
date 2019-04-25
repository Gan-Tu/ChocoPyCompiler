package chocopy.semantic;

import java.util.Objects;

import chocopy.common.analysis.types.ClassValueType;
import chocopy.common.analysis.types.SymbolType;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.ClassType;

/**
 * Semantic information for a class definition.
 */
public class ClassDefType extends SymbolType {

    /**
     * The name of the class.
     */
    private final String className;

    /**
     * The name of the superclass.
     */
    private String superclassName;

    /**
     * The ClassValueType reference for this class definition.
     */
    private final ClassValueType classValueType;

    /**
     * A class type for the class named CLASSNAME.
     */
    public ClassDefType(String className, String superclassName) {
        this.className = className;
        this.classValueType = new ClassValueType(className);
        this.superclassName = superclassName;
    }

    /**
     * A class type for the class referenced by CLASSTYPEANNOTATION.
     */
    public ClassDefType(ClassType classTypeAnnotation) {
        this.className = classTypeAnnotation.className;
        this.classValueType = new ClassValueType(this.className);
    }

    @Override
    public String className() {
        return this.className;
    }

    public String superclassName() {
        return this.superclassName;
    }

    public void setSuperclass(String superclassName) {
        this.superclassName = superclassName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        ClassDefType classType = (ClassDefType) o;
        return Objects.equals(this.className, classType.className);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.className);
    }

    @Override
    public String toString() {
        return this.className;
    }

    /**
     * Return the ClassValueType reference of this class definition.
     */
    public ClassValueType classValueType() {
        return this.classValueType;
    }
}
