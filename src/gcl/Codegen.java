package gcl;

import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

// --------------------- Codegen ---------------------------------
public class Codegen implements Mnemonic, CodegenConstants {
	
	private PrintWriter codefile;
	private int labelIndex = 0; // Last label issued.
	private int variableIndex = 0; // Address offset of next global allocated.
	private ConstantStorage constants = null;
	private SemanticActions.GCLErrorStream err = null; // SemanticActions.err;
	private SemanticActions.SemanticLevel currentLevel;
	private RegisterSet registers = new RegisterSet();
	private Stack<RegisterSet> savedRegisters = new Stack<RegisterSet>();
	// Honors Code
	private PrintWriter objfile;
	private Collection<Instruction> instructionList = new ArrayList<Instruction>(); // Collection of instructions
	private Hashtable<String, Integer> definedLabels = new Hashtable<String, Integer>(); // Label definitions String name -> int instructionIndex
	private Set<String> undefinedLabels = new HashSet<String>(); // Labels referenced but not yet defined.

	Codegen(final SemanticActions.GCLErrorStream err) {
		this.err = err;
		init();
	}

	void setSemanticLevel(final SemanticActions.SemanticLevel currentLevel) {
		this.currentLevel = currentLevel; // gives buildOperands access to the currentLevel.
	}

	public void closeCodefile() {
		codefile.close();
	}
	
	public void closeObjfile() {
		objfile.close();
	}

	/**
	 * Reserve an address for a global variable
	 * 
	 * @param size the number of bytes to reserve
	 * @return the offset at which it will be stored
	 */
	public int reserveGlobalAddress(final int size) {
		int result = variableIndex;
		variableIndex += size;
		return result;
	}

	/**
	 * Get a fresh unused label
	 * 
	 * @return the number of the label.
	 */
	public int getLabel() {
		return ++labelIndex;
	}

	/**
	 * How many bytes are allocated to global variables?
	 * 
	 * @return the size of the block
	 */
	public int variableBlockSize() {
		return variableIndex;
	}

	/**
	 * Get an unused register or register pair -- must later be freed
	 * 
	 * @param size number of registers to obtain (1 or 2)
	 * @return the register number
	 */
	public int getTemp(final int size){ // size = number of registers needed, 1 or  2.
		return registers.getTemp(size);
	}

	/**
	 * Free a register previously allocated. Only DREG and IREG operands are
	 * freed.
	 * 
	 * @param mode the addressing mode used with this register
	 * @param base the register to (possibly) free
	 */
	public void freeTemp(final Mode mode, final int base) {
		registers.freeTemp(mode, base);
	}

	/**
	 * Free the register held by a location object
	 * 
	 * @param l the location holding the register
	 */
	public void freeTemp(final Location l) {
		registers.freeTemp(l.mode, l.base);
	}

	public void printAllocatedRegisters() {
		registers.printAllocated();
	}

	/**
	 * Tag interface to indicate objects have a representation in the Macc
	 * runtime Used for Expressions and GCLStrings.
	 * 
	 */
	interface MaccSaveable {
	}

	/**
	 * Tag interface to indicate the object behaves like a constant and has a
	 * representation in the C1 block in the Macc runtime. Used for
	 * ConstantExpressions and GCLStrings.
	 * 
	 */
	interface ConstantLike extends MaccSaveable {
	}

	/**
	 * Compute a location object that will point to an expression. This is the
	 * major means of addressing SAM entities.
	 * 
	 * @param semanticItem the expression whose addressability is needed
	 * @param currentlevel the scoping level in which to compute the address
	 * @return a Location object with appropriate mode, base, and displacement
	 */
	public Location buildOperands(final MaccSaveable semanticItem) {
		if (semanticItem == null || semanticItem instanceof GeneralError){
			return new Location(DREG, 0, 0);
		}
		if (semanticItem instanceof ConstantLike){
			return constants.insertConstant((ConstantLike) semanticItem);
		}
		Mode mode = DREG;
		int base = -1, displacement = 0;
		VariableExpression variable = (VariableExpression) semanticItem;
			// ^Safe cast, other instances of Expressions factored out above
		int itsLevel = variable.semanticLevel();
		boolean isDirect = variable.isDirect();
		if (itsLevel == CPU_LEVEL) {
			mode = isDirect ? DREG : IREG;
			base = variable.offset();
			displacement = UNUSED;
		} else if (itsLevel == GLOBAL_LEVEL) {
			mode = isDirect ? INDXD : IINDXD;
			base = VARIABLE_BASE;
			displacement = variable.offset();
		} else if (itsLevel == STACK_LEVEL) { // This may be wrong. JB.
			mode = IREG;
			base = variable.offset();
			displacement = UNUSED;
		} else { // its level > 1;
			int currentlevel = currentLevel.value();
			// more later for function/procedure blocks
		}
		return new Location(mode, base, displacement);
	}
	
