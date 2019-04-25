package chocopy.semantic.errors;

public final class TypeErrorMessages {
    public static final String CONDITION_EXPR_TYPE = "Condition expression cannot be of type `%s`";
    public static final String NOT_A_VAR = "Not a variable: %s";
    public static final String NOT_NONLOCAL_VAR = "Not a nonlocal variable: %s";
    public static final String NOT_GLOBAL_VAR = "Not a global variable: %s";
    public static final String NOT_A_FUNC_OR_CLASS = "Not a function or class: %s";
    public static final String CANNOT_APPLY_UNOP = "Cannot apply operator `%s` on type `%s`";
    public static final String CANNOT_APPLY_BINOP = "Cannot apply operator `%s` on types `%s` and `%s`";
    public static final String NOT_EXPECTED_TYPE = "Expected type `%s`; got type `%s`";
    public static final String NO_RETURN = "Expected type `%s`; got `None`";
    public static final String NOT_EXPECTED_TYPE_IN_PARAM = "Expected type `%s`; got type `%s` in parameter %d";
    public static final String MULTIASSIGN_NONE_LIST = "Right-hand side of multiple assignment may not be [<None>]";
    public static final String WRONG_ARITY = "Expected %d arguments; got %d";
    public static final String NONINTEGER_INDEX = "Index is of non-integer type `%s`";
    public static final String CANNOT_INDEX_INTO_TYPE = "Cannot index into type `%s`";
    public static final String CANNOT_REDEFINE_ATTR = "Cannot re-define attribute: %s";
    public static final String CANNOT_SHADOW_CLASS_NAME = "Cannot shadow class name: %s";
    public static final String METHOD_OVERRIDE_DIFFERENT_SIG = "Method overridden with different type signature: %s";
    public static final String FIRST_PARAM_ENCLOSING_CLASS = "First parameter of the following method must be of the enclosing class: %s";
    public static final String INVALID_ANNOTATION = "Invalid type annotation; there is no class named: %s";
    public static final String DUPLICATE_ID_DECL = "Duplicate declaration of identifier in same scope: %s";
    public static final String SUPERCLASS_NOT_DEFINED = "Super-class not defined: %s";
    public static final String SUPERCLASS_MUST_BE_CLASS = "Super-class must be a class: %s";
    public static final String EXTEND_SPECIAL_TYPE = "Cannot extend special class: %s";
    public static final String NO_SUCH_ATTRIBUTE = "There is no attribute named `%s` in class `%s`";
    public static final String NO_SUCH_METHOD = "There is no method named `%s` in class `%s`";
    public static final String NOT_LIST_TYPE = "`%s` is not a list type";
    public static final String ACCESS_MEMBER_OF_NONCLASS = "Cannot access member of non-class type `%s`";
    public static final String NO_RETURN_TOP_LEVEL = "Return statement cannot appear at the top level";
    public static final String NO_IMPLICIT_ASSIGNMENT = "Cannot assign to variable that is not explicitly declared in this scope: %s";
    public static final String ALL_PATHS_MUST_RETURN = "All paths in this function/method must return a non-None value: %s";
    public static final String CANNOT_ITERATE_TYPE="Cannot iterate over value of type `%s`";
}
