package chocopy.semantic;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.FuncType;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.analysis.types.ClassValueType;

import chocopy.common.astnodes.Declaration;
import chocopy.common.astnodes.Errors;
import chocopy.common.astnodes.Identifier;
import chocopy.common.astnodes.Program;
import chocopy.common.astnodes.VarDef;
import chocopy.common.astnodes.FuncDef;
import chocopy.common.astnodes.ClassDef;
import chocopy.common.astnodes.TypedVar;
import chocopy.common.astnodes.GlobalDecl;
import chocopy.common.astnodes.NonLocalDecl;
import chocopy.common.astnodes.TypeAnnotation;
import chocopy.common.astnodes.ClassType;
import chocopy.common.astnodes.ListType;
import chocopy.semantic.errors.TypeErrorMessages;

import static chocopy.common.analysis.types.SymbolType.OBJECT_TYPE;
import static chocopy.common.analysis.types.SymbolType.INT_TYPE;
import static chocopy.common.analysis.types.SymbolType.STR_TYPE;
import static chocopy.common.analysis.types.SymbolType.BOOL_TYPE;
import static chocopy.common.analysis.types.SymbolType.NONE_TYPE;
import static chocopy.common.analysis.types.SymbolType.EMPTY_TYPE;


/**
 * Analyzes declarations to create a top-level symbol table.
 */
public class DeclarationAnalyzer extends AbstractNodeAnalyzer<DeclarationNode> {

    /**
     * The inheritance relationship between classes
     */
    private final InheritanceTable inheritanceTable;
    /**
     * Current symbol table.  Changes with new declarative region.
     */
    private SymbolTable<DeclarationNode> sym = new SymbolTable<>();
    /**
     * Global symbol table.
     */
    private final SymbolTable<DeclarationNode> globals = this.sym;
    /**
     * Receiver for semantic error messages.
     */
    private final Errors errors;
    /** Class Names, invalid or valid **/
    private HashSet<String> classNames;

    /**
     * A new declaration analyzer sending errors to ERRORS0, and saving
     * inheritance relationship in INHERITANCETABLE.
     */
    public DeclarationAnalyzer(Errors errors0, InheritanceTable table) {
        this.errors = errors0;
        this.classNames = new HashSet<>();
        this.initFunction();
        this.initClass();
        this.inheritanceTable = table;
    }

    /**
     * Initializes predefined functions.
     */
    private void initFunction() {
        // def print(x:object) -> None
        FuncType printFnType = new FuncType(new ArrayList<>(), NONE_TYPE);
        printFnType.parameters.add(OBJECT_TYPE);
        this.sym.put("print", new DeclarationNode(printFnType,
                                                  this.getEmptyDeclRegion()));

        // initialize: def input() -> str
        FuncType inputFnType = new FuncType(new ArrayList<>(), STR_TYPE);
        this.sym.put("input", new DeclarationNode(inputFnType,
                                                  this.getEmptyDeclRegion()));

        // initialize: def len(x:object) -> int
        FuncType lenFnType = new FuncType(new ArrayList<>(), INT_TYPE);
        lenFnType.parameters.add(OBJECT_TYPE);
        this.sym.put("len", new DeclarationNode(lenFnType,
                                                this.getEmptyDeclRegion()));
    }

    /**
     * Initializes predefined classes.
     */
    private void initClass() {
        ArrayList<String> classes = new ArrayList<>();
        // this way of initializing it ensures the name is updated with
        // default SymbolType construction
        classes.add(OBJECT_TYPE.className());
        classes.add(INT_TYPE.className());
        classes.add(STR_TYPE.className());
        classes.add(BOOL_TYPE.className());
        classes.add(NONE_TYPE.className());
        classes.add(EMPTY_TYPE.className());

        for (String className : classes) {
            // The predefined classes object, int, bool, and str each define
            // an __init__ method.
            List<ValueType> parameters = new ArrayList<>();
            parameters.add(new ClassValueType(className));
            FuncType initFuncType = new FuncType(parameters, NONE_TYPE);

            SymbolTable<DeclarationNode> classSym = new SymbolTable<>(this.sym);
            if (!NONE_TYPE.className().equals(className) &&
                    !EMPTY_TYPE.className().equals(className)) {
                // no __init__ method for NONE_TYPE and EMPTY_TYPE
                classSym.put("__init__", new DeclarationNode(initFuncType,
                                                             this.getEmptyDeclRegion()));
            }

            ClassDefType classDef = new ClassDefType(className, "object");
            this.sym.put(className, new DeclarationNode(classDef, classSym));
            this.classNames.add(className);
        }
    }