	/**
	 * Used to extract a component stored in a tuple.
	 *
	 * @param tupleExpression A tuple which contains the target component.
	 * @param fieldName Identifier of the target member.
	 * @return tupleExpression@fieldName
	 */
	public Expression extractTupleComponent(VariableExpression tupleExpression, Identifier fieldName){
		
		TupleType tupleType = tupleExpression.type().expectTupleType(err);
		TypeDescriptor fieldType;
		int fieldInset;
		try{
			fieldType = tupleType.getType(fieldName);
			fieldInset = tupleType.getInset(fieldName);
		}
		catch(NoSuchElementException e){
			err.semanticError(GCLError.NAME_NOT_DEFINED);
			return new ErrorExpression("$ Invalid tuple member identifier.");
		}
		catch(NullPointerException e){
			err.semanticError(GCLError.NAME_NOT_DEFINED);
			return new ErrorExpression("$ Invalid tuple member identifier.");
		}
		
		if(tupleExpression.case1()){
			return new VariableExpression(fieldType, tupleExpression.semanticLevel(), tupleExpression.offset() + fieldInset, DIRECT);
		}
		if(tupleExpression.case2()){
			int tupleAddressOffset = loadAddress(tupleExpression);
			if(fieldInset != 0){ 
				gen2Address(IA, tupleAddressOffset, "#" + fieldInset);
			}
			return new VariableExpression(fieldType, tupleExpression.semanticLevel(), tupleAddressOffset, INDIRECT);
		}
		if(tupleExpression.case3()){
			int tupleAddressOffset = loadPointer(tupleExpression);
			if(fieldInset != 0){ 
				gen2Address(IA, tupleAddressOffset, "#" + fieldInset);
			}
			return new VariableExpression(fieldType, 0, tupleAddressOffset, INDIRECT);
		}
		err.semanticError(GCLError.ILLEGAL_TUPLE_ACCESS);
		return new ErrorExpression("$ Unable to extract tuple component.");
	}

	/**
	 * Load a value into a register (or say which register if already loaded).
	 * 
	 * @param expression  the expression to be loaded
	 * @param currentlevel the scoping level from which to compute the address
	 * @return the register that was used in the load
	 */
	public int loadRegister(final Expression expression) {
		if (expression == null) {
			err.semanticError(GCLError.ILLEGAL_LOAD);
			return 0;
		}
		int size = expression.type().size() / 2; // number of registers
													// needed
		if (size > 2) {
			err.semanticError(GCLError.ILLEGAL_LOAD_SIZE);
			return -1;
		}
		Location loc = buildOperands(expression);
		int reg;
		if (loc.mode == DREG){
			reg = loc.base;
		}
		else if (loc.mode == IREG) {
			reg = getTemp(size);
			gen2Address(LD, reg, loc);
			if (size == 2){
				gen2Address(LD, reg + 1, INDXD, loc.base, 2);
			}
			freeTemp(loc);
		} else {
			reg = getTemp(size);
			gen2Address(LD, reg, loc);
			if (size == 2){
				gen2Address(LD, reg + 1, INDXD, loc.base, loc.displacement + 2);
			}
		}
		return reg;
	}

	/**
	 * Load a value's address into a register, or say which if already loaded.
	 * 
	 * @param semanticItem the expression whose address is to be loaded
	 * @param currentlevel the scoping level from which to compute the address
	 * @return the register that was used in the load
	 */
	public int loadAddress(final Expression expression) {
		if (expression == null) {
			err.semanticError(GCLError.ILLEGAL_LOAD);
			return 0;
		}
		if (!(expression instanceof VariableExpression)) {
			err.semanticError(GCLError.ILLEGAL_LOAD_ADDRESS);
			return -1;
		}
		Location loc = buildOperands(expression);
		int reg;
		VariableExpression copy = (VariableExpression) expression;
		if (loc.mode == IREG) {
			reg = loc.base;
		} else if (!copy.isDirect() && copy.semanticLevel() == 0)
			reg = loc.base;
		else {
			reg = getTemp(1);
			gen2Address(LDA, reg, loc);
		}
		return reg;
	}

	/**
	 * Load an already existing pointer into a register if it isn't already in a
	 * register. It is an error if the expression doesn't represent a pointer:
	 * i.e. "indirect".
	 * 
	 * @param expression the pointer expression to be loaded
	 * @param currentLevel the the scoping level from which to compute the address
	 * @return the register that holds a copy of the pointer
	 */
	public int loadPointer(final Expression expression) {
		if (expression == null) {
			err.semanticError(GCLError.ILLEGAL_LOAD);
			return 0;
		}
		Location loc = buildOperands(expression);
		int reg = 0;
		if (loc.mode == IREG) {
			reg = loc.base; // already loaded
		} else if (loc.mode == IMEM) {
			reg = getTemp(1);
			gen2Address(LD, reg, DMEM, loc.base, loc.displacement);
			freeTemp(loc);
		} else if (loc.mode == IINDXD) {
			reg = getTemp(1);
			gen2Address(LD, reg, INDXD, loc.base, loc.displacement);
			freeTemp(loc);
		} else {
			err.semanticError(GCLError.NOT_A_POINTER);
		}
		return reg;
	}

