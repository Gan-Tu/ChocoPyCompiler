package chocopy.semantic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static chocopy.common.analysis.types.SymbolType.INT_TYPE;
import static chocopy.common.analysis.types.SymbolType.STR_TYPE;
import static chocopy.common.analysis.types.SymbolType.BOOL_TYPE;
import static chocopy.common.analysis.types.SymbolType.NONE_TYPE;
import static chocopy.common.analysis.types.SymbolType.EMPTY_TYPE;
import static chocopy.common.analysis.types.SymbolType.OBJECT_TYPE;

import chocopy.common.analysis.types.ValueType;

/**
 * A data structure that stores the inheritance relationships
 * between classes, both predefined and user-defined.
 * It answers queries of type conformance (i.e., subtyping) as
 * well as computing joins (i.e., least upper bounds).
 */
public class InheritanceTable {

    /* the root of the class hierarchy */
    private ValueType rootType;

    /* a mapping between a ValueType to its parent ValueType */
    private HashMap<ValueType, ValueType> map;

    /* constructs a default inheritance table */
    public InheritanceTable() {
        this.map = new HashMap<>();
        this.rootType = OBJECT_TYPE;
        this.initPredefined();
    }

    /* Initializes predefined inheritance relationship between classes. */
    public void initPredefined() {
        this.extend(INT_TYPE, this.rootType);
        this.extend(STR_TYPE, this.rootType);
        this.extend(BOOL_TYPE, this.rootType);
        this.extend(NONE_TYPE, this.rootType);
        this.extend(EMPTY_TYPE, this.rootType);
        this.extend(this.rootType, this.rootType);
    }

    /* Return the immediate superclass of ValueType T, or the root type
     * (OBJECT_TYPE) if T is not defined explicatively. */
    public ValueType getSuper(ValueType t) {
        if (this.map.containsKey(t)) {
            return this.map.get(t);
        } else {
            return this.rootType;
        }
    }

    /**
     * Return all superclass of ValueType T, including itself,
     * or the root type (OBJECT_TYPE) if T is not defined explicatively.
     * The list is ordered by inheritance relationship.
     */
    public List<ValueType> getHierarchy(ValueType t) {
        List<ValueType> supertypes = new ArrayList<>();
        while (!this.rootType.equals(t)) {
            supertypes.add(t);
            t = this.getSuper(t);
        }
        supertypes.add(this.rootType);
        return supertypes;
    }

    /* A renamed method that acts exactly the same as `getSuper`. */
    public ValueType get(ValueType t) {
        return this.getSuper(t);
    }

    /* A renamed method that acts exactly the same as `getSuper`. */
    public ValueType parent(ValueType t) {
        return this.getSuper(t);
    }

    /* Store inheritance relationship between subclass and superclass.
     * ---
     * Note that this function does not check for potential errors in the
     * inheritance graph (e.g. circular inheritance, superclass not defined
     * yet, invalid inheritance - the superclass may not be one of int, str,
     * or bool. etc.). This error check should be handled by the caller. */
    public void extend(ValueType subclass, ValueType superclass) {
        this.map.put(subclass, superclass);
    }

    /* A renamed method that acts exactly the same as `extend`. */
    public void put(ValueType t1, ValueType t2) {
        this.extend(t1, t2);
    }

    /* Return true if there is assignment compatibility for T1 ≤a T2
     * Note that this is DIFFERENT from "isSubclass" function, which
     * checks ONLY for subclass comfortability, not NONE and list types. */
    public boolean isCompatible(ValueType t1, ValueType t2) {
        if (t1 == null || t2 == null) {
            return false;
        } else if (this.isSubclass(t1, t2)) {
            return true;
        } else if (t1.equals(NONE_TYPE)) {
            return !t2.isSpecialType();
        } else if (t1.equals(EMPTY_TYPE)) {
            return t2.isListType();
        } else if (t1.isListType() &&
                t1.elementType().equals(NONE_TYPE)) {
            return t2.isListType() &&
                    this.isCompatible(NONE_TYPE, t2.elementType());
        }
        return false;
    }

    /* Return the join of two types.
     * - if T1 ≤a T2, return T2
     * - if T2 ≤a T1, return T1
     * - else return the least upper bound of T1 and T2
     */
    public ValueType join(ValueType t1, ValueType t2) {
        if (this.isCompatible(t1, t2)) {
            return t2;
        } else if (this.isCompatible(t2, t1)) {
            return t1;
        } else {
            return this.leastUpperBound(t1, t2);
        }
    }

    /* Return true if ValueType T1 equals, or is a subclass of, T2.
     * This method is private because you should not call it directly.
     * Use 'isCompatible' instead for assignment compatibility.
     */
    private boolean isSubclass(ValueType t1, ValueType t2) {
        if (t1 == null || t2 == null) {
            return false;
        } else if (t1.equals(t2)) {
            return true;
        } else if (t2.isListType()) {
            // nothing is a subclass of [T], except an exact same self
            return false;
        } else if (t1.isListType()) {
            // [T] <= OBJECT_TYPE only
            return t2.equals(this.rootType);
        } else if (t1.equals(t2) || t2.equals(this.rootType)) {
            return true;
        } else if (t1.equals(this.rootType)) {
            // t2.equals(this.rootType) is not true from last branch
            return false;
        } else if (this.map.containsKey(t2) && this.map.get(t2).equals(t1)) {
            // early-stopping to make recursion more efficient
            // if t2 < t1, then t1 is not a subclass (but superclass) of t2
            return false;
        } else if (this.map.containsKey(t1)) {
            // if map[t1] = t2, it will return true by the
            // next recursion's base case above
            return this.isSubclass(this.getSuper(t1), t2);
        } else {
            return false;
        }
    }

    /* Return the least upper bound for two ValueTypes in O(n) time.
     * This method is private because you should not call it directly.
     * Use 'join' instead to join two types.
     */
    private ValueType leastUpperBound(ValueType t1, ValueType t2) {
        if (t1 == null || t2 == null) {
            return null;
        } else if (t1.equals(this.rootType) || t2.equals(this.rootType)) {
            return this.rootType;
        }
        HashSet<ValueType> supertypesOft1 = new HashSet<>(this.getHierarchy(t1));
        while (!t2.equals(this.rootType)) {
            if (supertypesOft1.contains(t2)) {
                return t2;
            }
            t2 = this.getSuper(t2);
        }
        return this.rootType;
    }

}
