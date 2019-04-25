import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;

import chocopy.common.analysis.types.ValueType;
import chocopy.common.analysis.types.ListValueType;
import chocopy.common.analysis.types.ClassValueType;

import static chocopy.common.analysis.types.SymbolType.INT_TYPE;
import static chocopy.common.analysis.types.SymbolType.STR_TYPE;
import static chocopy.common.analysis.types.SymbolType.BOOL_TYPE;
import static chocopy.common.analysis.types.SymbolType.NONE_TYPE;
import static chocopy.common.analysis.types.SymbolType.EMPTY_TYPE;
import static chocopy.common.analysis.types.SymbolType.OBJECT_TYPE;

import chocopy.semantic.InheritanceTable;

public class InheritanceTableTest {

    @Test
    public void test_basic_compatible() {
        InheritanceTable table = new InheritanceTable();
        ArrayList<ValueType> types = new ArrayList<>();
        types.add(INT_TYPE);
        types.add(STR_TYPE);
        types.add(BOOL_TYPE);
        types.add(OBJECT_TYPE);
        types.add(NONE_TYPE);
        types.add(EMPTY_TYPE);
        types.add(new ClassValueType("custom"));

        for (ValueType t : types) {
            // types are compatible with itself
            assertTrue(table.isCompatible(t, t));
            // all types are compatible with objects
            assertTrue(table.isCompatible(t, OBJECT_TYPE));
            // but not the reverse
            if (!t.equals(OBJECT_TYPE)) {
                assertFalse(table.isCompatible(OBJECT_TYPE, t));
            }
        }

    }

    @Test
    public void test_list_compatible() {
        InheritanceTable table = new InheritanceTable();
        ArrayList<ValueType> types = new ArrayList<>();
        types.add(INT_TYPE);
        types.add(STR_TYPE);
        types.add(BOOL_TYPE);
        types.add(OBJECT_TYPE);
        types.add(NONE_TYPE);
        types.add(new ClassValueType("custom"));


        ListValueType objectList = new ListValueType(OBJECT_TYPE);
        for (ValueType t : types) {
            ListValueType t_list = new ListValueType(t);
            // empty list ≤a [T], but not the reverse
            assertTrue(table.isCompatible(EMPTY_TYPE, t_list));
            assertFalse(table.isCompatible(t_list, EMPTY_TYPE));
            // list types only conform to OBJECT_TYPE
            assertTrue(table.isCompatible(t_list, OBJECT_TYPE));
            assertFalse(table.isCompatible(OBJECT_TYPE, t_list));
            assertTrue(table.isCompatible(t_list, t_list));
            assertEquals(t.equals(OBJECT_TYPE) || t.equals(NONE_TYPE),
                         table.isCompatible(t_list, objectList));
        }


        // [None] conforms with [T] if <None> ≤a T
        ListValueType noneList = new ListValueType(NONE_TYPE);
        for (ValueType t : types) {
            ListValueType t_list = new ListValueType(t);
            if (t.isSpecialType()) {
                assertFalse(table.isCompatible(noneList, t_list));
            } else {
                assertTrue(table.isCompatible(noneList, t_list));
            }
            if (!t.equals(NONE_TYPE)) {
                assertFalse(table.isCompatible(t_list, noneList));
            } else {
                assertTrue(table.isCompatible(t_list, noneList));
            }
        }
    }