	/**
	 * Guarantee all code has been written before we quit or at another key point
	 */
	public void flushcode() {/* Nothing in this version */
	}
	
	/**
	 * Replaces labels with offsets and writes the codefile and objfile.
	 */
	public void writeInstructionList(){
		defineLabelOffsets();
		for(Instruction instruction : instructionList){
			codefile.println(instruction.samCode());
			//objfile.println(fromBitSet(instruction.maccCode())); TODO remove this comment when testing obj output.
		}
	}

	/**
	 * Finds the memory offsets of labels
	 */
	private void defineLabelOffsets(){
		Integer instructionOffset = 0;
		Integer offset = null;
		// first pass: record label offsets.
		for(Instruction instruction : instructionList){
			if(instruction instanceof Label){
				Label labelInstruction = (Label)instruction;
				undefinedLabels.remove(labelInstruction.name());
				definedLabels.put(labelInstruction.name(), instructionOffset);
			}
			instructionOffset += instruction.maccSize();
		}
		// second pass: define jump offsets.
		for(Instruction instruction : instructionList){ // TODO can be implemented as part of the write loop in writeInstructionList.
			if(instruction instanceof Jump){
				Jump jumpInstruction = (Jump)instruction;
				offset = definedLabels.get(jumpInstruction.label());
				if(offset == null){
					err.semanticError(GCLError.LABEL_UNDEFINED, jumpInstruction.label());
				}
				else{
					jumpInstruction.defineOffset(offset);
				}
			}
		}
	}
	
	/**
	 * This adds the instruction to the list and optionally to the listing.
	 * @param instruction sam/macc instruction.
	 */
	private void writeFiles(final Instruction instruction) {
		instructionList.add(instruction);
		CompilerOptions.listCode("$   " + instruction.samCode());
	}

	/**
	 * Generate a 0 address instruction such as HALT
	 * 
	 * @param opcode the operation code as defined in Mnemonic
	 */
	public void gen0Address(final SamOp opcode) {
		writeFiles(new ZeroAddress(opcode));
	}

	/**
	 * Generate a 1 address instruction such as WRI
	 * 
	 * @param opcode the operation code as defined in Mnemonic
	 * @param mode a valid addressing mode for the operand of the instruction
	 * @param base a register for the operand
	 * @param displacement an offset for the operand
	 */
	public void gen1Address(final SamOp opcode, final Mode mode, final int base, final int displacement) {
		writeFiles(new OneAddress(opcode, mode, base, displacement));
	}

	/**
	 * Generate a 1 address instruction such as WRI
	 * 
	 * @param opcode the operation code as defined in Mnemonic
	 * @param l a location giving the operand of the instruction
	 */
	public void gen1Address(final SamOp opcode, final Location l) {
		gen1Address(opcode, l.mode, l.base, l.displacement);
	}

	/**
	 * Generate a 2 address instruction such as IA. These are also used
	 * for INC and DEC, though reg is an amount to INC/DEC and not
	 * a register.
	 * 
	 * @param opcode the operation code as defined in Mnemonic
	 * @param reg the register representing the first operand
	 * @param mode a valid addressing mode for the second operand of the instruction
	 * @param base a register for the second operand
	 * @param displacement an offset for the second operand
	 */
	public void gen2Address(final SamOp opcode, final int reg, final Mode mode, final int base, int displacement) {
		writeFiles(new TwoAddress(opcode, reg, mode, base, displacement));
	}

	/**
	 * Generate a 2 address instruction such as IA
	 * 
	 * @param opcode the operation code as defined in Mnemonic
	 * @param reg the register representing the first operand
	 * @param l  a location giving the second operand of the instruction
	 */
	public void gen2Address(final SamOp opcode, final int reg, final Location l) {
		gen2Address(opcode, reg, l.mode, l.base, l.displacement);
	}

	/**
	 * Generate a two address instruction with a DMEM (label) for the second
	 * operand.
	 * 
	 * @param opcode the operation code as defined in Mnemnonic
	 * @param reg the register representing the first operand
	 * @param dmemLocation a string representing the second operand's label
	 */
	public void gen2Address(final SamOp opcode, final int reg, final String dmemLocation) {
		writeFiles(new TwoAddress(opcode, reg, dmemLocation));
	}
	
