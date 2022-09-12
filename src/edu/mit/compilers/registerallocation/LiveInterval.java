package edu.mit.compilers.registerallocation;

import edu.mit.compilers.codegen.InstructionList;
import edu.mit.compilers.codegen.names.LValue;
import edu.mit.compilers.codegen.names.VirtualRegister;

/**
 * @param variable        The variable this {@link LiveInterval} belongs to
 * @param startPoint      The start index of the {@link edu.mit.compilers.codegen.codes.Instruction} in the given {@link InstructionList}
 * @param endPoint        The end index of the instruction in the given {@link InstructionList}
 * @param instructionList the {@link InstructionList} this {@link LiveInterval} belongs to
 * @param methodName      the name of the {@link edu.mit.compilers.codegen.codes.Method} containing this {@link LiveInterval}
 *
 *                        <ul>
 *                           <li>The <strong>live range</strong> for a variable is the set of program points at which that variable is live.</p>
 *                        </ul>
 *                        <ul>
 *                           <li>
 *                              The <strong>live interval</strong> for a variable is the smallest subrange of the IR code containing all a variable's live ranges. </p>
 *                              <ul>
 *                                 <li>A property of the IR code, not CFG. </li>
 *                                 <li>Less precise than live ranges, but simpler to work with</li>
 *                              </ul>
 *                           </li>
 *                        </ul>
 *                        </li>
 */
public record LiveInterval(LValue variable, int startPoint, int endPoint, InstructionList instructionList,
                           String methodName) {
    public int compareStartPoint(LiveInterval other) {
        if (startPoint == other.startPoint)
            return 0;
        return startPoint < other.startPoint ? -1 : 1;
    }

    public int compareEndpoint(LiveInterval other) {
        if (endPoint == other.endPoint)
            return 0;
        return endPoint < other.endPoint ? -1 : 1;
    }
}