    @Test
    public void test_nested_list_compatible() {
        InheritanceTable table = new InheritanceTable();
        ArrayList<ValueType> types = new ArrayList<>();
        types.add(INT_TYPE);
        types.add(STR_TYPE);
        types.add(BOOL_TYPE);
        types.add(OBJECT_TYPE);
        types.add(NONE_TYPE);
        types.add(new ClassValueType("custom"));


        ListValueType objectList = new ListValueType(OBJECT_TYPE);
        ListValueType objectList_nested = new ListValueType(objectList);
        for (ValueType t : types) {
            ListValueType t_list = new ListValueType(t);
            ListValueType t_list_nested = new ListValueType(t_list);
            // empty list ≤a [T], but not the reverse
            assertTrue(table.isCompatible(EMPTY_TYPE, t_list_nested));
            assertFalse(table.isCompatible(t_list_nested, EMPTY_TYPE));
            // list types only conform to OBJECT_TYPE
            assertTrue(table.isCompatible(t_list_nested, OBJECT_TYPE));
            assertFalse(table.isCompatible(OBJECT_TYPE, t_list_nested));
            // list types are not compatible with each other, even
            // itself, except for [<None>]
            if (!t.equals(NONE_TYPE)) {
                assertTrue(table.isCompatible(t_list_nested, t_list_nested));
                assertEquals(t.equals(OBJECT_TYPE),
                    table.isCompatible(t_list_nested, objectList_nested));

                assertFalse(table.isCompatible(t_list, t_list_nested));
                assertFalse(table.isCompatible(t_list_nested, t_list));
            }
        }


        // [None] conforms with [T] if <None> ≤a T
        ListValueType noneList = new ListValueType(NONE_TYPE);
        ListValueType noneList_nested = new ListValueType(noneList);
        for (ValueType t : types) {
            ListValueType t_list = new ListValueType(t);
            ListValueType t_list_nested = new ListValueType(t_list);

            // it's fine to have [int], [str], [boolean] are special type
            assertTrue(table.isCompatible(noneList, t_list_nested));
            assertFalse(table.isCompatible(t_list_nested, noneList));

            assertEquals(t.equals(NONE_TYPE), table.isCompatible(noneList_nested, t_list_nested));
            assertEquals(t.equals(NONE_TYPE),table.isCompatible(t_list_nested, noneList_nested));

            assertFalse(table.isCompatible(noneList_nested, t_list));
            if (!t.equals(NONE_TYPE)) {
                assertFalse(table.isCompatible(t_list, noneList_nested));
            } else {
                assertTrue(table.isCompatible(t_list, noneList_nested));
            }
        }
    }


    @Test
    public void test_list_join() {
        InheritanceTable table = new InheritanceTable();
        ArrayList<ValueType> types = new ArrayList<>();
        types.add(INT_TYPE);
        types.add(STR_TYPE);
        types.add(BOOL_TYPE);
        types.add(OBJECT_TYPE);
        types.add(NONE_TYPE);
        types.add(new ClassValueType("custom"));

        for (ValueType t : types) {
            ListValueType t_list = new ListValueType(t);
            ListValueType t_nested_list = new ListValueType(t_list);
            // check joins on list
            assertEquals(t_list, table.join(t_list, t_list));
            assertEquals(t_nested_list, table.join(t_nested_list, t_nested_list));
            if (t.equals(NONE_TYPE)) {
                // <None> join [<None>] should be [<None>]
                assertEquals(t_list, table.join(t, t_list));
                assertEquals(t_list, table.join(t_list, t));
                // <None> join [[<None>]] should be [[<None>]]
                assertEquals(t_nested_list, table.join(t, t_nested_list));
                assertEquals(t_nested_list, table.join(t_nested_list, t));
                // [<None>] join [[<None>]] should be  [[<None>]]
                // Because  T1:[<None>] ≤a T2:[T], if <None> ≤a T
                // and <None> ≤a [<None>]
                // ?? TODO: is this true?
                assertEquals(t_nested_list, table.join(t_list, t_nested_list));
                assertEquals(t_nested_list, table.join(t_nested_list, t_list));
            } else {
                assertEquals(OBJECT_TYPE, table.join(t, t_list));
                assertEquals(OBJECT_TYPE, table.join(t_list, t));
                assertEquals(OBJECT_TYPE, table.join(t, t_nested_list));
                assertEquals(OBJECT_TYPE, table.join(t_nested_list, t));
                assertEquals(OBJECT_TYPE, table.join(t_list, t_nested_list));
                assertEquals(OBJECT_TYPE, table.join(t_nested_list, t_list));
            }
        }

    }