	/**
	 * Generate a shift instruction. These are also used for PUSH and POP
	 * 
	 * @param opcode the operation code as defined in Mnemnonic
	 * @param reg the register representing the first operand
	 * @param amount an integer in the range 1..16
	 */
	public void genShiftInstruction(final SamOp opcode, final int reg, final int amount){
		writeFiles(new Directive(opcode, "R" + reg + ", " + amount));
	}
	
	/** Set a bit of a word corresponding to a register number.
	 * @param reg the register to transform
	 * @return an integer with one bit set
	 */
	private int regToBits(final int reg){
		return (int)Math.pow(2, reg);
	}

	/** Push the contents of a register onto the runtime stack.
	 * @param reg the register to push
	 */
	public void genPushRegister(final int reg){
		genShiftInstruction(PUSH, STACK_POINTER, regToBits(reg));
	}
	
	/** Pop the top of the runtime stack into a register.
	 * @param reg the register to receive the top of stack
	 */
	public void genPopRegister(final int reg){
		genShiftInstruction(POP, STACK_POINTER, regToBits(reg));
	}

	/**
	 * Generate a jump to a label
	 * 
	 * @param opcode the jump instruction
	 * @param prefix a one character prefix character
	 * @param offset  the integer value of the label
	 */
	public void genJumpLabel(final SamOp opcode, final char prefix, final int offset) {
		String label = prefix + String.valueOf(offset);
		if(definedLabels.get(label) == null){
			undefinedLabels.add(label);
		}
		writeFiles(new Jump(opcode, label));
	}

	/**
	 * Generate a label
	 * 
	 * @param prefix the one character prefix
	 * @param offset the integer value of the label
	 */
	public void genLabel(final char prefix, final int offset) {
		String label = prefix + String.valueOf(offset);
		writeFiles(new Label(LABEL_DIRECTIVE, label));
	}

	/**
	 * Generate a SAM pseudo op INT
	 * 
	 * @param value the value of the integer
	 */
	public void genIntDirective(final int value) {
		writeFiles(new Directive(INT_DIRECTIVE, String.valueOf(value)));
	}
	
	public void genStringDirective(final String value) {
		writeFiles(new Directive(STRING_DIRECTIVE, value));
	}

	/**
	 * Generate a SAM pseudo op SKIP
	 * 
	 * @param number  of bytes to skip
	 */
	public void genSkipDirective(final int value) {
		writeFiles(new Skip(SKIP_DIRECTIVE, value));
	}

	/** Generate the startup allocation code */
	public void genCodePreamble() {
		gen2Address(LDA, VARIABLE_BASE, "V1");
		gen2Address(LDA, CONSTANT_BASE, "C1");
		gen2Address(LD, STACK_POINTER, IMMED, 0, 16000);
	}

	/** Generate the end code, including the C1 and V1 blocks */
	public void genCodePostamble() {
		gen0Address(HALT);
		genLabel('C', 1);
		constants.genConstBlock();
		genLabel('V', 1);
		genSkipDirective(variableBlockSize());
		flushcode();
	}

	/**
	 * Comment the code arbitrarily -- usually for debugging
	 * 
	 * @param comment the comment
	 */
	public void genCodeComment(final String comment) {
		writeFiles(new Comment(comment));
	}

	/** Save the current register set and begin with a fresh one.
	 */
	public void saveRegisterInformation(){
		savedRegisters.push(registers);
		registers = new RegisterSet();
	}

	/** Discard the current register set and return to the previous one
	 */
	private void restoreRegisterInformation(){
		registers = savedRegisters.pop();		
	}
	
	/** Restore the dirty registers from the current register set from the runtime stack. 
	 * It must have been previously pushed.
	 * @param locals the additional size of the stack to remove - usually the local data size of a proc
	 * @return the number of dirty registers in the current set
	 */
	public int popRegisters(final int locals){
		return registers.restore(locals);
	}
	
	/** Save the dirty registers of the current register set to the runtime stack.
	 * @param locals the additional size to reserve under the save area. Usually the local data size of a proc.
	 * @param numberUsed the number of dirty registers in the current set
	 */
	public void pushRegisters(final int locals, final int numberUsed){
		registers.save(locals, numberUsed);
		restoreRegisterInformation();
	}
	
	/** Manages the registers in a way that they can be saved as a set. Saving the registers as a set is
	 * useful in procedure call optimization.
	 */
	private class RegisterSet{
		private boolean[] freeRegisters = null;
		private boolean[] pair = null;
		private boolean[] wasUsed = null; //True if a register was ever used in this set. Dirty registers.
		
