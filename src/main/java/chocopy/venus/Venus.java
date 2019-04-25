package chocopy.venus;

import java.util.Arrays;
import java.util.List;
import venus.assembler.Assembler;
import venus.assembler.AssemblerError;
import venus.assembler.AssemblerOutput;
import venus.linker.LinkedProgram;
import venus.linker.Linker;
import venus.riscv.Program;
import venus.simulator.Simulator;

public class Venus {
    private static final String[] a = new String[]{"venus.glue.Renderer", "venus.assembler.PseudoWriter", "venus.assembler.DebugInfo", "venus.assembler.Lexer", "venus.assembler.LexerKt", "venus.assembler.AssemblerKt", "venus.assembler.AssemblerPassOne", "venus.assembler.AssemblerError", "venus.assembler.AssemblerPassTwo", "venus.assembler.DebugInstruction", "venus.assembler.PassOneOutput", "venus.assembler.pseudos.BGT", "venus.assembler.pseudos.BNEZ", "venus.assembler.pseudos.JR", "venus.assembler.pseudos.UtilsKt", "venus.assembler.pseudos.SGTZ", "venus.assembler.pseudos.BGEZ", "venus.assembler.pseudos.SGE", "venus.assembler.pseudos.NEG", "venus.assembler.pseudos.BLE", "venus.assembler.pseudos.JALR", "venus.assembler.pseudos.JAL", "venus.assembler.pseudos.J", "venus.assembler.pseudos.LI", "venus.assembler.pseudos.BGTU", "venus.assembler.pseudos.SLTZ", "venus.assembler.pseudos.BLEZ", "venus.assembler.pseudos.BEQZ", "venus.assembler.pseudos.SEQZ", "venus.assembler.pseudos.NOT", "venus.assembler.pseudos.TAIL", "venus.assembler.pseudos.SEQ", "venus.assembler.pseudos.LA", "venus.assembler.pseudos.BLTZ", "venus.assembler.pseudos.Store", "venus.assembler.pseudos.NOP", "venus.assembler.pseudos.RET", "venus.assembler.pseudos.SGT", "venus.assembler.pseudos.SLE", "venus.assembler.pseudos.BLEU", "venus.assembler.pseudos.CALL", "venus.assembler.pseudos.SNE", "venus.assembler.pseudos.BGTZ", "venus.assembler.pseudos.MV", "venus.assembler.pseudos.Load", "venus.assembler.pseudos.SNEZ", "venus.assembler.Assembler", "venus.assembler.AssemblerOutput", "venus.assembler.PseudoDispatcher", "venus.linker.Linker", "venus.linker.ProgramDebugInfo", "venus.linker.LinkedProgram", "venus.linker.RelocationInfo", "venus.simulator.Diff", "venus.simulator.SimulatorError", "venus.simulator.Simulator", "venus.simulator.SimulatorState", "venus.simulator.Memory", "venus.simulator.diffs.PCDiff", "venus.simulator.diffs.HeapSpaceDiff", "venus.simulator.diffs.RegisterDiff", "venus.simulator.diffs.MemoryDiff", "venus.simulator.History", "venus.riscv.UtilsKt", "venus.riscv.MachineCode", "venus.riscv.MemorySegments", "venus.riscv.Program", "venus.riscv.Settings", "venus.riscv.InstructionField", "venus.riscv.insts.DivKt", "venus.riscv.insts.AuipcKt", "venus.riscv.insts.SraKt", "venus.riscv.insts.AddKt", "venus.riscv.insts.XorKt", "venus.riscv.insts.SltiuKt", "venus.riscv.insts.AndKt", "venus.riscv.insts.AndiKt", "venus.riscv.insts.DivuKt", "venus.riscv.insts.SltKt", "venus.riscv.insts.LuiKt", "venus.riscv.insts.SubKt", "venus.riscv.insts.RemKt", "venus.riscv.insts.BgeKt", "venus.riscv.insts.JalrKt", "venus.riscv.insts.AddiKt", "venus.riscv.insts.EcallKt", "venus.riscv.insts.BgeuKt", "venus.riscv.insts.SrliKt", "venus.riscv.insts.ShKt", "venus.riscv.insts.MulKt", "venus.riscv.insts.XoriKt", "venus.riscv.insts.OriKt", "venus.riscv.insts.BltKt", "venus.riscv.insts.LhuKt", "venus.riscv.insts.MulhsuKt", "venus.riscv.insts.BltuKt", "venus.riscv.insts.SlliKt", "venus.riscv.insts.OrKt", "venus.riscv.insts.LbuKt", "venus.riscv.insts.RemuKt", "venus.riscv.insts.LhKt", "venus.riscv.insts.SbKt", "venus.riscv.insts.BeqKt", "venus.riscv.insts.SwKt", "venus.riscv.insts.JalKt", "venus.riscv.insts.MulhuKt", "venus.riscv.insts.MulhKt", "venus.riscv.insts.SrlKt", "venus.riscv.insts.BneKt", "venus.riscv.insts.SltiKt", "venus.riscv.insts.dsl.disasms.InstructionDisassembler", "venus.riscv.insts.dsl.disasms.LoadDisassembler", "venus.riscv.insts.dsl.disasms.RawDisassembler", "venus.riscv.insts.dsl.disasms.ShiftImmediateDisassembler", "venus.riscv.insts.dsl.disasms.RTypeDisassembler", "venus.riscv.insts.dsl.disasms.ITypeDisassembler", "venus.riscv.insts.dsl.disasms.UTypeDisassembler", "venus.riscv.insts.dsl.disasms.STypeDisassembler", "venus.riscv.insts.dsl.disasms.BTypeDisassembler", "venus.riscv.insts.dsl.UtilsKt", "venus.riscv.insts.dsl.STypeInstruction", "venus.riscv.insts.dsl.parsers.DoNothingParser", "venus.riscv.insts.dsl.parsers.InstructionParser", "venus.riscv.insts.dsl.parsers.UtilsKt", "venus.riscv.insts.dsl.parsers.LoadParser", "venus.riscv.insts.dsl.parsers.ParserError", "venus.riscv.insts.dsl.parsers.BTypeParser", "venus.riscv.insts.dsl.parsers.RTypeParser", "venus.riscv.insts.dsl.parsers.UTypeParser", "venus.riscv.insts.dsl.parsers.RawParser", "venus.riscv.insts.dsl.parsers.STypeParser", "venus.riscv.insts.dsl.parsers.ShiftImmediateParser", "venus.riscv.insts.dsl.parsers.ITypeParser", "venus.riscv.insts.dsl.BTypeInstruction", "venus.riscv.insts.dsl.formats.STypeFormat", "venus.riscv.insts.dsl.formats.ITypeFormat", "venus.riscv.insts.dsl.formats.RTypeFormat", "venus.riscv.insts.dsl.formats.FieldEqual", "venus.riscv.insts.dsl.formats.UTypeFormat", "venus.riscv.insts.dsl.formats.OpcodeFormat", "venus.riscv.insts.dsl.formats.OpcodeFunct3Format", "venus.riscv.insts.dsl.formats.BTypeFormat", "venus.riscv.insts.dsl.formats.InstructionFormat", "venus.riscv.insts.dsl.ShiftImmediateInstruction", "venus.riscv.insts.dsl.RTypeInstruction", "venus.riscv.insts.dsl.impls.UtilsKt", "venus.riscv.insts.dsl.impls.BTypeImplementation32", "venus.riscv.insts.dsl.impls.BTypeImplementation32Kt", "venus.riscv.insts.dsl.impls.InstructionImplementation", "venus.riscv.insts.dsl.impls.STypeImplementation32", "venus.riscv.insts.dsl.impls.RawImplementation", "venus.riscv.insts.dsl.impls.LoadImplementation32", "venus.riscv.insts.dsl.impls.RTypeImplementation32", "venus.riscv.insts.dsl.impls.ShiftImmediateImplementation32", "venus.riscv.insts.dsl.impls.ITypeImplementation32", "venus.riscv.insts.dsl.impls.STypeImplementation32Kt", "venus.riscv.insts.dsl.impls.NoImplementation", "venus.riscv.insts.dsl.ITypeInstruction", "venus.riscv.insts.dsl.Instruction", "venus.riscv.insts.dsl.LoadTypeInstruction", "venus.riscv.insts.dsl.relocators.JALRelocator32", "venus.riscv.insts.dsl.relocators.Relocator64", "venus.riscv.insts.dsl.relocators.Relocator", "venus.riscv.insts.dsl.relocators.PCRelLoStoreRelocator32", "venus.riscv.insts.dsl.relocators.PCRelHiRelocatorKt", "venus.riscv.insts.dsl.relocators.JALRelocatorKt", "venus.riscv.insts.dsl.relocators.PCRelHiRelocator32", "venus.riscv.insts.dsl.relocators.PCRelLoStoreRelocatorKt", "venus.riscv.insts.dsl.relocators.NoRelocator64", "venus.riscv.insts.dsl.relocators.PCRelLoRelocatorKt", "venus.riscv.insts.dsl.relocators.Relocator32", "venus.riscv.insts.dsl.relocators.PCRelLoRelocator32", "venus.riscv.insts.dsl.UTypeInstruction", "venus.riscv.insts.SllKt", "venus.riscv.insts.SraiKt", "venus.riscv.insts.LbKt", "venus.riscv.insts.SltuKt", "venus.riscv.insts.LwKt"};

    public static Simulator assembleAndLink(String asm) {
        try {
            Venus.init();
        }
        catch (ClassNotFoundException e2) {
            throw new IllegalStateException(e2);
        }
        AssemblerOutput out = Assembler.INSTANCE.assemble(asm);
        if (out.getErrors().size() > 0) {
            AssemblerError e3 = (AssemblerError)out.getErrors().get(0);
            throw new IllegalArgumentException((Throwable)e3);
        }
        LinkedProgram linkedProgram = Linker.INSTANCE.link(Arrays.asList(new Program[]{out.getProg()}));
        Simulator sim = new Simulator(linkedProgram);
        return sim;
    }

    public static void assembleLinkAndRun(String asm) {
        Venus.assembleAndLink(asm).run();
    }

    public static int assembleLinkAndRunWithCounter(String asm, Integer maxCycles) {
        int cycles;
        Simulator sim = Venus.assembleAndLink(asm);
        for (cycles = 0; !(sim.isDone() || maxCycles != null && cycles >= maxCycles); ++cycles) {
            sim.step();
        }
        return cycles;
    }

    public static void init() throws ClassNotFoundException {
        for (String className : a) {
            Class.forName(className);
        }
    }
}