    @Test
    public void test_class_join() {
        InheritanceTable table = new InheritanceTable();

        // custom class type
        ClassValueType a = new ClassValueType("a");
        ClassValueType b = new ClassValueType("b");
        ClassValueType c = new ClassValueType("c");
        ClassValueType d = new ClassValueType("d");
        ClassValueType e = new ClassValueType("e");
        ClassValueType f = new ClassValueType("f");
        ClassValueType g = new ClassValueType("g");

        // set inheritance graph:
        //      object <- a <- c
        //      object <- b <- d <- e
        //                       <- f
        //                  <- g

        table.extend(a, OBJECT_TYPE);
        table.extend(b, OBJECT_TYPE);
        table.extend(c, a);
        table.extend(d, b);
        table.extend(e, d);
        table.extend(f, d);
        table.extend(g, b);

        // size of super
        assertEquals(2, table.getHierarchy(a).size());
        assertEquals(3, table.getHierarchy(c).size());
        assertEquals(2, table.getHierarchy(b).size());
        assertEquals(3, table.getHierarchy(d).size());
        assertEquals(3, table.getHierarchy(g).size());
        assertEquals(4, table.getHierarchy(e).size());
        assertEquals(4, table.getHierarchy(f).size());

        // all possible joins of A
        assertEquals(a,           table.join(a, a));
        assertEquals(OBJECT_TYPE, table.join(a, b));
        assertEquals(a,           table.join(a, c));
        assertEquals(OBJECT_TYPE, table.join(a, d));
        assertEquals(OBJECT_TYPE, table.join(a, e));
        assertEquals(OBJECT_TYPE, table.join(a, f));
        assertEquals(OBJECT_TYPE, table.join(a, g));

        // all possible joins of b
        assertEquals(OBJECT_TYPE, table.join(b, a));
        assertEquals(b,           table.join(b, b));
        assertEquals(OBJECT_TYPE, table.join(b, c));
        assertEquals(b,           table.join(b, d));
        assertEquals(b,           table.join(b, e));
        assertEquals(b,           table.join(b, f));
        assertEquals(b,           table.join(b, g));

        // all possible joins of c
        assertEquals(a,           table.join(c, a));
        assertEquals(OBJECT_TYPE, table.join(c, b));
        assertEquals(c,           table.join(c, c));
        assertEquals(OBJECT_TYPE, table.join(c, d));
        assertEquals(OBJECT_TYPE, table.join(c, e));
        assertEquals(OBJECT_TYPE, table.join(c, f));
        assertEquals(OBJECT_TYPE, table.join(c, g));

        // all possible joins of d
        assertEquals(OBJECT_TYPE, table.join(d, a));
        assertEquals(b,           table.join(d, b));
        assertEquals(OBJECT_TYPE, table.join(d, c));
        assertEquals(d,           table.join(d, d));
        assertEquals(d,           table.join(d, e));
        assertEquals(d,           table.join(d, f));
        assertEquals(b,           table.join(d, g));

        // all possible joins of e
        assertEquals(OBJECT_TYPE, table.join(e, a));
        assertEquals(b,           table.join(e, b));
        assertEquals(OBJECT_TYPE, table.join(e, c));
        assertEquals(d,           table.join(e, d));
        assertEquals(e,           table.join(e, e));
        assertEquals(d,           table.join(e, f));
        assertEquals(b,           table.join(e, g));

        // all possible joins of f
        assertEquals(OBJECT_TYPE, table.join(f, a));
        assertEquals(b,           table.join(f, b));
        assertEquals(OBJECT_TYPE, table.join(f, c));
        assertEquals(d,           table.join(f, d));
        assertEquals(d,           table.join(f, e));
        assertEquals(f,           table.join(f, f));
        assertEquals(b,           table.join(f, g));

        // all possible joins of g
        assertEquals(OBJECT_TYPE, table.join(g, a));
        assertEquals(b,           table.join(g, b));
        assertEquals(OBJECT_TYPE, table.join(g, c));
        assertEquals(b,           table.join(g, d));
        assertEquals(b,           table.join(g, e));
        assertEquals(b,           table.join(g, f));
        assertEquals(g,           table.join(g, g));

    }