		public RegisterSet(){
			freeRegisters = new boolean[MAX_REG + 1];
			pair = new boolean[MAX_REG + 1];
			wasUsed = new boolean[MAX_REG + 1];
			for (int i = 0; i < MAX_REG; ++i) {
				freeRegisters[i] = true;
				pair[i] = false;
				wasUsed[i] = false;
			}
			freeRegisters[STACK_POINTER] = false; // more later
			freeRegisters[CONSTANT_BASE] = false;
			freeRegisters[VARIABLE_BASE] = false;					
		}
	
		
		/** Allocate one or two (adjacent) registers from this set
		 * @param size the number of registers (1 or 2)
		 * @return the register number (of the first register if two)
		 */
		public int getTemp(final int size){ // size = number of registers needed, 1 or 2.
			int result = 0;
			try {
				if (size == 1) {
					while (!freeRegisters[result]){
						result++;
					}
					freeRegisters[result] = false;
					wasUsed[result] = true;
				} else {
					while (!freeRegisters[result] || !freeRegisters[result + 1]){
						result++;
					}
					freeRegisters[result] = false;
					freeRegisters[result + 1] = false;
					wasUsed[result] = true;
					wasUsed[result + 1] = true;
					pair[result] = true;
				}
			} catch (RuntimeException e) {
				err.semanticError(GCLError.NO_REGISTER_AVAILABLE);
			}
			return result <= MAX_REG ? result : 0;
		}
		
		/** conditionally free a register
		 * @param mode the mode that was used with this register
		 * @param base the register to attempt to free
		 */
		public void freeTemp(final Mode mode, final int base) {
			if (mode == DREG || mode == IREG || ((mode == INDXD ||mode == IINDXD) && base <= LAST_GENERAL_REGISTER)) {
				if(freeRegisters[base]){
					return; // nothing to do
				}
				freeRegisters[base] = true;
				if (pair[base]) {
					freeRegisters[base + 1] = true;
					pair[base] = false;
				}
			}
		}

		/** Save the "dirty" registers in the set by pusing them onto the run time stack
		 * @param locals the additional space to allocate under the save area
		 * @param numberUsed the number of dirty registers in the set
		 */
		public void save(final int locals, final int numberUsed){
			int size = INT_SIZE * numberUsed + locals;
			if(size > 0){
				gen2Address(IS, STACK_POINTER, IMMED, UNUSED, size);
			}
			if(numberUsed > 0){
				int where = 0;
				for(int i = 0; i <= LAST_GENERAL_REGISTER; ++i){
					if(wasUsed[i]){
						gen2Address(STO, i, INDXD, STACK_POINTER, where);
						where += 2;
					}
				}
			}
		}
		
		/** Restore the registers previously saved
		 * @param locals the additional space allocated during "save"
		 */
		public int restore(final int locals){
			int numberUsed = 0;
			for(int i = 0; i <= LAST_GENERAL_REGISTER; ++i){
				if(wasUsed[i]){
					numberUsed++;
				}
			}
			if(numberUsed > 0){
				int where = 0;
				for(int i = 0; i <= LAST_GENERAL_REGISTER; ++i){
					if(wasUsed[i]){
						gen2Address(LD, i, INDXD, STACK_POINTER, where);
						where += 2;
					}
				}
			}
			int size = INT_SIZE * numberUsed + locals;
			if(size > 0){
				gen2Address(IA, STACK_POINTER, IMMED, UNUSED, size);
			}
			return numberUsed;
		}
		
		/** Print the allocated registers to the listing file
		 * 
		 */
		public void printAllocated() {
			boolean old = CompilerOptions.listCode;
			CompilerOptions.listCode = true;
			boolean any = false;
			String which = "  Allocated Registers: ";
			for (int i = 0; i < MAX_REG + 1; ++i){
				if (!freeRegisters[i]) {
					which += (i + " ");
					any = true;
				}
			}
			if (!any){
				CompilerOptions.listCode("All registers free.");
			}
			else{
				CompilerOptions.listCode(which);
			}
			CompilerOptions.listCode("");
			CompilerOptions.listCode = old;
		}
	}	
	
	public final void init() {
		try {
			codefile = new PrintWriter(new FileOutputStream("codefile"));
		} catch (IOException e) {
			boolean old = CompilerOptions.listCode;
			CompilerOptions.listCode = true;
			CompilerOptions.listCode("File error creating codefile: " + e);
			CompilerOptions.listCode = old;
			System.exit(1);
		}
		try {
			objfile = new PrintWriter("OBJ");
		} catch (IOException e) {
			boolean old = CompilerOptions.listCode;
			CompilerOptions.listCode = true;
			CompilerOptions.listCode("File error creating OBJ: " + e);
			CompilerOptions.listCode = old;
			System.exit(1);
		}
		registers = new RegisterSet();
		savedRegisters.clear();
		variableIndex = 0;
		labelIndex = 0;
		constants = new ConstantStorage(this);

	}