    public SymbolTable<DeclarationNode> getGlobals() {
        return this.globals;
    }

    @Override
    public DeclarationNode analyze(Program program) {

        // fetch all class names, valid or not
        for (Declaration decl : program.declarations) {
            if (decl instanceof ClassDef) {
                this.classNames.add(decl.getIdentifier().name);
            }
        }

        // analyze declarations
        this.analyzeDeclarations(program.declarations);

        return null;
    }

    @Override
    public DeclarationNode analyze(VarDef varDef) {
        return new DeclarationNode(
                        ValueType.annotationToValueType(varDef.var.type),
                        this.getEmptyDeclRegion()
                   );
    }

    @Override
    public DeclarationNode analyze(ClassDef classDef) {
        Identifier id = classDef.getIdentifier();
        Identifier superclassId = classDef.superClass;

        String className = id.name;
        String superclassName = superclassId.name;

        if (!this.globals.declares(superclassName)) {
            this.errors.semError(superclassId, TypeErrorMessages.SUPERCLASS_NOT_DEFINED, superclassName);
            // technically, parser should catch the error, if superclass is not defined
            // but in case of malformed AST tree, we will treat all classes a subclass of
            // the OBJECT root class.
            superclassName = "object";
        }

        DeclarationNode superclass = this.globals.get(superclassName);

        // checks superclass validity
        boolean validSuperclass = true;

        if (!superclass.isClassDef()) {
            this.errors.semError(superclassId, TypeErrorMessages.SUPERCLASS_MUST_BE_CLASS, superclassName);
            superclassName = "object";
            validSuperclass = false;
        } else if (superclass.isSpecialClass()) {
            this.errors.semError(superclassId, TypeErrorMessages.EXTEND_SPECIAL_TYPE, superclassName);
            superclassName = "object";
            validSuperclass = false;
        }

        DeclarationNode classDecl = new DeclarationNode(
                                            new ClassDefType(className, superclassName),
                                            this.getEmptyDeclRegion()
                                        );


         if (validSuperclass && !this.globals.declares(className)) {
            // store inheritance, only if superclass is valid
            // and class name is not a duplicate of existing class
            // --> so we don't redefine the inheritance table due to second,
            // invalid class definition
            this.inheritanceTable.extend(
                    classDecl.classValueType(),
                    superclass.classValueType()
            );
        }

        return classDecl;
    }

    @Override
    public DeclarationNode analyze(FuncDef funcDef) {
        // create a new declarative region
        SymbolTable<DeclarationNode> parentSym = this.sym;
        this.sym = new SymbolTable<>(parentSym);

        // analyze function signatures
        List<ValueType> parameters = new ArrayList<>();
        ValueType returnType = ValueType.annotationToValueType(funcDef.returnType);

        for (TypedVar p : funcDef.params) {
            Identifier id = p.identifier;
            String name = id.name;

            if (this.shadowsClassName(name)) {
                // function parameters may not shadow class names
                this.errors.semError(id, TypeErrorMessages.CANNOT_SHADOW_CLASS_NAME, name);
                continue;
            } else if (this.sym.declares(name)) {
                // no duplicate parameter declaration
                this.raiseDuplicateDeclarationError(id);
            }

            ValueType pType = ValueType.annotationToValueType(p.type);
            parameters.add(pType);

            this.sym.put(id.name, new DeclarationNode(pType, this.getEmptyDeclRegion()));
        }

        // construct function type
        FuncType fnType = new FuncType(parameters, returnType);
        DeclarationNode fnDecl = new DeclarationNode(fnType, this.sym);

        // restore the declarative region to the parent;
        this.sym = parentSym;

        return fnDecl;
    }

