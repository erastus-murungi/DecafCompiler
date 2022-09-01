package edu.mit.compilers;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import edu.mit.compilers.interpreter.Interpreter;
import edu.mit.compilers.utils.CLI;
import edu.mit.compilers.utils.Compilation;

class Main {
    public static void main(String[] args) throws FileNotFoundException {
        final String[] optnames = {"all", "cp", "cse", "dce", "regalloc"};
        CLI.parse(args, optnames);
        CLI.outfile = "test.s";
//        String sourceCode = "import printf; \n void main() {int x; x = (1 + 6 * 3); printf(\"%d\", x);}";
//        new Compilation(sourceCode).run();
          new Compilation(new FileInputStream("tests/misc/simple.dcf"), true).run();
//        new Compilation().run();
//        TestRunner.run();
    }
}