	// --------------------- SAM addressing modes ---------------------------
	abstract static class Mode {
		private int samCode;
		private int bytesRequired;
		public static final Mode DREG = new DREG();
		public static final Mode DMEM = new DMEM();
		public static final Mode INDXD = new INDXD();
		public static final Mode IMMED = new IMMED();
		public static final Mode IREG = new IREG();
		public static final Mode IMEM = new IMEM();
		public static final Mode IINDXD = new IINDXD();
		public static final Mode PCREL = new PCREL();

		public abstract String address(int base, int displaement);
		
		public int bytesRequired(){ // number of bytes required for an instruction with this mode
			assert bytesRequired==2 || bytesRequired==4;
			return this.bytesRequired;
		}

		private Mode(final int samCode, int bytesRequired) {
			this.samCode = samCode;
			this.bytesRequired = bytesRequired;
		}

		private int samCode() {
			return samCode;
		}

		private static class DREG extends Mode {
			public String address(final int base, final int displacement) {
				return ("R" + base);
			}

			private DREG() {
				super(0, 2);
			}
		}

		private static class DMEM extends Mode {
			public String address(final int base, final int displacement) {
				return (displacement >= 0 ? "+" : "") + displacement;
			}

			private DMEM() {
				super(1, 4);
			}
		}

		private static class INDXD extends Mode {
			public String address(final int base, final int displacement) {
				return (displacement >= 0 ? "+" : "") + displacement + "(R"
						+ base + ')';
			}

			private INDXD() {
				super(2, 4);
			}
		}

		private static class IMMED extends Mode {
			public String address(final int base, final int displacement) {
				return ("#" + displacement);
			}

			private IMMED() {
				super(3, 4);
			}
		}

		private static class IREG extends Mode {
			public String address(final int base, final int displacement) {
				return ("*R" + base);
			}

			private IREG() {
				super(4, 2);
			}
		}

		private static class IMEM extends Mode {
			public String address(final int base, final int displacement) {
				return ((displacement >= 0 ? "*+" : "*") + displacement); //needed?
			}

			private IMEM() {
				super(5, 4);
			}
		}

		private static class IINDXD extends Mode {
			public String address(final int base, final int displacement) {
				return (displacement >= 0 ? "*+" : "*") + displacement + "(R"
						+ base + ')';
			}

			private IINDXD() {
				super(6, 4);
			}
		}

		private static class PCREL extends Mode {
			public String address(final int base, final int displacement) {
				return ("&" + displacement);
			}

			private PCREL() {
				super(7, 4);
			}
		}
	}

	// --------------------- machine locations ---------------------------------
	static class Location { // Encapsulate a SAM address in cpu or memory. Immutable
		private Mode mode = null;
		private int base = -99;
		private int displacement = -99;

		public Location(final Mode mode, final int base, final int displacement) {
			this.mode = mode;
			this.base = base;
			this.displacement = displacement;
		}

		// Note: No accessors, but this is an inner class
		public boolean equals(Object obj) {
			try {
				Location other = (Location) obj;
				return (other.mode == mode && other.base == base && other.displacement == displacement);
			} catch (ClassCastException e) {
				return false;
			}
		}

		public int hashCode() {
			return mode.samCode() * 11 + base * 17 + displacement;
		}
	}

	// --------------------- Constant table ------ inner ------------------
	static class SamConstant { // A constant to be put into the C1 block. Immutable
		private int offset; // Offset in the C1 block
		private ConstantLike item; // value of the constant

		SamConstant(final int offset, final ConstantLike item) {
			this.offset = offset;
			this.item = item;
		}

		/**
		 * The SemanticItem at this offset in the constant block.
		 * 
		 * @return the semantic item (ConstantExpression or StringItem)
		 */
		public ConstantLike item() {
			return item;
		}

		public String toString() {
			return "A const cell at offset: " + offset + ": " + item.toString();
		}
	}

	static class ConstantStorage implements Mnemonic { // The storage for C1 block constants
		private int constantOffset = 0;
		private Codegen codegen;

		// This class also serves as a warehouse for all SamConstant objects.
		private static Stack<SamConstant> storage = null;

		ConstantStorage(Codegen cg) {
			this.codegen = cg;
			storage = new Stack<SamConstant>();
		}

		public Location insertConstant(final ConstantLike semanticItem) {
			Mode mode = Codegen.INDXD;
			int base = Codegen.CONSTANT_BASE;
			int displacement = lookup(semanticItem);
			if (displacement < 0) {
				displacement = constantOffset;
				storage.push(new SamConstant(displacement, semanticItem));
				if (semanticItem instanceof Expression) { // assume integer or boolean constant
					constantOffset += ((Expression) semanticItem).type().size();
				}
				else { // assume string constant
					constantOffset += ((StringConstant) semanticItem).size();
				}
				
			}
			return new Location(mode, base, displacement);
		}

