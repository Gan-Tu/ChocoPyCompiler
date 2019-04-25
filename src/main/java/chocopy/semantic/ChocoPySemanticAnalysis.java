package chocopy.semantic;

import chocopy.common.analysis.SymbolTable;
import chocopy.common.astnodes.Program;


/**
 * Top-level class for performing semantic analysis.
 */
public class ChocoPySemanticAnalysis {

    /**
     * Perform semantic analysis on PROGRAM, adding error messages and
     * type annotations. Provide debugging output iff DEBUG. Returns modified
     * tree.
     */
    public static Program process(Program program, boolean debug) {
        if (program.hasErrors()) {
            return program;
        }

        InheritanceTable inheritanceTable = new InheritanceTable();

        DeclarationAnalyzer declarationAnalyzer =
                new DeclarationAnalyzer(program.errors, inheritanceTable);
        program.dispatch(declarationAnalyzer);

        SymbolTable<DeclarationNode> globalSym =
                declarationAnalyzer.getGlobals();

        if (!program.hasErrors()) {
            // Analyze semantics on statements
            StatementAnalyzer statementAnalyzer =
                    new StatementAnalyzer(program.errors, globalSym);
            program.dispatch(statementAnalyzer);

            // Type checking
            TypeChecker typeChecker =
                    new TypeChecker(globalSym, program.errors, inheritanceTable);
            program.dispatch(typeChecker);
        }

        return program;
    }
}