    @Test
    public void test_class_compatible() {
        InheritanceTable table = new InheritanceTable();

        // custom class type
        ClassValueType a = new ClassValueType("a");
        ClassValueType b = new ClassValueType("b");
        ClassValueType c = new ClassValueType("c");
        ClassValueType d = new ClassValueType("d");
        ClassValueType e = new ClassValueType("e");
        ClassValueType f = new ClassValueType("f");
        ClassValueType g = new ClassValueType("g");

        // set inheritance graph:
        //      object <- a <- c
        //      object <- b <- d <- e
        //                       <- f
        //                  <- g

        table.extend(a, OBJECT_TYPE);
        table.extend(b, OBJECT_TYPE);
        table.extend(c, a);
        table.extend(d, b);
        table.extend(e, d);
        table.extend(f, d);
        table.extend(g, b);

        // size of super
        assertEquals(2, table.getHierarchy(a).size());
        assertEquals(3, table.getHierarchy(c).size());
        assertEquals(2, table.getHierarchy(b).size());
        assertEquals(3, table.getHierarchy(d).size());
        assertEquals(3, table.getHierarchy(g).size());
        assertEquals(4, table.getHierarchy(e).size());
        assertEquals(4, table.getHierarchy(f).size());


        // all possible isCompatibles of A
        assertTrue(table.isCompatible(a, a));
        assertFalse(table.isCompatible(a, b));
        assertFalse(table.isCompatible(a, c));
        assertFalse(table.isCompatible(a, d));
        assertFalse(table.isCompatible(a, e));
        assertFalse(table.isCompatible(a, f));
        assertFalse(table.isCompatible(a, g));
        assertTrue(table.isCompatible(a, OBJECT_TYPE));

        // all possible isCompatibles of b
        assertFalse(table.isCompatible(b, a));
        assertTrue(table.isCompatible(b, b));
        assertFalse(table.isCompatible(b, c));
        assertFalse(table.isCompatible(b, d));
        assertFalse(table.isCompatible(b, e));
        assertFalse(table.isCompatible(b, f));
        assertFalse(table.isCompatible(b, g));
        assertTrue(table.isCompatible(b, OBJECT_TYPE));

        // all possible isCompatibles of c
        assertTrue(table.isCompatible(c, a));
        assertFalse(table.isCompatible(c, b));
        assertTrue(table.isCompatible(c, c));
        assertFalse(table.isCompatible(c, d));
        assertFalse(table.isCompatible(c, e));
        assertFalse(table.isCompatible(c, f));
        assertFalse(table.isCompatible(c, g));
        assertTrue(table.isCompatible(c, OBJECT_TYPE));

        // all possible isCompatibles of d
        assertFalse(table.isCompatible(d, a));
        assertTrue(table.isCompatible(d, b));
        assertFalse(table.isCompatible(d, c));
        assertTrue(table.isCompatible(d, d));
        assertFalse(table.isCompatible(d, e));
        assertFalse(table.isCompatible(d, f));
        assertFalse(table.isCompatible(d, g));
        assertTrue(table.isCompatible(d, OBJECT_TYPE));

        // all possible isCompatibles of e
        assertFalse(table.isCompatible(e, a));
        assertTrue(table.isCompatible(e, b));
        assertFalse(table.isCompatible(e, c));
        assertTrue(table.isCompatible(e, d));
        assertTrue(table.isCompatible(e, e));
        assertFalse(table.isCompatible(e, f));
        assertFalse(table.isCompatible(e, g));
        assertTrue(table.isCompatible(e, OBJECT_TYPE));

        // all possible isCompatibles of f
        assertFalse(table.isCompatible(f, a));
        assertTrue(table.isCompatible(f, b));
        assertFalse(table.isCompatible(f, c));
        assertTrue(table.isCompatible(f, d));
        assertFalse(table.isCompatible(f, e));
        assertTrue(table.isCompatible(f, f));
        assertFalse(table.isCompatible(f, g));
        assertTrue(table.isCompatible(f, OBJECT_TYPE));

        // all possible isCompatibles of g
        assertFalse(table.isCompatible(g, a));
        assertTrue(table.isCompatible(g, b));
        assertFalse(table.isCompatible(g, c));
        assertFalse(table.isCompatible(g, d));
        assertFalse(table.isCompatible(g, e));
        assertFalse(table.isCompatible(g, f));
        assertTrue(table.isCompatible(g, g));
        assertTrue(table.isCompatible(g, OBJECT_TYPE));

    }


}