		public void genConstBlock() { // Generate the C1 block data		
			Iterator<SamConstant> elements = elements();
			while (elements.hasNext()) {
				ConstantLike temp = (elements.next()).item();
				if (temp instanceof ConstantExpression) {
					ConstantExpression constant = (ConstantExpression) temp;
					codegen.genIntDirective(constant.value());
				}
				else {
					StringConstant constant = (StringConstant) temp;
					codegen.genStringDirective(constant.samString());
				}

			}
		}

		public int lookup(final ConstantLike semanticItem) {
			return -1; // not implemented -- used for optimization of constants
		}

		/**
		 * Return an enumeration over all of the Sam Constants in the order in
		 * which they were created.
		 * 
		 * @return an enumeration over all the sam constants
		 */
		private static Iterator<SamConstant> elements() {
			return storage.iterator();
		}

		static final void init(){ // needed to reinitialize between runs of GUICompiler	
			storage = new Stack<SamConstant>();
		}
	}

	//------------------------------- Honors Code ------------------------------------
	/**
	 * Assigns the low bits of value to bits on the range [fromBit, toBit].
	 * 
	 * @param bits BitSet to modify.
	 * @param fromBit index of the starting bit to be modified.
	 * @param toBit index of the ending bit to be modified.
	 * @param value integer value to be assigned to that range.
	 */
	private static void setBits(BitSet bits, int fromBit, int toBit, int value){ 
		// low bits of value to low bits of from--to. value >= 0
		for(int i = fromBit; i <= toBit; ++i){
			if(value % 2 == 1){
				bits.set(i);
			}
			else {
				bits.clear(i); // in case bits wasn't "zeros" to start
			}
			value = value >> 1;			
		}
	}

	/**
	 * @param code short integer value.
	 * @return 16 bit BitSet representation.
	 */
	private static BitSet toBitSet(short code){
		BitSet result = new  BitSet(16);
		for(int i = 0; i < 16; ++i){
			if(Math.abs(code % 2) == 1){
				result.set(i);
			}
			code = (short)(code >> 1);
		}
		return result;
	}

	/**
	 * @param set bit set whose string representation you want.
	 * @return String representation of a bit set.
	 */
	private static String bitString(BitSet set){ // 16 bit sets only
		String result = "";
		for(int i = 15; i >=0; --i){
			if(set.get(i)){
				result += "1";
			}
			else{
				result += "0";
			}
		}
		return result;
	}
	
	
	/**
	 * @param set bit set whose short integer representation you want.
	 * @return short integer representation of a bit set.
	 */
	private static short fromBitSet(BitSet set){
		String value = bitString(set);
		return (short)Integer.parseInt(value, 2); // decode in base 2
	}
	
	/** Instruction in sam or macc. */
	interface Instruction {
		
		/**
		 * @return Returns the assembly code as a String.
		 */
		public abstract String samCode();
		
		/**
		 * @return Returns the macc code as a BitSet.
		 */
		public abstract BitSet maccCode();
		
		/**
		 * @return Returns the size, in words (2 bytes), of the instruction.
		 */
		public abstract int maccSize();
	}
	
	/** Comment instruction. Has no output in macc. */
	class Comment implements Instruction{
		
		private String message;
		
		public Comment(String message){
			this.message = message;
		}

		@Override
		public BitSet maccCode() {
			return new BitSet(0);
		}

		@Override
		public int maccSize() {
			return 0;
		}

		@Override
		public String samCode() {
			return ("% " + message);
		}
		
		
	}
	
	/** Jump instruction. Must define offset in a second pass. */
	class Jump implements Instruction{
		
		private SamOp opcode;
		private String label;
		private BitSet maccCode;
		
		public Jump(SamOp opcode, String label){
			this.opcode = opcode;
			this.label = label;
		}
		
		/**
		 * instantiates the macc code once the offset of the label is known.
		 */
		public void defineOffset(int offset){
			maccCode = new BitSet(32);
			maccCode.set(20);
			setBits(maccCode, 27, 31, opcode.opCodeValue());
			setBits(maccCode, 23, 26, opcode.specifier());
			setBits(maccCode, 0, 15, offset);
		}
		
		/**
		 * @return String name of the label to which this instruction jumps.
		 */
		public String label(){
			return label;
		}

		/**
		 * Will return null if defineOffset has not yet been called.
		 */
		@Override
		public BitSet maccCode() {
			return maccCode;
		}

		@Override
		public int maccSize() {
			return 2;
		}

		@Override
		public String samCode() {
			return (opcode.samCodeString() + label);
		}
	}
	
	/** Label instruction. Has no output in macc. */
	class Label implements Instruction{