    @Override
    public DeclarationNode analyze(GlobalDecl globalDecl) {
        Identifier id = globalDecl.getIdentifier();
        String name = id.name;

        if (this.sym.getParent() == null) {
            // It is illegal for a global declaration to occur at the top level.
            // Technically, the parser will catch this error, but we still maintain
            // this check, in case of malformed AST tree
            this.errors.semError(id, TypeErrorMessages.NOT_GLOBAL_VAR, name);
            return null;
        } else if (!this.globals.declares(name)) {
            // the variable must be defined in global scope
            this.errors.semError(id, TypeErrorMessages.NOT_GLOBAL_VAR, name);
            return null;
        } else if (!this.globals.get(name).isVarDef()) {
            // global declaration can only refer to existing, VARIABLE
            // not functions, or classes
            this.errors.semError(id, TypeErrorMessages.NOT_GLOBAL_VAR, name);
            return null;
        }

        return this.globals.get(name);
    }

    @Override
    public DeclarationNode analyze(NonLocalDecl nonLocalDecl) {
        Identifier id = nonLocalDecl.getIdentifier();
        String name = id.name;

        // a valid nonlocal variable refers to:

        // - a variable definition in upper scope
        // - that exists
        // - and is ORIGINALLY defined in a nested function scope
        //   (so not accessed by another global keyword -- nonlocal is fine)

        if (this.sym.get(name) == null ||
            !this.sym.get(name).isVarDef() ||
            this.globals.equals(this.sym.get(name).sym.getParent())) {
            this.errors.semError(id, TypeErrorMessages.NOT_NONLOCAL_VAR, name);
        }

        return this.sym.get(name);
    }

    /**
     * Analyze a set of declarations using Analyzer's current symbol table SYM
     */
    private void analyzeDeclarations(List<Declaration> declarations) {
        // save the declarative regions, because some invalid declarations may
        // not be saved in the current scope's symbol table (e.g. duplicate name),
        // BUT we STILL need to analyze the declarations nested inside these declarations
        List<SymbolTable<DeclarationNode>> declarativeRegions = new ArrayList<>();

        // cache all declaration nodes
        List<DeclarationNode> declarationNodes = new ArrayList<>();

        for (Declaration decl : declarations) {
            Identifier id = decl.getIdentifier();
            String name = id.name;

            DeclarationNode declType = decl.dispatch(this);
            declarationNodes.add(declType);

            if (declType == null) {
                declarativeRegions.add(null);
                continue;
            }

            declarativeRegions.add(declType.sym);

            if (!this.sym.equals(this.globals) &&
               !declType.isClassDef() &&
               this.shadowsClassName(name)) {
                // variables and functions may not shadow class names

                // but we don't throw error on the global declarative region
                // since if a var/func is defined before the class, it is valid
                // and the class itself throws a duplicated declarations error
                // but if a var/func occurs after a class, the previous branch
                // will also throw duplication error
                this.errors.semError(id, TypeErrorMessages.CANNOT_SHADOW_CLASS_NAME, name);
            } else if (this.sym.declares(name)) {
                this.raiseDuplicateDeclarationError(id);
            } else {
                this.sym.put(name, declType);
            }

            // check for type annotation
            if (decl instanceof VarDef) {
                VarDef varDef = (VarDef) decl;
                this.checkTypeAnnotations(varDef.var.type);
            } else if (decl instanceof FuncDef) {
                FuncDef funcDef = (FuncDef) decl;
                for (TypedVar paramType : funcDef.params) {
                    this.checkTypeAnnotations(paramType.type);
                }
                this.checkTypeAnnotations(funcDef.returnType);
            }
        }

        // we recursively analyze function bodies after all declarations
        // of this declarative region have been processed
        for (int i = 0; i < declarations.size(); i++) {
            Declaration decl = declarations.get(i);

            // switch to declaration's declarative region
            SymbolTable<DeclarationNode> parentSym = this.sym;
            this.sym = declarativeRegions.get(i);

            if (this.sym == null) {
                this.sym = parentSym;
                continue;
            }

            if (decl instanceof FuncDef) {
                this.analyzeDeclarations(((FuncDef) decl).declarations);
            } else if (decl instanceof ClassDef) {
                ClassDefType classDefType =
                            (ClassDefType) declarationNodes.get(i).getType();
                String superclassName = classDefType.superclassName();
                this.analyzeClassDeclarations((ClassDef) decl, superclassName);
            }

            // switch back to parent declarative region
            this.sym = parentSym;

        }
    }

