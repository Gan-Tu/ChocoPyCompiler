package chocopy.semantic;

import chocopy.common.analysis.AbstractNodeAnalyzer;
import chocopy.common.analysis.SymbolTable;
import chocopy.common.analysis.types.ValueType;
import chocopy.common.astnodes.AssignStmt;
import chocopy.common.astnodes.ClassDef;
import chocopy.common.astnodes.Declaration;
import chocopy.common.astnodes.Errors;
import chocopy.common.astnodes.Expr;
import chocopy.common.astnodes.ForStmt;
import chocopy.common.astnodes.FuncDef;
import chocopy.common.astnodes.Identifier;
import chocopy.common.astnodes.IfStmt;
import chocopy.common.astnodes.Program;
import chocopy.common.astnodes.ReturnStmt;
import chocopy.common.astnodes.Stmt;
import chocopy.common.astnodes.WhileStmt;
import chocopy.semantic.errors.TypeErrorMessages;

import java.util.List;

/**
 * The StatementAnalyzer performs semantic analysis tasks that come
 * after declaration analysis and perform analysis on statements.
 * <p>
 * In particular, it handles:
 * 1. No top-level return statements
 * 2. No assignment statements to implicitly inherited variables
 * 3. Functions or methods that return special types must have an
 * explicit return statement along all paths.
 * <p>
 * The `analyze` method returns True if there's an explicit return
 * among ALL paths in the analyzed Node.
 */
public class StatementAnalyzer extends AbstractNodeAnalyzer<Boolean> {

    /**
     * Global symbol table.
     */
    private SymbolTable<DeclarationNode> sym;
    /**
     * Receiver for semantic error messages.
     */
    private final Errors errors;

    public StatementAnalyzer(Errors errors0, SymbolTable<DeclarationNode> table) {
        this.errors = errors0;
        this.sym = table;
        this.setDefault(false);
    }

    @Override
    public Boolean analyze(Program program) {
        // we only analyze program declarations
        this.analyzeDeclarations(program.declarations);
        for (Stmt statement : program.statements) {
            statement.dispatch(this);
        }
        return false;
    }

    @Override
    public Boolean analyze(ClassDef classDef) {
        this.analyzeDeclarations(classDef.declarations);
        return false;
    }

    @Override
    public Boolean analyze(FuncDef funcDef) {
        this.analyzeDeclarations(funcDef.declarations);

        ValueType returnType =
                ValueType.annotationToValueType(funcDef.returnType);

        boolean hasExplicitReturn = false;
        for (Stmt s : funcDef.statements) {
            // Note: analyze on `IfStmt` only returns True if its ALL
            // paths have explicit returns. So, as long as one IfStmt
            // has explicit return along all of its path, we are good,
            // as the following statements will simply be unreachable

            // Note the order of execution, to ensure no short-circuiting
            // exists to not evaluate recursive dispatch on `s`
            hasExplicitReturn = s.dispatch(this) || hasExplicitReturn;
        }

        if (returnType.isSpecialType() && !hasExplicitReturn) {
            Identifier id = funcDef.getIdentifier();
            this.errors.semError(
                    id, TypeErrorMessages.ALL_PATHS_MUST_RETURN, id.name
            );
        }

        // declarations don't count towards return statements of parent
        return false;
    }

    @Override
    public Boolean analyze(ForStmt stmt) {
        Identifier id = stmt.identifier;

        // control variable for for loop cannot be implicitly inherited
        if (this.isImplicitlyInherited(id)) {
            this.errors.semError(
                    id, TypeErrorMessages.NO_IMPLICIT_ASSIGNMENT, id.name
            );
        }

        for (Stmt s : stmt.body) {
            s.dispatch(this);
        }

        // a for statement may be not evaluated
        // so no explicit return among all paths
        return false;
    }

    @Override
    public Boolean analyze(WhileStmt stmt) {
        for (Stmt s : stmt.body) {
            s.dispatch(this);
        }

        // a while statement may be not evaluated
        // so no explicit return among all paths
        return false;
    }

    @Override
    public Boolean analyze(AssignStmt stmt) {
        for (Expr target : stmt.targets) {
            if (target instanceof Identifier) {
                Identifier id = (Identifier) target;
                if (this.isImplicitlyInherited(id)) {
                    this.errors.semError(
                            id, TypeErrorMessages.NO_IMPLICIT_ASSIGNMENT, id.name
                    );
                }
            }
        }
        return false;
    }

    @Override
    public Boolean analyze(IfStmt stmt) {
        boolean thenHasExplicitReturn = false;
        boolean bodyHasExplicitReturn = false;

        for (Stmt s : stmt.thenBody) {
            // Note the order of execution, to ensure no short-circuiting
            // exists to not evaluate recursive dispatch on `s`
            thenHasExplicitReturn = s.dispatch(this) || thenHasExplicitReturn;
        }

        for (Stmt s : stmt.elseBody) {
            // Note the order of execution, to ensure no short-circuiting
            // exists to not evaluate recursive dispatch on `s`
            bodyHasExplicitReturn = s.dispatch(this) || bodyHasExplicitReturn;
        }

        return thenHasExplicitReturn && bodyHasExplicitReturn;
    }

    @Override
    public Boolean analyze(ReturnStmt stmt) {
        if (this.sym.getParent() == null) {
            this.errors.semError(stmt, TypeErrorMessages.NO_RETURN_TOP_LEVEL);
        }
        return true;
    }


    /**
     * Return true if identifier ID is implicitly inherited.
     */
    private boolean isImplicitlyInherited(Identifier id) {
        return !this.sym.declares(id.name) && // not in current scope
                this.sym.get(id.name) != null && // but exists in upper scope
                this.sym.get(id.name).isVarDef(); // is a variable
    }

    /**
     * Helper function to recursively analyze the statements inside
     * a list of DECLARATIONS.
     */
    private void analyzeDeclarations(List<Declaration> declarations) {
        for (Declaration decl : declarations) {
            String name = decl.getIdentifier().name;
            if (this.sym.declares(name)) {
                SymbolTable<DeclarationNode> prevSym = this.sym;
                this.sym = this.sym.get(name).sym;

                decl.dispatch(this);

                this.sym = prevSym;
            }
        }
    }
}