		private SamOp opcode;
		private String name;
		
		public Label(final SamOp opcode, final String name){
			this.opcode = opcode;
			this.name = name;
		}
		
		public String name(){
			return name;
		}
		
		@Override
		public BitSet maccCode() {
			return new BitSet(0);
		}

		@Override
		public int maccSize() {
			return 0;
		}

		@Override
		public String samCode() {
			return opcode.samCodeString() + name;
		}
	}
	
	/** Write instruction. */
	class Write implements Instruction{
		
		public Write(final SamOp opcode, final int base, final int displacement){
			
		}

		@Override
		public BitSet maccCode() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int maccSize() {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public String samCode() {
			// TODO Auto-generated method stub
			return null;
		}
	}
	
	/** Skip instruction. Allocates memory. */
	class Skip implements Instruction{
		
		private SamOp opcode;
		private int allocatedSize;
		
		/**
		 * @param allocatedSize in 2 word intervals.
		 */
		public Skip(final SamOp opcode, final int allocatedSize){
			this.opcode = opcode;
			this.allocatedSize = allocatedSize;
		}

		@Override
		public BitSet maccCode() {
			return new BitSet(allocatedSize * 32);
		}

		@Override
		public int maccSize() {
			return (allocatedSize * 2);
		}

		@Override
		public String samCode() {
			return (opcode.samCodeString() + allocatedSize);
		}
	}
	
	/** Directive instruction. */
	class Directive implements Instruction{
		
		private SamOp opcode;
		private String directive;
		
		public Directive(final SamOp opcode, final String directive){
			this.opcode = opcode;
			this.directive = directive;
		}

		@Override
		public BitSet maccCode() {
			// TODO Implement Properly.
			BitSet maccCode = new BitSet(16);
			setBits(maccCode, 11, 15, opcode.opCodeValue());
			setBits(maccCode, 5, 8, opcode.specifier());
			return maccCode;
		}

		@Override
		public int maccSize() {
			return 2;
		}

		@Override
		public String samCode() {
			return (opcode.samCodeString() + directive);
		}
	}
	
	/** Instruction with zero addresses. */
	class ZeroAddress implements Instruction{
		
		private SamOp opcode;
		
		public ZeroAddress(final SamOp opcode){
			this.opcode = opcode; 
		}

		@Override
		public BitSet maccCode() {
			BitSet maccCode = new BitSet(16);
			setBits(maccCode, 11, 15, opcode.opCodeValue());
			setBits(maccCode, 7, 10, opcode.specifier());
			return maccCode;
		}

		@Override
		public int maccSize() {
			return 2;
		}

		@Override
		public String samCode() {
			return opcode.samCodeString();
		}
	}
	
	/** Instruction with one address. */
	class OneAddress implements Instruction{
		
		private SamOp opcode;
		private Mode mode;
		private int base;
		private int displacement;
		
		public OneAddress(final SamOp opcode, final Mode mode, final int base, final int displacement){
			this.opcode = opcode;
			this.mode = mode;
			this.base = base;
			this.displacement = displacement;
		}
		
		@Override
		public String samCode(){
			return (opcode.samCodeString() + mode.address(base, displacement));
		}

		@Override
		public BitSet maccCode() {
			// TODO Implement Properly.
			return null;
		}

		@Override
		public int maccSize() {
			return mode.bytesRequired();
		}
	}
	
	/** Instruction with two addresses. */
	class TwoAddress implements Instruction{
		
		private SamOp opcode;
		private int reg;
		private Mode mode;
		private int base;
		private int displacement;
		private String dmemLocation;
		
		public TwoAddress(final SamOp opcode, final int reg, final Mode mode, final int base, int displacement){
			this.opcode = opcode;
			this.reg = reg;
			this.mode = mode;
			this.base = base;
			this.displacement = displacement;
		}
		
		public TwoAddress(final SamOp opcode, final int reg, final String dmemLocation){
			this.opcode = opcode;
			this.reg = reg;
			this.dmemLocation = dmemLocation;
		}
		
		@Override
		public String samCode(){
			if (dmemLocation == null) {
			    if(opcode == Mnemonic.INC || opcode == Mnemonic.DEC){
			         int temp = reg;
			         if( reg == 0){
		                 temp = 16;
			         }
			         return (opcode.samCodeString()+ mode.address(base, displacement) + ", " + temp );
			    }
			    return (opcode.samCodeString() + 'R' + reg + ", " + mode.address(base, displacement));
			}
			return (opcode.samCodeString() + 'R' + reg + ", " + dmemLocation);
		}

		@Override
		public BitSet maccCode() {
			// TODO Implement Properly.
			return null;
		}

		@Override
		public int maccSize() {
			if(mode != null){
				return mode.bytesRequired();
			}
			return 2;
		}
	}
}