    /**
     * Analyze a set of declarations inside class definition
     */
    private void analyzeClassDeclarations(ClassDef classDef, String superclassName) {
        String className = classDef.getIdentifier().name;
        DeclarationNode superclassDecl = this.globals.get(superclassName);

        // get all inherited attributes and methods
        SymbolTable<DeclarationNode> inherited =
                ClassUtilities.getBaseClassDeclaration(
                        superclassDecl.classValueType(),
                        this.inheritanceTable,
                        this.getGlobals()
                );

        // save the declarative regions, because some invalid declarations may
        // not be saved in the current scope's symbol table, but we still need
        // to analyze the declarations nested inside these declarations
        List<SymbolTable<DeclarationNode>> declarativeRegions = new ArrayList<>();

        // analyze declarations of class, without analyzing nested declaration bodies
        for (Declaration decl : classDef.declarations) {
            Identifier declId = decl.getIdentifier();
            String declName = declId.name;

            // analyze declaration one level down AND
            // we should not have access to class attributes and methods, without using self
            SymbolTable<DeclarationNode> classSym = this.sym;
            this.sym = this.globals;
            DeclarationNode declType = decl.dispatch(this);
            this.sym = classSym;

            if (declType == null) {
                declarativeRegions.add(null);
                continue;
            }

            declarativeRegions.add(declType.sym);

            // if there's an semantic error in class method definition, it is
            // not saved in class's declarative region for inheritance

            boolean hasError = false;

            if (this.sym.declares(declName)) {
                // no duplicate methods declared in current subclass
                this.raiseDuplicateDeclarationError(declId);
                hasError = true;
            }

            // the following branch is commented out, because currently
            // there's a discrepancy that it's fine to have class methods
            // and attributes shadow a class name, as they can only be accessed
            // through member expressions
            //
            // if (!declType.isClassDef() && this.shadowsClassName(declName)) {
            //     // variables and functions may not shadow class names
            //     errors.semError(declId, TypeErrorMessages.CANNOT_SHADOW_CLASS_NAME, declName);
            //     continue;
            // }
            if (declType.isVarDef() && inherited.declares(declName)) {
                // attributes cannot override inherited attributes or methods
                this.errors.semError(declId, TypeErrorMessages.CANNOT_REDEFINE_ATTR, declName);
                hasError = true;
            } else if (declType.isFuncDef()) {
                // methods can only override methods of the same name, inherited from
                // base classes, as long as the signatures match
                if (inherited.declares(declName) && inherited.get(declName).isVarDef()) {
                    // methods cannot override inherited class attributes
                    this.errors.semError(declId, TypeErrorMessages.CANNOT_REDEFINE_ATTR, declName);
                    hasError = true;
                } else if (inherited.declares(declName) && inherited.get(declName).isFuncDef()) {
                    FuncType parentFuncType = (FuncType) inherited.get(declName).getType();
                    FuncType declFuncType = (FuncType) declType.getType();
                    // methods can override inherited class methods, only if their signatures match
                    if (!this.matchFunctionSignature(parentFuncType, declFuncType)) {
                        this.errors.semError(
                                declId,
                                TypeErrorMessages.METHOD_OVERRIDE_DIFFERENT_SIG,
                                declName
                        );
                        hasError = true;
                    }
                }

                // we check class method signature on post-semantic check methods parameters
                FuncType methodDef = (FuncType) declType.getType();
                if (!this.checkClassMethodSignature(methodDef, declId, className)) {
                    hasError = true;
                }
            }

            // if method is free of semantic errors, we then save it to the declarative region
            if (!hasError) {
                this.sym.put(declName, declType);
            }
        }

        // we recursively analyze function bodies after all valid declarations
        // of this declarative region have been processed and saved in the method definition
        for (int i = 0; i < classDef.declarations.size(); i++) {
            Declaration decl = classDef.declarations.get(i);
            String name = decl.getIdentifier().name;

            // switch to declaration's declarative region
            SymbolTable<DeclarationNode> parentSym = this.sym;
            this.sym = declarativeRegions.get(i);

            if (this.sym == null) {
                this.sym = parentSym;
                continue;
            }

            if (decl instanceof FuncDef) {
                this.analyzeDeclarations(((FuncDef) decl).declarations);
            }

            // check for type annotation
            if (decl instanceof VarDef) {
                VarDef varDef = (VarDef) decl;
                this.checkTypeAnnotations(varDef.var.type);
            } else if (decl instanceof FuncDef) {
                FuncDef funcDef = (FuncDef) decl;
                for (TypedVar paramType : funcDef.params) {
                    this.checkTypeAnnotations(paramType.type);
                }
                this.checkTypeAnnotations(funcDef.returnType);
            }

            // switch back to parent declarative region
            this.sym = parentSym;

        }

    }

    /**
     * Perform semantic checks on class methods, and return if it's a valid method signature:
     * - methods must specify at least one formal parameter,
     * - the first parameter must be of the same type as the enclosing class
     * - "__init__" methods must have exactly one formal parameter and a <None> return type
     */
    private boolean checkClassMethodSignature(FuncType classMethod, Identifier id, String className) {
        String methodName = id.name;

        if (classMethod.parameters.isEmpty()) {
            this.errors.semError(
                    id,
                    TypeErrorMessages.FIRST_PARAM_ENCLOSING_CLASS,
                    methodName);
            return false;
        }

        ValueType firstParamType = classMethod.parameters.get(0);

        if (!(firstParamType instanceof ClassValueType)) {
            this.errors.semError(
                    id,
                    TypeErrorMessages.FIRST_PARAM_ENCLOSING_CLASS,
                    methodName);
            return false;
        }

        ClassValueType firstParamClassValueType = (ClassValueType) firstParamType;

        if (!className.equals(firstParamClassValueType.className())) {
            this.errors.semError(
                    id,
                    TypeErrorMessages.FIRST_PARAM_ENCLOSING_CLASS,
                    methodName);
            return false;
        }

        if (methodName.equals("__init__")) {
            if (classMethod.parameters.size() != 1 || !classMethod.returnType.equals(NONE_TYPE)) {
                // TODO: we should throw semantic error if the return for __init__ is not <None>:
                // in form of "Expected type `<None>`; got type `???`", in type checking
                this.errors.semError(id, TypeErrorMessages.METHOD_OVERRIDE_DIFFERENT_SIG, methodName);
                return false;
            }
        }
        return true;
    }

    /**
     * Check if two function signatures match, with the exception of first parameter.
     */
    private boolean matchFunctionSignature(FuncType f1, FuncType f2) {
        if (!f1.returnType.equals(f2.returnType)) {
            return false;
        }
        List<ValueType> f1Params = f1.parameters;
        List<ValueType> f2Params = f2.parameters;
        if (f1Params.size() != f2Params.size()) {
            return false;
        }
        for (int i = 1; i < f1Params.size(); i++) {
            if (!f1Params.get(i).equals(f2Params.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Report duplicate declaration error
     */
    private void raiseDuplicateDeclarationError(Identifier id) {
        this.errors.semError(id, TypeErrorMessages.DUPLICATE_ID_DECL, id.name);
    }

    /**
     * Return true, if given NAME shadows a class name.
     */
    private boolean shadowsClassName(String name) {
        return this.classNames.contains(name);
    }


    /**
     * Return the classType inside the TypeAnnotation.
     */
    private ClassType getClassType(TypeAnnotation annotation) {
        if (annotation instanceof ClassType) {
            return (ClassType) annotation;
        } else {
            assert annotation instanceof ListType;
            return this.getClassType(((ListType) annotation).elementType);
        }
    }

    /**
     * Check if type ANNOTATION refers to a defined className.
     */
    private void checkTypeAnnotations(TypeAnnotation annotation) {
        ClassType classType = this.getClassType(annotation);
        String name = classType.className;
        if (!this.shadowsClassName(name)) {
            this.errors.semError(classType,
                    TypeErrorMessages.INVALID_ANNOTATION,
                    name);
        }
    }

    /** Quick utility function to create an empty declarative region
     *  symbol table, with parent being current declarative region. */
    private SymbolTable<DeclarationNode> getEmptyDeclRegion() {
        return new SymbolTable<DeclarationNode>(this.sym);
    }
}