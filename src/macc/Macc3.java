package macc;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.BitSet;
import java.util.InputMismatchException;
import java.util.Scanner;
import java.util.regex.Pattern;


/** A representation of the Macc3 machine architecture. For details see the Sam doc and the Macc doc. 
 * Internally the memory is an array of BitSets so that we can manipulate individual bits in a cell
 * easily. The registers, likewise, are BitSets. Therefore we need quite a few methods to move values
 * of various kinds (bytes, shorts, ...) in an out of bitsets. <P>
 * 
 * Not all of the bit manipulation operations are documented here. We show a few to indicate the kind of thing to be 
 * done, but there are very many such operations. <p>
 * 
 * It is not internationalizable as a char is 8 bits and interchangeable with a byte.<p>
 * 
 * The program is also not very interesting, with mostly long methods and many switches. It represents a fairly
 * typical c-like solution.  A more interesting program might have an object for each instruction, with much of
 * the functionality centered in those objects. <p>
 * 
 * Some of the design decisions seem possibly suspect. Search for ??? in the sourc to find them. In particular the binary digit,
 * octal digit, hex digit, and char manipulations. <p>
 * 
 * This version makes no objection to an odd address, though the results might be somewhat difficult to manage. In particular
 * something like +7(R0) works fine. 
 * 
 * @author jbergin
 *
 */
public class Macc3 {
	
	private static final int MAXINT = 32767;
	private static final int MININT = -32768;
	private static final int MAXFULLINT = 2147483647;
	private static final int MINFULLINT = -2147483648;
	private static final int ALLBITSSET = -1;
	private static final int MINBYTE = -128;
	private static final int MAXBYTE = 127;
	private static final int REGSIZE = 16;
	private static final int MEMSIZE = MAXINT;
	private static final int INTSIZE = MAXINT;
	private static final int DREG = 0;
	private static final int DMEM = 1;
	private static final int INDXD = 2;
	private static final int IMMED = 3;
	private static final int IREG = 4;
	private static final int IMEM = 5;
	private static final int IINDXD = 6;
	private static final int PCREL = 7;
	
	private static final int INEG = 0;
	private static final int IA = 1;
	private static final int IS = 2;
	private static final int IM = 3;
	private static final int ID = 4;
	private static final int FN = 5;
	private static final int FA = 6;
	private static final int FS = 7;
	private static final int FM = 8;
	private static final int FD = 9;
	private static final int BI = 10;
	private static final int BO = 11;
	private static final int BA = 12;
	private static final int IC = 13;
	private static final int FC = 14;
	private static final int JSR = 15;
	private static final int BKT = 16;
	private static final int LD = 17;
	private static final int STO = 18;
	private static final int LDA = 19;
	private static final int FLT = 20;
	private static final int FIX = 21;
	private static final int J = 22;
	private static final int SR = 23;
	private static final int SL = 24;
	private static final int RD = 25;
	private static final int WR = 26;
	private static final int TRNG = 27;
	private static final int INC = 28;
	private static final int DEC = 29; // opcode 30 is unused
	private static final int HALT = 31;
	

	private BitSet[] memory = new BitSet[MAXINT+1];
	private BitSet[] registers = new BitSet[REGSIZE];
	private short programCounter;
	private BitSet instructionRegister; // bitset(16)
	private boolean stopProgram = false;
	private boolean 
		lt = false,
		eq = false, 
		gt = false; //for ic/fc
	private short oldpc;
	private boolean memImage = false;
	private boolean trace = false;
	private boolean dump = false;
	private boolean showRegisters = false;
	private DataInputStream inFile;
	private PrintStream dumpfile;
	private int rememberedDisplacement = 0; // displacement of most recent instruction (for tracing)
	private Scanner sysin;
	
	private int readInputFile(String filename) throws IOException{// is the endian correct??
		inFile = new DataInputStream(new FileInputStream(filename));
		int memLoc = 0;
		try{
			while(true){
				byte result1 = inFile.readByte();
				byte result2 = inFile.readByte();
				memory[memLoc++] = byteToBits8(result2);
				memory[memLoc++] = byteToBits8(result1);
			}
		}
		catch(IOException e){
			if(memLoc > 0){
				memImage = true;
			}
		}
		return memLoc;
	}
	
	private void showRegister(PrintStream out, int reg){
		out.print("R" + reg);
	}
	
	private void showIndxd(PrintStream out, int base, int displacement){
		out.print((displacement >=0?"+":"") + displacement + "(R" + base + ")" );
	}
	
	private void showAddress(PrintStream out, int location, int mode, int base, int displacement){
		switch(mode){
		case DREG:{
			showRegister(out, base);
			break;
		}
		case DMEM:{
			out.print(displacement);
			break;
		}
		case INDXD:{
			showIndxd(out, base, displacement);
			break;
		}
		case IMMED:{
			out.println("#" + displacement);
			break;
		}
		case IREG:{
			out.print("*");
			showRegister(out, base);
			break;
		}
		case IMEM:{
			out.print("*" + displacement);
			break;
		}
		case IINDXD:{
			out.print("*");
			showIndxd(out, base, displacement);
			break;
		}
		case PCREL:{
			out.print("&" + displacement);
			break;
		}
		default:{
			out.println("Invalid addressing mode: " + mode);
			break;
		}
		}
	}
	
	private void writeOpcode(PrintStream out, int opCode, int reg, int mode, int base){
		switch(opCode){
		default:{
			out.println("Invalid opcode: " + opCode);
			break;
		}
		case INEG:{
			out.print("IN    ");
			break;
		}
		case IA:{
			out.print("IA    ");
			break;
		}
		case IS:{
			out.print("IS    ");
			break;
		}
		case IM:{
			out.print("IM    ");
			break;
		}
		case ID:{
			out.print("ID    ");
			break;
		}
		case FN:{
			out.print("FN    ");
			break;
		}
		case FA:{
			out.print("FA    ");
			break;
		}
		case FS:{
			out.print("FS    ");
			break;
		}
		case FM:{
			out.print("FM    ");
			break;
		}
		case FD:{
			out.print("FD    ");
			break;
		}
		case BI:{
			out.print("BI    ");
			break;
		}
		case BO:{
			out.print("BO    ");
			break;
		}
		case BA:{
			out.print("BA    ");
			break;
		}
		case IC:{
			out.print("IC    ");
			break;
		}
		case FC:{
			out.print("FC    ");
			break;
		}
		case JSR:{
			out.print("JSR   ");
			break;
		}
		case BKT:{
			out.print("BKT   ");
			break;
		}
		case LD:{
			out.print("LD    ");
			break;
		}
		case STO:{
			out.print("STO   ");
			break;
		}
		case LDA:{
			out.print("LDA   ");
			break;
		}
		case FLT:{
			out.print("FLT   ");
			break;
		}
		case FIX:{
			out.print("FIX   ");
			break;
		}
		case J:{
			switch(reg){
			default:{
				out.println("Invalid jump instruction " + reg);
				break;
			}
			case 0:{
				out.print("JMP   ");
				break;
			}
			case 1:{
				out.print("JLT   ");
				break;
			}
			case 2:{
				out.print("JLE   ");
				break;
			}
			case 3:{
				out.print("JEQ   ");
				break;
			}
			case 4:{
				out.print("JNE   ");
				break;
			}
			case 5:{
				out.print("JGE   ");
				break;
			}
			case 6:{
				out.print("JGT   ");
				break;
			}
			case 7:{
				out.print("NOP   ");
				break;
			}
			}
			break;
		}
		case SR:{
			switch(mode){
			default:{
				out.println("Invalid shift right instruction " + mode);
				break;
			}
			case SRZ:{
				out.print("SRZ   ");
				break;
			}
			case SRO:{
				out.print("SRO   ");
				break;
			}
			case SRE:{
				out.print("SRE   ");
				break;
			}
			case SRC:{
				out.print("SRC   ");
				break;
			}
			case SRCZ:{
				out.print("SRCZ  ");
				break;
			}
			case SRCO:{
				out.print("SRCO  ");
				break;
			}
			case SRCE:{
				out.print("SRCE  ");
				break;
			}
			case SRCC:{
				out.print("SRCC  ");
				break;
			}		
			}
			break;
		}
		case SL:{
			switch(mode){
			default:{
				out.println("Invalid shift left instruction " + mode);
				break;
			}
			case SLZ:{
				out.print("SLZ   ");
				break;
			}
			case SLO:{
				out.print("SLO   ");
				break;
			}
			case SLE:{
				out.print("SLE   ");
				break;
			}
			case SLC:{
				out.print("SLC   ");
				break;
			}
			case SLCZ:{
				out.print("SLCZ  ");
				break;
			}
			case SLCO:{
				out.print("SLCO  ");
				break;
			}
			case SLCE:{
				out.print("SLCE  ");
				break;
			}
			case SLCC:{
				out.print("SLCC  ");
				break;
			}
			}
			break;
		}
		case RD:{
			switch(reg){
			default:{
				out.println("Invalid read instruction " + reg);
				break;
			}
			case 0:{
				out.print("RDI   ");
				break;
			}
			case 1:{
				out.print("RDF   ");
				break;
			}
			case 2:{
				out.print("RDBD  ");
				break;
			}
			case 3:{
				out.print("RDBW  ");
				break;
			}
			case 4:{
				out.print("RDOD  ");
				break;
			}
			case 5:{
				out.print("RDOW  ");
				break;
			}
			case 6:{
				out.print("RDHD  ");
				break;
			}
			case 7:{
				out.print("RDHW  ");
				break;
			}
			case 8:{
				out.print("RDCH  ");
				break;
			}
			case 9:{
				out.print("RDST  ");
				break;
			}
			case 11:{
				out.print("RDNL  ");
				break;
			}
			
			}
			break;
		}
		case WR:{
			switch(reg){
			default:{
				out.println("Invalid write instruction " + reg);
				break;
			}
			case 0:{
				out.print("WRI   ");
				break;
			}
			case 1:{
				out.print("WRF   ");
				break;
			}
			case 2:{
				out.print("WRBD  ");
				break;
			}
			case 3:{
				out.print("WRBW  ");
				break;
			}
			case 4:{
				out.print("WROD  ");
				break;
			}
			case 5:{
				out.print("WROW  ");
				break;
			}
			case 6:{
				out.print("WRHD  ");
				break;
			}
			case 7:{
				out.print("WRHW  ");
				break;
			}
			case 8:{
				out.print("WRCH  ");
				break;
			}
			case 9:{
				out.print("WRST  ");
				break;
			}
			case 10:{
				out.print("RDIN  ");//
				break;
			}
			case 11:{
				out.print("WRNL  ");
				break;
			}
			}
			break;
		}
		case TRNG:{
			out.print("TRNG  ");
			break;
		}
		case HALT:{
			switch(mode){
			default:{
				out.println("Invalid system control instruction: " + mode);
				break;
			}
			case 0:{
				out.print("HALT  ");
				break;
			}
			case 1:{
				out.print("PUSH  ");
				break;
			}
			case 2:{
				out.print("POP   ");
				break;
			}
			}
			break;
		}
		case INC:{
			out.print("INC   ");
			break;
		}
		case DEC:{
			out.print("DEC   ");
			break;
		}
		}
	}
	
	private void writeInstruction(PrintStream out, int pc, int opCode, int reg, int mode, int base, int displacement){
		out.print("     " + pc + "     ");
		writeOpcode(out, opCode, reg, mode, base);
		if(opCode >= INEG && opCode <= FLT || opCode == TRNG){
			showRegister(out, reg);
			out.print(", ");
			showAddress(out, pc, mode, base, displacement);
		}
		else if ( (opCode == J && reg != 7)
				|| (opCode == WR && reg != 11)
				|| (opCode == RD)
				|| (opCode == INC) || (opCode == DEC)
		){
			showAddress(out, pc, mode, base, displacement);
			if(opCode == INC || opCode == DEC){
				out.print(", " + base);
			}
		}
		else if(opCode == SR || opCode == SL){
			showRegister(out, reg);
			out.print(", " + base);
		}
		else if(opCode == HALT){
			if(mode == 1 || mode == 2){
				showRegister(out, reg);
				out.print(", " + displacement);
			}
		}
		out.println();
	}
	
	private void writeBoolean(boolean b){
		if(b){
			System.out.print("TRUE");
		}
		else{
			System.out.print("FALSE");
		}
	}
	
	private void displayRegisters(){
		System.out.println("#############################");
		System.out.print("pc= " + programCounter + " LT= ");
		writeBoolean(lt);
		System.out.print(" EQ= ");
		writeBoolean(eq);
		System.out.print(" GT= ");
		writeBoolean(gt);
		System.out.println();
		for(int i = 0; i < REGSIZE; ++i){
			System.out.print("  Reg[" + i + "] = " + bits16toShort(registers[i]));
			if((i + 1) % 4 == 0){
				System.out.println();
			}
		}
		System.out.println("#############################");
	}
	
	/** This is the fetch-increment instruction steps of the main machine loop
	 * @return a bitset representing the instruction at the pc. Also increments the pc. 
	 */
	public BitSet fetchWord(){
		BitSet result = bytesToBits16(memory[programCounter], memory[(programCounter+1) % MEMSIZE]);		
		programCounter = (short)((programCounter + 2)%MEMSIZE);
		return result;
	}
	
	/** Extract the five bit instruction (bits 11..15) from an instruction
	 * @param instruction a bitset representing an instruction
	 * @return and int giving the instruction in range INEG(0)..HALT(31)
	 */
	public int opCode(BitSet instruction){ // opcode is bits 11-15 of instruction
		int result = 0;
		if(instruction.get(15)){
			result = 16;
		}
		if(instruction.get(14)){
			result += 8;
		}
		if(instruction.get(13)){
			result += 4;
		}
		if(instruction.get(12)){
			result += 2;
		}
		if(instruction.get(11))
			result += 1;
		return result;
	}
	
	/** Extract the register (first operand) from bits 7..11 of an instruction
	 * @param instruction a bitset representing an instruction
	 * @return an int giving a register number
	 */
	public int reg(BitSet instruction){ // reg is bits 7-11 of instruction
		int result = 0;
		if(instruction.get(10)){
			result = 8;
		}
		if(instruction.get(9)){
			result += 4;
		}
		if(instruction.get(8)){
			result += 2;
		}
		if(instruction.get(7)){
			result += 1;
		}
		return result;
	}
	
	/** Extract the address mode (bits 4..6) from an instruction
	 * @param instruction a bitset representing an instruction
	 * @return an int giving the addressing mode
	 */
	public int mode(BitSet instruction){ // mode is bits 4-6 of instruction
		int result = 0;
		if(instruction.get(6)){
			result += 4;
		}
		if(instruction.get(5)){
			result += 2;
		}
		if(instruction.get(4)){
			result += 1;
		}
		return result;
	}
	
	/** Extract the base (bits 0..3) from an instruction
	 * @param instruction a bitset representing an instruction
	 * @return an int giving the base register
	 */
	public int base(BitSet instruction){ //base is bits 0-3 of instruction
		int result = 0;
		if(instruction.get(3)){
			result = 8;
		}
		if(instruction.get(2)){
			result += 4;
		}
		if(instruction.get(1)){
			result += 2;
		}
		if(instruction.get(0)){
			result += 1;
		}
		return result;
	}
	
	private void rememberDisplacement(int displacement){ // hack to simulate var args from Pascal
		this.rememberedDisplacement = displacement;
	}
	
	private int rememberedDisplacement(){// hack to simulate var args from Pascal. used only in tracing
		return rememberedDisplacement;
	}
	
	
	/** Compute an effective address of an instruction at the current pc location
	 * @param mode the instruction mode 0..7
	 * @param base the base register 0..15
	 * @param displacement the displacement a 16 bit integer
	 * @return the machine address represented if non-negative, or if negative, the base is the register address
	 */
	public int effectiveAddress(int mode, int base, int displacement){
		int taddr = 0;
		displacement = 0; //in pascal this is returned as a var??
		switch(mode){
		case DREG:{
				taddr = -1 - base; // register encoded use (-result - 1) to retrieve the register
			break;
		}
		case DMEM:{
			BitSet temp = fetchWord();
			taddr = bits16toShort(temp);
			displacement = taddr;
			break;
		}
		case INDXD:{
			BitSet temp = fetchWord();
			displacement = bits16toShort(temp);
			taddr = (displacement + bits16toShort(registers[base])) % MEMSIZE;
			break;
		}
		case IMMED:{
			taddr = programCounter;
			BitSet temp = bytesToBits16(memory[taddr], memory[(taddr+1) % MEMSIZE]);
			displacement = bits16toShort(temp);
			programCounter = (short)((programCounter + 2) % MEMSIZE);
			break;
		}
		case IREG:{
			taddr = bits16toShort(registers[base]);
			break;
		}
		case IMEM:{
			BitSet temp = fetchWord();
			displacement = bits16toShort(temp);
			temp = bytesToBits16(memory[displacement], memory[(displacement + 1) % MEMSIZE]);
			taddr = bits16toShort(temp);
			break;
		}
		case IINDXD:{
			BitSet temp = fetchWord();
			displacement = bits16toShort(temp);
			taddr = (displacement + bits16toShort(registers[base])) % MEMSIZE;
			temp = bytesToBits16(memory[taddr], memory[(taddr +1) % MEMSIZE]);
			taddr = bits16toShort(temp);
			break;
		}
		case PCREL:{
			BitSet temp = fetchWord();
			displacement = bits16toShort(temp);
			taddr = (displacement + programCounter) % MEMSIZE;
			break;
		}
		}
		rememberDisplacement(displacement); // only for tracing
		return taddr;
	}
	
	private BitSet lowByte(BitSet word){ // bits 0..7
		BitSet result = new BitSet(8);
		for(int i = 0; i < 8; ++i){
			if(word.get(i)){
				result.set(i);
			}
		}
		return result;
	}
	
	private BitSet highByte(BitSet word){ // bits 8..15
		BitSet result = new BitSet(8);
		for(int i = 0; i < 8; ++i){
			if(word.get(i + 8)){
				result.set(i);
			}
		}
		return result;
	}
	
	/** Represents the INC and DEC instructions
	 * @param opcode INC or DEC
	 * @param amount how much to increment or decrement by (0..15) 0 is interpreted as 16
	 * @param mode the mode of the affected location
	 * @param base the base of the affected location
	 * @param displacement the displacement of the affected location
	 */
	public void incDecOperation(int opcode, int amount, int mode, int base, int displacement){
		if(amount == 0){
			amount = 16;
		}
		displacement = this.rememberedDisplacement();
		int ans = 0;
		BitSet temp = locationAsBits(mode, base, displacement);
//		int addr = effectiveAddress(mode, base, displacement);
//		if(addr >= 0){
//			temp = bytesToBits16(memory[addr], memory[(addr + 1) % MEMSIZE]);
//		}
//		else{
//			temp = registers[base];
//		}
		int iopl = bits16toShort(temp);
		if(opcode == INC){
			ans = iopl + amount;
		}
		else if(opcode == DEC){
			ans = iopl - amount;
		}
		int addr = effectiveAddress(mode, base, displacement);
		shortToMemoryWord((short)ans, addr);
//		temp = shortToBits16((short)ans);
//		if(addr >= 0){
//			//word to bytes 
//			memory[(addr + 1) % MEMSIZE] = highByte(temp);
//			memory[addr] = lowByte(temp);
//		}
//		else {
//			registers[base] = temp;
//		}		
		rememberDisplacement(displacement);
	}
	
	private void assureBytesAt(int addr, int bytes){
		for(int i = 0; i < bytes; ++i){
			if(memory[(addr + i) % MEMSIZE] == null){
				memory[(addr + i) % MEMSIZE] = new BitSet(8);
			}
		}
	}
	
	private BitSet locationAsBits(int mode, int base, int displacement){
		int addr = effectiveAddress(mode, base, displacement);
		BitSet temp;
		if(addr >= 0){
			assureBytesAt(addr, 2);
			temp = bytesToBits16(memory[addr], memory[(addr + 1) % MEMSIZE]);
		}
		else{
			temp = registers[base];
		}
		return temp;
	}
	
	private float doubleWordAsFloat(int mode, int base, int displacement){
		int addr = effectiveAddress(mode, base, displacement);
		float temp;
		if(addr >= 0){
			assureBytesAt(addr, 4);
			temp = floatFromBits(memory[(addr+0) % MEMSIZE], memory[(addr+1) % MEMSIZE], memory[(addr+2) % MEMSIZE], memory[(addr+3) % MEMSIZE]);
		}
		else{
			temp = floatFromBits(registers[base], registers[(base + 1) % REGSIZE]);
		}
		return temp;
	}
	
	/** All of the two address instructions
	 * @param opcode the specific opcode of the instruction
	 * @param reg the first operand
	 * @param mode the mode of the second operand
	 * @param base the base of the second operand
	 * @param displacement the displacement of the second operand
	 */
	public void twoAddressOperation(int opcode, int reg, int mode, int base, int displacement){
		if(opcode >= INEG && opcode <= ID || opcode == IC){ 
			BitSet temp = locationAsBits(mode, base, displacement);
			int iop2 = bits16toShort(temp);
			int iop1 = bits16toShort(registers[reg]);
			int ans = 0;
			switch(opcode){
			case INEG:{
				ans = -iop2;
				break;
			}
			case IA:{
				ans = iop1 + iop2;
				break;
			}
			case IS:{
				ans = iop1 - iop2;
				break;
			}
			case IM:{
				ans = iop1 * iop2;
				break;
			}
			case ID:{
				if(iop2 == 0){
					System.out.println("Integer Zero Divide check. Exiting.");
					System.exit(1);
				}
				else{
					ans = iop1 / iop2;
				}
				break;
			}
			case IC:{
				ans = iop1;
				lt = eq = gt = false;
				if(iop1 < iop2){
					lt = true;
				}else if(iop1 == iop2){
					eq = true;
				}else{
					gt = true;
				}
				break;
			}
			}
			registers[reg] = shortToBits16((short)ans);
		}
		else if(opcode >= FN && opcode <= FD || opcode == FC){
			float left = floatFromBits(registers[reg], registers[(reg + 1) % REGSIZE]);
			float right = doubleWordAsFloat(mode, base, displacement);
			switch(opcode){
			case FN: {
				left = - right;
				break;
				}
			case FA: {
				left = left + right;
				break;
				}
			case FS: {
				left = left - right;
				break;
				}
			case FM: {
				left = left * right;
				break;
				}
			case FD: {
				if(right > -1.0e-6 && right < 1.0e-6){
					System.out.println(" Floating zero divide. Exiting.");
					System.exit(1);
				}
				else{
					left = left / right;
				}
				break;
				}
			case FC: {
				lt = eq = gt = false;
				if(left < right){
					lt = true;
				}
				else if(left == right){
					eq = true;
				}
				else {
					gt = true;
				}
				break;
				}
			}
			BitSet result = floatToBits32(left);
			registers[reg] = lowWord(result);
			registers[(reg + 1) % REGSIZE] = highWord(result); 
		}
		else if(opcode >= BI && opcode <= BA){
			BitSet wd = locationAsBits(mode, base, displacement);
			switch(opcode){
			case BI:{
				registers[reg] = new BitSet(16);
				for(int i = 0; i < 16; ++i){
					if(! wd.get(i)){
						registers[reg].set(i);
					}
				}
				break;
			}
			case BO:{
				registers[reg].or(wd);
				break;
			}
			case BA:{
				registers[reg].and(wd);
				break;
			}
			}
		} 
		else if(opcode == JSR){
			int addr = effectiveAddress(mode, base, displacement);
			registers[reg] = shortToBits16(programCounter);
			if(addr >= 0){
				programCounter = (short)addr;
			}
			else{
				System.out.println("JSR to a register " + (programCounter - 2));
				stopProgram = true;
			}
		}
		else if (opcode == BKT){
			int addr = effectiveAddress(mode, base, displacement);
			if(addr < 0){
				System.out.println("Address of BKT is a Register " + (programCounter - 2));
				stopProgram = true;
			}
			else {
				int iop2 = bits16toShort(registers[reg]);
				for(int i = 0; i < bits16toShort(registers[(reg + 1) % REGSIZE]); ++i){
					memory[(addr + i) % MEMSIZE] = memory[(iop2 + i) % MEMSIZE];
				}
			}
		}
		else if (opcode == LD){
			registers[reg] = locationAsBits(mode, base, displacement);
		}
		else if (opcode == STO){
			int addr = effectiveAddress(mode, base, displacement);
			if(addr >= 0){
				assureBytesAt(addr, 2);
				memory[(addr + 1) % MEMSIZE] = highByte(registers[reg]);// NOTE that the low byte is from the high address
				memory[addr] = lowByte(registers[reg]);
				if(trace){
					System.out.println(" ***** value " + bits16toShort(bytesToBits16(memory[addr], memory[(addr + 1) % MEMSIZE])) + 
							" stored at location" + addr);
				}
			}
			else{
				registers[base] = registers[reg];
			}
		}
		else if (opcode == LDA){
			int addr = effectiveAddress(mode, base, displacement);
			if(addr >= 0){
				registers[reg] = shortToBits16((short)addr);
			}
			else{
				System.out.println("LDA of a register " + (programCounter - 2));
				stopProgram = true;
			}
		}
		else if (opcode == FLT){
			BitSet temp = locationAsBits(mode, base, displacement);
			int value = bits16toShort(temp);
			float rValue = (float)value;
			int bValue = Float.floatToIntBits(rValue);
			temp = intToBits32(bValue);
			registers[reg] = lowWord(temp);
			registers[(reg+1) % REGSIZE] = highWord(temp);
		}
		else if (opcode == FIX){
			Float value = doubleWordAsFloat(mode, base, displacement);
			short iValue = (short)value.intValue();
			registers[reg] = shortToBits16(iValue);
		}
		else if (opcode == TRNG){
			int addr = effectiveAddress(mode, base, displacement);
			if(addr >= 0){
				assureBytesAt(addr, 4);
				int iop1 = bits16toShort(bytesToBits16(memory[addr], memory[(addr + 1) % MEMSIZE]));
				int iop2 = bits16toShort(bytesToBits16(memory[(addr + 2) % MEMSIZE], memory[(addr + 3) % MEMSIZE]));
				int ans = bits16toShort(registers[reg]);
				if(ans < iop1 || ans > iop2){
					System.out.println("Index expression out of bounds at " + (programCounter - 2) + 
							" expr = " + ans + " lower = " + iop1 + " upper = " + iop2);
					stopProgram = true;
				}
			}
			else{
				System.out.println(" Address of TRNG is a register " + (programCounter - 2));
				stopProgram = true;
			}
		}
	}
	
	private static final int JMP = 0, JLT = 1, JLE = 2, JEQ = 3, JNE = 4, JGE = 5, JGT = 6, NOP = 7;
	
	/** All of the jump instructions except JSR 
	 * @param jumpMode which jump JMP(0) JLT(1) etc
	 * @param mode the mode of target location
	 * @param base the base of the target location
	 * @param displacement the displacement of the target location
	 */
	public void jumpOperation(int jumpMode, int mode, int base, int displacement){
		int addr = effectiveAddress(mode, base, displacement);
		if(addr < 0){
			System.out.println(" Jump to a register " + (programCounter - 2));
			stopProgram = true;
		}
		else{
			switch(jumpMode){
			default:{
				System.out.println(" Invalid Jump mode " + (programCounter - 2));
				stopProgram = true;
			}
			case JMP:{
				programCounter = (short)addr; //jmp
				break;
			}
			case JLT:{
				if(lt){
					programCounter = (short)addr; //jlt
				}
				break;
			}
			case JLE:{
				if(lt || eq){
					programCounter = (short)addr; //jle
				}
				break;
			}
			case JEQ:{
				if(eq){
					programCounter = (short)addr; //jeq
				}
				break;
			}
			case JNE:{
				if(!eq){
					programCounter = (short)addr; //jne
				}
				break;
			}
			case JGE:{
				if(gt || eq){
					programCounter = (short)addr; //jge
				}
				break;
			}
			case JGT:{
				if(gt){
					programCounter = (short)addr; //jgt
				}
				break;
			}
			case NOP:{
				//NOP
				break;
			}
			}
		}
	}

	private void partialRight(BitSet temp, int amount){
		for(int j = 0; j < 16 - amount; ++j){
			if(temp.get(j + amount)){
				temp.set(j);
			}
			else{
				temp.clear(j);
			}
		}
	}
	
	private final int SRZ=0, SRO=1, SRE=2, SRC=3, SRCZ=4, SRCO=5, SRCE=6, SRCC=7; 

	/** All of the shift right instructions 
	 * @param reg the register to be shifted
	 * @param mode which shift SRZ(0) etc
	 * @param amount how much to shift 0..15. Zero is a 16 bit shift
	 */
	public void shiftRightOperation(int reg, int mode, int amount){
		switch (mode){
		case SRZ: {
			if(amount == 0 ){ // 16 bit right zero shift
				registers[reg].clear();
			}
			else{
				BitSet temp = registers[reg];
				partialRight(temp, amount);
				for(int i = 0; i < amount; ++i){
					temp.clear(15 - i);
				}
				registers[reg] = temp;
			}
			return;
		}
		case SRO: {
			if(amount == 0 ){ // 16 bit right one shift
				registers[reg].set(0, 16);
			}
			else{
				BitSet temp = registers[reg];
				partialRight(temp, amount);
				for(int i = 0; i < amount; ++i){
					temp.set(15 - i);
				}
				registers[reg] = temp;
			}
			return;
		}
		case SRE: {
			boolean first = registers[reg].get(15);
			if(amount == 0 ){ // 16 bit right extend shift
				if(first){
					registers[reg].set(0, 16);
				}
				else{
					registers[reg].clear();
				}
			}
			else{
				BitSet temp = registers[reg];
				partialRight(temp, amount);
				for(int i = 0; i < amount; ++i){
					if(first){
						temp.set(15 - i);
					}
					else{
						temp.clear(15-i);
					}
				}
				registers[reg] = temp;
			}
			return;
		}
		case SRC: {
			if(amount == 0){
				return;
			}
			BitSet temp = registers[reg];
			for(int i = 0; i < amount; ++i){
				boolean first = temp.get(0);
				partialRight(temp, 1);
				if(first){
					temp.set(15);
				}
				else{
					temp.clear(15);
				}
			}
			registers[reg] = temp;
			return;
		}
		case SRCZ: {
			BitSet w1 = registers[reg];
			BitSet w2 = registers[(reg + 1) % REGSIZE];
			if(amount == 0){ // 16 bit shift
				w2 = w1;
				w1.clear();
			}
			else{
				for(int i = 1; i <= amount; ++i){
					for(int j = 0; j < 15; ++j){
						w2.clear(j);
						if(w2.get(j + 1)){
							w2.set(j);
						}
					}
					w2.clear(15);
					if(w1.get(0)){
						w2.set(15);
					}
					for(int j = 0; j < 16 - i; ++j){
						w1.clear(j);
						if(w1.get(j+1)){
							w1.set(j);
						}
					}
					w1.clear(16-i);
				}
			}
			registers[reg] = w1;
			registers[(reg + 1) % REGSIZE] = w2;
			return;
		}
		case SRCO: {
			BitSet w1 = registers[reg];
			BitSet w2 = registers[(reg + 1) % REGSIZE];
			if(amount == 0){ // 16 bit shift
				w2 = w1;
				w1 = shortToBits16((short)ALLBITSSET);
			}
			else{
				for(int i = 1; i <= amount; ++i){
					for(int j = 0; j < 15; ++j){
						w2.clear(j);
						if(w2.get(j + 1)){
							w2.set(j);
						}
					}
					w2.clear(15);
					if(w1.get(0)){
						w2.set(15);
					}
					for(int j = 0; j < 16 - i; ++j){
						w1.clear(j);
						if(w1.get(j+1)){
							w1.set(j);
						}
					}
					w1.set(16-i);
				}
			}
			registers[reg] = w1;
			registers[(reg + 1) % REGSIZE] = w2;
			return;
		}
		case SRCE: {
			BitSet w1 = registers[reg];
			BitSet w2 = registers[(reg + 1) % REGSIZE];
			boolean high = w1.get(15);
			if(amount == 0){ // 16 bit shift
				w2 = w1;
				if(high){	
					w1 = shortToBits16((short)ALLBITSSET);
				}
				else {
					w1.clear();
				}
			}
			else{
				for(int i = 1; i <= amount; ++i){
					for(int j = 0; j < 15; ++j){
						w2.clear(j);
						if(w2.get(j + 1)){
							w2.set(j);
						}
					}
					w2.clear(15);
					if(w1.get(0)){
						w2.set(15);
					}
					for(int j = 0; j < 16 - i; ++j){
						w1.clear(j);
						if(w1.get(j+1)){
							w1.set(j);
						}
					}
					w1.clear(16-i);
					if(high){
						w1.set(16-i);
					}
				}
			}
			registers[reg] = w1;
			registers[(reg + 1) % REGSIZE] = w2;
			return;
		}
		case SRCC: {
			BitSet w1 = registers[reg];
			BitSet w2 = registers[(reg + 1) % REGSIZE];
			if(amount == 0){ // 16 bit shift
				BitSet w3 = w2;
				w2 = w1;
				w1 = w3;
			}
			else{
				for(int i = 1; i <= amount; ++i){
					boolean low = w2.get(0);
					for(int j = 0; j < 15; ++j){
						w2.clear(j);
						if(w2.get(j + 1)){
							w2.set(j);
						}
					}
					w2.clear(15);
					if(w1.get(0)){
						w2.set(15);
					}
					for(int j = 0; j < 15; ++j){
						w1.clear(j);
						if(w1.get(j+1)){
							w1.set(j);
						}
					}
					w1.clear(15);
					if(low){
						w1.set(15);
					}
				}
			}
			registers[reg] = w1;
			registers[(reg + 1) % REGSIZE] = w2;
			return;
		}
		}
		System.out.println(" Illegal double Right Shift. Exiting");
		System.exit(2);
	}

	private final int SLZ=0, SLO=1, SLE=2, SLC=3, SLCZ=4, SLCO=5, SLCE=6, SLCC=7; 
	
	private void partialLeft(BitSet temp, int amount){
		for(int j = 15 - amount; j >= 0; --j){
			if(temp.get(j)){
				temp.set(j+amount);
			}
			else{
				temp.clear(j+amount);
			}
		}
	}
	
	/** All of the shift left instructions. 
	 * @param reg the register to be shifted
	 * @param mode which shift SLZ(0) etc
	 * @param amount how much to shift 0..15. Zero is a 16 bit shift
	 */
	public void shiftLeftOperation(int reg, int mode, int amount){
		switch (mode){
		case SLZ: {
			if(amount == 0){ // 16 bit left zero shift
				registers[reg].clear();
			}
			else{
				BitSet temp = registers[reg];
				partialLeft(temp, amount);
				for(int i = 0; i < amount; ++i){
					temp.clear(i);
				}
				registers[reg] = temp;
			}
			return;
		}
		case SLO: {
			if(amount == 0 ){ // 16 bit left one shift
				registers[reg].set(0, 16);
			}
			else{
				BitSet temp = registers[reg];
				partialLeft(temp, amount);
				for(int i = 0; i < amount; ++i){
					temp.set(i);
				}
				registers[reg] = temp;
			}
			return;
		}
		case SLE: {
			boolean first = registers[reg].get(0);
			if(amount == 0 ){ // 16 bit left extend shift
				if(first){
					registers[reg].set(0, 16);
				}
				else{
					registers[reg].clear();
				}
			}
			else{
				BitSet temp = registers[reg];
				partialLeft(temp, amount);
				for(int i = 0; i < amount; ++i){
					if(first){
						temp.set(i);
					}
					else{
						temp.clear(i);
					}
				}
				registers[reg] = temp;
			}
			return;
		}
		case SLC: {
			if(amount == 0){
				return;
			}
			BitSet temp = registers[reg];
			for(int i = 0; i < amount; ++i){
				boolean first = temp.get(15);
				partialLeft(temp, 1);
				if(first){
					temp.set(0);
				}
				else{
					temp.clear(0);
				}
			}
			registers[reg] = temp;
			return;
		}
		case SLCZ: {
			BitSet w1 = registers[reg];
			BitSet w2 = registers[(reg + 1) % REGSIZE];
			if(amount == 0){ // 16 bit shift
				w1 = w2;
				w2 = new BitSet(16);
			}
			else{
				for(int i = 1; i <= amount; ++i){
					for(int j = 14; j >= 0; --j){
						w1.clear(j + 1);
						if(w1.get(j)){
							w1.set(j + 1);
						}
					}
					w1.clear(0);
					if(w2.get(15)){
						w1.set(0);
					}
					for(int j = 14; j >= i - 1; --j){
						w2.clear(j + 1);
						if(w2.get(j)){
							w2.set(j + 1);
						}
					}
					w2.clear(i - 1);
				}
			}
			registers[reg] = w1;
			registers[(reg + 1) % REGSIZE] = w2;
			return;
		}
		case SLCO: {
			BitSet w1 = registers[reg];
			BitSet w2 = registers[(reg + 1) % REGSIZE];
			if(amount == 0){ // 16 bit shift
				w1 = w2;
				w2 = shortToBits16((short)ALLBITSSET);
			}
			else{
				for(int i = 1; i <= amount; ++i){
					for(int j = 14; j >= 0; --j){
						w1.clear(j + 1);
						if(w1.get(j)){
							w1.set(j + 1);
						}
					}
					w1.clear(0);
					if(w2.get(15)){
						w1.set(0);
					}
					for(int j = 14; j >= i - 1; --j){
						w2.clear(j + 1);
						if(w2.get(j)){
							w2.set(j + 1);
						}
					}
					w2.set(i - 1);
				}
			}
			registers[reg] = w1;
			registers[(reg + 1) % REGSIZE] = w2;
			return;
		}
		case SLCE: {
			BitSet w1 = registers[reg];
			BitSet w2 = registers[(reg + 1) % REGSIZE];
			boolean low = w2.get(0);
			if(amount == 0){ // 16 bit shift
				w1 = w2;
				if(low){
					w2 = shortToBits16((short)ALLBITSSET);
				}
				else{
					w2.clear();
				}
			}
			else{
				for(int i = 1; i <= amount; ++i){
					for(int j = 14; j >= 0; --j){
						w1.clear(j + 1);
						if(w1.get(j)){
							w1.set(j + 1);
						}
					}
					w1.clear(0);
					if(w2.get(15)){
						w1.set(0);
					}
					for(int j = 14; j >= i - 1; --j){
						w2.clear(j + 1);
						if(w2.get(j)){
							w2.set(j + 1);
						}
					}
					if(low){
						w2.set(i - 1);
					}
					else{
						w2.clear(i - 1);
					}
				}
			}
			registers[reg] = w1;
			registers[(reg + 1) % REGSIZE] = w2;
			return;
		}
		case SLCC: {
			BitSet w1 = registers[reg];
			BitSet w2 = registers[(reg + 1) % REGSIZE];
			if(amount == 0){ // 16 bit shift
				BitSet w3 = w2;
				w2 = w1;
				w1 = w3;
			}
			else{
				for(int i = 1; i <= amount; ++i){
					boolean high = w1.get(15);
					for(int j = 14; j >= 0; --j){
						w1.clear(j + 1);
						if(w1.get(j)){
							w1.set(j + 1);
						}
					}
					w1.clear(0);
					if(w2.get(15)){
						w1.set(0);
					}
					for(int j = 14; j >= 0; --j){
						w2.clear(j + 1);
						if(w2.get(j)){
							w2.set(j + 1);
						}
					}
					if(high){
						w2.set(0);
					}
					else{
						w2.clear(0);
					}
				}
			}
			registers[reg] = w1;
			registers[(reg + 1) % REGSIZE] = w2;
			return;
		}
		}
		System.out.println(" Illegal double Left Shift. Exiting");
		System.exit(2);
	}
	
	/* Assumes that addr is >= 0 (memory) or an encoded register (-reg - 1)
	 * as produced by effectiveAddress
	 */
	private void shortToMemoryWord(short value, int addr){
		BitSet temp = shortToBits16(value);
		if(addr >= 0){
			assureBytesAt(addr, 2);
			bits16toBytes(temp, memory[addr], memory[(addr + 1) % MEMSIZE] );
		} 
		else{
			registers[-addr - 1] = temp;
		}

	}
	
	private final int RDI = 0, RDF = 1, RDBD = 2, RDBW = 3, RDOD = 4, RDOW = 5, RDHD = 6, RDHW = 7, RDCH = 8, RDST = 9, RDNL = 11;
	private final int WRI = 0, WRF = 1, WRBD = 2, WRBW = 3, WROD = 4, WROW = 5, WRHD = 6, WRHW = 7, WRCH = 8, WRST = 9, WRNL = 11;
	// There is no mode 10 in the i/o instructions
	
	
	/** All of the input instructions 
	 * @param readMode which instruction rdi(0) rdf(1) etc. 
	 * @param mode the mode of the locations(s) to be read
	 * @param base the base of the location
	 * @param displacement the displacement if any
	 */
	public void readOperation(int readMode, int mode, int base, int displacement){
		if(readMode >= 0 && readMode <= 11){
			int addr = effectiveAddress(mode, base, displacement);
			switch(readMode){
			case RDI:{ // rdi
				try {
					int i = sysin.nextInt();
					if(i > MAXINT || i < MININT){
						System.out.println("Invalid input in RDI. " + (programCounter - 2));
						stopProgram = true;
					}
					else{
						shortToMemoryWord((short)i, addr);
					}
				} catch (Exception e) {
					System.out.println(e.getMessage()); // TODO debug
					System.out.println(" Could not read. Exiting");
					System.exit(1);
				}
				break;
			}
			case RDF:{ // rdf
				float value = sysin.nextFloat();
				int iValue = Float.floatToIntBits(value);
				BitSet temp = intToBits32(iValue);
				if(addr >= 0){
					assureBytesAt(addr, 4);
					memory[addr] = getByte(0, temp);
					memory[(addr+1) % MEMSIZE] = getByte(1, temp);
					memory[(addr+2) % MEMSIZE] = getByte(2, temp);
					memory[(addr+3) % MEMSIZE] = getByte(3, temp);
				}
				else{
					registers[base] = lowWord(temp);
					registers[(base+1) % REGSIZE] = highWord(temp);
				}
				break;
			}
			case RDBD:{ // RDBD binary digit (into the high bit of dest, leaving rest intact ???)
				try{
					char ch = sysin.next(Pattern.compile("[01]")).charAt(0); 
					if(ch == '0'){
						if(addr >= 0){
							assureBytesAt(addr, 2);
							memory[(addr + 1) % MEMSIZE].clear(7);
						}
						else{
							registers[base].clear(15);
						}
					}
					else if(ch == '1'){
						if(addr >= 0){
							assureBytesAt(addr, 2);
							memory[(addr + 1) % MEMSIZE].set(7);
						}
						else{
							registers[base].set(15);
						}
					}
				}
				catch (InputMismatchException e) {
					System.out.println(" Invalid input for RDBD " +  (programCounter - 2));
					System.out.println("next up is " + sysin.next());
				}
				break;
			}
			case RDBW:{ // RDBW binary word (Must be 16 bits)
				int i = 15;
				boolean isValid = true;
				BitSet word = new BitSet(16);
				Pattern old = sysin.delimiter();
				sysin.useDelimiter(Pattern.compile("[^01]"));
				String next = sysin.next(); //must be 16 binary digits
				sysin.useDelimiter(old);
				while(i >= 0 && isValid){
					try{
						char ch = next.charAt(15-i);
						if(ch == '1'){
							word.set(i);
						}
						else if(ch == '0'){ // redundant in a new bitset
							word.clear(i);
						}
					}
					catch (Exception e) {
						System.out.println(" Invalid input for RDBW " +  (programCounter - 2));
						isValid = false;
						stopProgram = true;
					}
					i -= 1;
				}
				if(isValid){
					if(addr >= 0){
						assureBytesAt(addr, 2);
						bits16toBytes(word, memory[addr], memory[(addr + 1) % MEMSIZE]);
					}
					else{
						registers[base] = word;
					}
				}
				break;
			}
			case RDOD: { //rdod writes into the low end 3 bits, leaving rest wiped ???)
				String ch = sysin.next(Pattern.compile("[0-7]"));
				sysin.reset();
				short value = (short)Integer.parseInt(ch);
				value = (short)(value);
				shortToMemoryWord(value, addr);
//				BitSet temp = shortToBits16(value);
//				if(addr >= 0){
//					assureBytesAt(addr, 2);
//					bits16toBytes(temp, memory[addr], memory[(addr + 1) % MEMSIZE] );
//				} 
//				else{
//					registers[base] = temp;
//				}
				break;
			}
			case RDOW: { //RDOW 6 octal digits, left most is 0 or 1
				Pattern old = sysin.delimiter();
				sysin.useDelimiter(Pattern.compile("\\B|\\s"));
				String ch = sysin.next(Pattern.compile("[01]"));
				short value = (short)Integer.parseInt(ch);
				value = (short)(value << 15);
				for(int i = 0; i < 5; ++i){
					ch = sysin.next(Pattern.compile("[01234567]"));
					short nValue = (short)Integer.parseInt(ch);
					nValue = (short)(nValue << 3*(4 - i));
					value += nValue;
				}
				sysin.useDelimiter(old);
				shortToMemoryWord(value, addr);
				break;
			}
			case RDHD: { //RDHD (reads into the low four bits of the dest, wiping the rest ???}
				String ch = sysin.next(Pattern.compile("[0-9a-fA-F]"));
				sysin.reset();
				short value = (short)Integer.parseInt(ch, 16);
				shortToMemoryWord(value, addr);
//				BitSet temp = shortToBits16(value);
//				if(addr >= 0){
//					assureBytesAt(addr, 2);
//					bits16toBytes(temp, memory[addr], memory[(addr + 1) % MEMSIZE] );
//				} 
//				else{
//					registers[base] = temp;
//				}
				
				break;
			}
			case RDHW: { //RDHW 4 hex digits
				Pattern old = sysin.delimiter();
				sysin.useDelimiter(Pattern.compile("\\B|\\s"));
				short value = 0;
				for(int i = 0; i < 4; ++i){
					String ch = sysin.next(Pattern.compile("[0-9a-fA-F]"));
					short nValue = (short)Integer.parseInt(ch, 16);
					nValue = (short)(nValue << 4*(3 - i));
					value |= nValue;
				}
				sysin.useDelimiter(old);
				shortToMemoryWord(value, addr);
				break;
			}
			case RDCH: { // rdch (reads into the HIGH byte of the dest ???)
				BitSet temp;
				if(addr >= 0){
					assureBytesAt(addr, 2);
					temp = bytesToBits16(memory[addr], memory[(addr + 1) % MEMSIZE]);
				}
				else{
					temp = registers[base];
				}
				Pattern old = sysin.delimiter();
				sysin.useDelimiter(Pattern.compile("\\B|\\s"));
				char ch = sysin.next().charAt(0); 
				sysin.useDelimiter(old);
				BitSet aByte = byteToBits8((byte)ch); 
				setByte(1, temp, aByte);
				if(addr >= 0){
					bits16toBytes(temp, memory[addr], memory[(addr + 1) % MEMSIZE]);
				}
				else{
					registers[base] = temp;
				}
				break;
			}
			case RDST: {//rdst (reads a string from a full line and puts it into memory null terminated)
				if(addr < 0){
					System.out.println(" Invalid address in RDST " + (programCounter - 2));
					stopProgram = true;
					break;
				}
				Pattern old = sysin.delimiter();
				sysin.useDelimiter(Pattern.compile("\n|\r"));
				String line = sysin.next();
				sysin.useDelimiter(old);
				char lastch = 0;
				char first, second;
				boolean isEven = line.length() % 2 == 0;
				if(!isEven){
					lastch = line.charAt(line.length() - 1);
					line = line.substring(0, line.length() - 1);
				}
				int next = 0;
				while(next < line.length()){
					first = line.charAt(next);
					second = line.charAt(next + 1);
					BitSet bits = charsToBits16(first, second);
					assureBytesAt(addr, 2);
					memory[addr] = getByte(1, bits);
					memory[(addr + 1) % MEMSIZE] = getByte(0, bits);
					addr += 2;
					next +=2;
				}
				if(!isEven){
					first = lastch;
					second = 0;
				}
				else{
					first = 0;
					second = 0;					
				}
				BitSet bits = charsToBits16(first, second);
				assureBytesAt(addr, 2);
				memory[addr] = getByte(1, bits);
				memory[(addr + 1) % MEMSIZE] = getByte(0, bits);
				addr += 2;
				break;
			}
			case RDNL:{ // rdnl
				String ignore = sysin.nextLine();
				break;
			}
			default:{
				System.out.println(" Read mode " + readMode + " not implemented. Exiting.");
				System.exit(2);
			}
			}
			
		}
		else{
			System.out.println(" Illegal Read mode detected at " + (programCounter - 2));
			stopProgram = true;
		}			
	}
	
	/** All of the output instructions 
	 * @param writeMode which instruction wri(0) wrf(1), etc
	 * @param mode the mode of the locations(s) to be written
	 * @param base the base of the location
	 * @param displacement the displacement if any
	 */
	public void writeOperation(int writeMode, int mode, int base, int displacement){
		if(writeMode >= 0 && writeMode <= 11){
			switch(writeMode){
			case WRI:{ //wri
				BitSet temp = locationAsBits(mode, base, displacement);
				System.out.print(bits16toShort(temp));
				break;
			}
			case WRF:{ //wrf
				Float fValue = doubleWordAsFloat(mode, base, displacement);
				System.out.print("" + fValue);
				break;
			}
			case WRBD:{ //WRBD binary digit ( Writes the high bit of the source ???)
				BitSet temp = locationAsBits(mode, base, displacement);
//				if(addr >= 0){
//					assureBytesAt(addr, 2);
//					temp = bytesToBits16(memory[addr], memory[(addr + 1) % MEMSIZE]);
//				}
//				else{
//					temp = registers[base];
//				}
				if(temp.get(15)){
					System.out.print('1');
				}
				else{
					System.out.print('0');
				}
				break;
			}
			case WRBW:{ ///WRBW binary word 
				BitSet temp = locationAsBits(mode, base, displacement);
				System.out.print(bitString(temp));
				break;
			}
			case WROD: {//wrod (writes the low order three bits of the source)
				BitSet temp = locationAsBits(mode, base, displacement);
//				if(addr >= 0){
//					assureBytesAt(addr, 2);
//					temp = bytesToBits16(memory[addr], memory[(addr +1) % MEMSIZE]);
//				}
//				else{
//					temp = registers[base];
//				}
				BitSet bValue = new BitSet(8);
				for(int i = 0; i < 3; ++i){
					if(temp.get(i)){
						bValue.set(i);
					}
				}
				System.out.print(bits8toByte(bValue));
				break;
			}
			case WROW: {//wrow
				BitSet temp = locationAsBits(mode, base, displacement);
				String sValue = "";
				if(temp.get(15)){
					sValue += "1";
				}
				else{
					sValue += "0";
				}
				for(int i = 4; i >= 0; --i){
					BitSet nibble = new BitSet(8);
					for(int j = 0; j < 3; ++j){
						if(temp.get(3*i+j)){
							nibble.set(j);
						}
					}
					sValue += bits8toByte(nibble);
				}
				System.out.print(sValue);
				break;
			}
			case WRHD: { // WRHD (writes the low order four bits of the source)
				char [] values = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
				BitSet temp = locationAsBits(mode, base, displacement);
//				if(addr >= 0){
//					assureBytesAt(addr, 2);
//					temp = bytesToBits16(memory[addr], memory[(addr +1) % MEMSIZE]);
//				}
//				else{
//					temp = registers[base];
//				}
				BitSet bValue = new BitSet(8);
				for(int i = 0; i < 4; ++i){
					if(temp.get(i)){
						bValue.set(i);
					}
				}
				System.out.print(values[bits8toByte(bValue)]);
				break;
			}
			case WRHW: { // WRHW
				char [] values = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
				BitSet temp = locationAsBits(mode, base, displacement);
				String sValue = "";
				for(int i = 3; i >= 0; --i){
					BitSet bValue = new BitSet(8);
					for(int j = 0; j < 4; ++j){
						if(temp.get(4*i + j)){
							bValue.set(j);
						}
					}
					sValue += values[bits8toByte(bValue)];
				}
				System.out.print(sValue);
				break;
			}
			case WRCH: { //wrch (NOTE Write the high byte
				BitSet temp = locationAsBits(mode, base, displacement);
//				if(addr >= 0){
//					assureBytesAt(addr, 2);
//					temp = bytesToBits16(memory[addr], memory[(addr +1) % MEMSIZE]);
//				}
//				else{
//					temp = registers[base];
//				}
				char ch = (char)bits8toByte(highByte(temp));
				System.out.print(ch);	
				break;
			}
			case WRST:{ // wrst
				char ch = ' ';
				int addr = effectiveAddress(mode, base, displacement);
				while(ch != 0){
					BitSet temp;
					if(addr >= 0){
						this.assureBytesAt(addr, 2);
						temp = bytesToBits16(memory[addr], memory[(addr + 1) % MEMSIZE]);
						addr = (addr + 2) % MEMSIZE;						
					}
					else{
						temp = registers[base];
						base = (base + 1) % REGSIZE;
					}
					if(bits16highToChar(temp) != 0){
						System.out.print(bits16highToChar(temp));
					}
					else{
						ch = 0;
					}
					if(ch != 0 && bits16lowToChar(temp) != 0){
						System.out.print(bits16lowToChar(temp));
					}
					else{
						ch = 0;
					}
				}
				break;
			}
			case WRNL:{//wrnl
				System.out.println();
				break;
			}
			default:{
				System.out.println(" Write mode " + writeMode + " not implemented. Exiting.");
				System.exit(2);
			}
			}
		}
		else{
			System.out.println(" Illegal Write mode detected at " + (programCounter - 2));
			stopProgram = true;
		}			
	}
	
	private static final int PUSH = 1, POP = 2; // modes above 2 are unused. 
	
	/** Reresents instruction 32 (HALT, PUSH, POP)
	 * @param reg the stack register in PUSH and POP
	 * @param mode which instruction HALT(0) PUSH(1) or POP(2)
	 * @param base ignored
	 * @param displacement the register set to be pushed or popped as a bitset
	 */
	public void systemOperation(int reg, int mode, int base, int displacement){
		if(mode == 0){
			stopProgram = true;
		}
		else{
			int addr = effectiveAddress(IMMED, 0, displacement);
			BitSet registersToMove = bytesToBits16(memory[addr],memory[ (addr + 1) % MEMSIZE]);
			displacement = bits16toShort(registersToMove);
			if(mode == PUSH){ // push
				for(int i = 0; i < 16; ++i){
					if(registersToMove.get(i)){
						int point = (bits16toShort(registers[reg]) - 2) % MEMSIZE;
						registers[reg] = shortToBits16((short)(point));
						twoAddressOperation(STO, i, IREG, reg, 0);
					}
				}
			}
			else if(mode == POP){ // pop
				for(int i = 15; i >= 0; --i){
					if(registersToMove.get(i)){
						twoAddressOperation(LD, i, IREG, reg, 0);
						int point = (bits16toShort(registers[reg]) + 2) % MEMSIZE;
						registers[reg] = shortToBits16((short)(point));
	
					}
				}
			}
			else{
				System.out.println("Invalid System op mode detected " + (programCounter - 1));
				stopProgram = true;
			}
		}
	}

	/**
	 * Execute a single instruction. 
	 */
	public void execute(){
		int opCode = opCode(instructionRegister);
		int reg = reg(instructionRegister);
		int mode = mode(instructionRegister);
		int base = base(instructionRegister);
		int displacement = 0;
		switch(opCode){
			case INEG:
			case IA:
			case IS:
			case IM:
			case ID:
			case FN:
			case FA:
			case FS:
			case FM:
			case FD:
			case BI:
			case BO:
			case BA:
			case IC:
			case FC:
			case JSR:
			case BKT:
			case LD:
			case STO:
			case LDA:
			case FIX:
			case FLT:
			case TRNG:
			{
				twoAddressOperation(opCode, reg, mode, base, displacement);
				break;
			}
			case J:{
				jumpOperation(reg, mode, base, displacement);
				break;
			}
			case SR:{
				shiftRightOperation(reg, mode, base);
				break;
			}
			case SL:{
				shiftLeftOperation(reg, mode, base);
				break;
			}
			case RD:{
				readOperation(reg, mode, base, displacement);
				break;
			}
			case WR:{
				writeOperation(reg, mode, base, displacement);
				break;
			}
			case HALT:{
				if(mode != 1 && mode != 2){
					stopProgram = true;
				}
				else{
					systemOperation(reg, mode, base, displacement);
				}
				break;
			}
			case INC:
			case DEC:{
				incDecOperation(opCode,reg, mode, base, displacement);
				break;
			}
			default:{
				System.out.println("Bad opcode at: " + (programCounter-2));
				stopProgram = true;
				break;
			}
		}
		if(trace){
			displacement = rememberedDisplacement();
			System.out.println(" -pc--opcode----reg-----mode---base---displacement----");
			System.out.println("   " + oldpc + "    " + opCode + "       " + reg 
					+ "       " + mode + "      " + base + "      " + displacement);
			writeInstruction(System.out, oldpc, opCode, reg, mode, base, displacement);
			if(showRegisters){
				displayRegisters();
			}
			System.out.println(" --------------------------------");
		}
		if(dump){
			writeInstruction(dumpfile, oldpc, opCode, reg, mode, base, displacement);
		}
	}
	
	/** Create a machine and point it at an object file
	 * @param filename the OBJ file to be read
	 * @param trace should the machine pause after every instruction is executed
	 * @param showRegisters should it show all registers on every trace step
	 * @param dump should it dump the executed instructions to the dumpf.txt file
	 * @throws IOException  if a file can't be read or written
	 */
	public Macc3(String filename, boolean trace, boolean showRegisters, boolean dump) throws IOException{
		this.trace = trace;
		this.dump = dump;
		this.showRegisters = showRegisters;
		sysin = new Scanner(System.in);
		readInputFile(filename);
		if(!memImage){
			System.out.println("No memory file read. Exiting");
			System.exit(1);
		}
		if(dump){
			dumpfile = new PrintStream("dumpf.txt");
		}
	}
	
	/**
	 * This is the machine's execution loop
	 */
	public void run(){
		for(int i = 0; i < REGSIZE; ++i){
			registers[i] = new BitSet(16);
		}
		programCounter = 0;
		do{
			oldpc = programCounter;
			instructionRegister = fetchWord();
			execute();
			if(trace){
				System.out.println( " Press return to continue. ");
				sysin.nextLine();
			}
		}while(!stopProgram);
		
	}
	
	/**Initialize a Macc and send it the inputfile and the pragmas T(race), D(ump) and R(egister dumps in traces).
	 * The machine's standard input is System.in and its standard output is System.out. The default input
	 * filename is "OBJ" and the flags are off. If T is on, the machine will halt after each statement is 
	 * executed, show you what was done and require an <enter> to resume. 
	 * 
	 * @param args inputFilename and flags /TDR in any order
	 * @throws IOException if the OBJ file can't be found or read
	 */
	public static void main(String[] args) throws IOException {
		boolean trace = false;
		boolean dump = false;
		boolean showRegisters = false;
		String filename = "OBJ";
		for (int i = 0; i < args.length; ++i) {
			if (args[i].charAt(0) == '-'){
				for (int j = 1; j < args[i].length(); ++j){
					switch (Character.toUpperCase(args[i].charAt(j))) {
					case 'T':
						trace = true;
						break;
					case 'D':
						dump = true;
						break;
					case 'R':
						showRegisters = true;
						break;
					default:
						System.out.println("Invalid option "
								+ args[i].charAt(j) + ": Only T,R,D allowed\n");
						System.exit(1);
					}
				}
			}
			else{
				File temp = new File(args[i]);
				if(temp.exists()){
					filename = args[i];
				}
				else {
					System.out.println("No file named: " + args[i]);
					System.exit(1);	
				}
			}
		}
		Macc3 macc = new Macc3(filename, trace, showRegisters, dump);
		macc.run();	
		
	}
	
/* What follows are the bit manipulation methods. Not all of them are used and the unused ones
 * may not have been tested. 
 * 
 */
	
	private static String bitString(BitSet set){ // 16 bit sets only
		String result = "";
		for(int i = 15; i >= 0; --i){
			if(set.get(i)){
				result += "1";
			}
			else{
				result += "0";
			}
		}
		return result;
	}
//	
//	private static BitSet toBitSet(int code){ // 16 bit sets only
//		BitSet result = new BitSet(16);
//		for(int i = 0; i < 16; ++i){
//			if(Math.abs(code % 2) == 1){
//				result.set(i);
//			}
//			code = code >> 1;
//		}
//		return result;
//	}
//	
//	private static void setBits(BitSet bits, int fromBit, int toBit, int value){ 
//		// bits must have sufficient "size"
//		// low bits of value to low bits of from--to. value >= 0
//		for(int i = fromBit; i <= toBit; ++i){
//			if(Math.abs(value % 2) == 1){
//				bits.set(i);
//			}
//			value = value >> 1;			
//		}
//	}
//	
//	private static void setOpCode(BitSet bits, int value){
//		setBits(bits, 11, 15, value);
//	}
//	
//	private static void setRegister(BitSet bits, int value){
//		setBits(bits, 7, 10, value);
//	}
//	
//	private static void setReadWriteType(BitSet bits, int value){
//		setRegister(bits, value);
//	}
//	
//	private static void setJumpMode(BitSet bits, int value){
//		setRegister(bits, value);
//	}
//	
//	private static void setMode(BitSet bits, int value){
//		setBits(bits, 4, 6, value);
//	}
//	
//	private static void setShiftType(BitSet bits, int value){
//		setMode(bits, value);
//	}
//	
//	private static void setShiftAmount(BitSet bits, int value){
//		setBase(bits, value);
//	}
//	
//	private static void setBase(BitSet bits, int value){
//		setBits(bits, 0, 3, value);		
//	}
//	
//	private int parseInt(String anIntString){
//		if(anIntString.charAt(0) == '+'){
//			return Integer.parseInt(anIntString.substring(1));
//		}else{
//			return Integer.parseInt(anIntString);
//		}	
//	}

	/** Pull the bits of a byte into a BitSet(8)
	 * @param value the byte whose bits we need
	 * @return the equivalent BitSet(8)
	 */
	public static BitSet byteToBits8(byte value){
		BitSet storage = new BitSet(8);
		if(value < 0){
			storage.set(7);
			value = (byte)(value & (byte)MAXBYTE);
		}
		for(int i = 0; i < 7; ++i){
			//				System.out.println(value);
			if( value % 2 == 1){
				storage.set(i);
			}
			value >>= 1;
		}
		return storage;
	}

	/** Extract the byte value of a BitSet(8)
	 * @param bits the bitset
	 * @return the equivalent byte value
	 */
	public static byte bits8toByte(BitSet bits){
		BitSet storage = bits;
		byte result = 0;
		for(int i = 6 ; i >=0; --i){
			result <<= 1;
			if(storage.get(i)){
				result += 1;
			}
		}
		if(storage.get(7)){
			result = (byte)(result | MINBYTE);
		}
		return result;
	}

	/** Pull the bits of a short into a BitSet(16)
	 * @param value the short whose bits we need
	 * @return The equivalent BitSet
	 */
	public static BitSet shortToBits16(short value){
		BitSet storage = new BitSet(16);
		if(value < 0){
			storage.set(15);
			value = (short)(value & (short)MAXINT);
		}
		for(int i = 0; i < 15; ++i){
			//				System.out.println(value);
			if( value % 2 == 1){
				storage.set(i);
			}
			value >>= 1;
		}
		return storage;
	}

	/** Extract a short from a BitSet(16)
	 * @param bits the bitset
	 * @return the equivalent short value
	 */
	public static short bits16toShort(BitSet bits){
		BitSet storage = bits;
		short result = 0;
		for(int i = 14 ; i >=0; --i){
			result <<= 1;
			if(storage.get(i)){
				result += 1;
			}
		}
		if(storage.get(15)){
			result = (short)(result | MININT);
		}
		return result;
	}

	private static BitSet intToBits32(int value){
		BitSet storage = new BitSet(32);
		if(value < 0){
			storage.set(31);
			value = (value & MAXFULLINT); // 2^31 - 1 (wipe out sign bit)
		}
		for(int i = 0; i < 31; ++i){
			//				System.out.println(value);
			if( value % 2 == 1){
				storage.set(i);
			}
			value >>= 1;
		}
		return storage;
	}

	private static int bits32toInt(BitSet bits){
		BitSet storage = bits;
		int result = 0;
		for(int i = 30 ; i >=0; --i){
			result <<= 1;
			if(storage.get(i)){
				result += 1;
			}
		}
		if(storage.get(31)){
			result = (result | MINFULLINT);
		}
		return result;
	}

	private static BitSet floatToBits32(float value){
		int bits = Float.floatToIntBits(value);
		return  intToBits32(bits);
	}

	private static float floatFromBits(BitSet low, BitSet high){
		BitSet temp = bits16doubleToBits32(low, high);
		int iValue = bits32toInt(temp);
		return Float.intBitsToFloat(iValue);
	}

	private static float floatFromBits(BitSet low, BitSet b2, BitSet b3, BitSet high){
		BitSet temp = bytes4toBits32(low, b2, b3, high);
		int iValue = bits32toInt(temp);
		return Float.intBitsToFloat(iValue);
	}

	private static BitSet charsToBits16(char c1, char c2){ 
		BitSet storage = new BitSet(16);
		for(int i = 0; i < 8; ++i){
			if(c1 % 2 == 1){
				storage.set(i);
			}
			if(c2 % 2 == 1){
				storage.set(i + 8);
			}
			c1 >>= 1;
			c2 >>= 1;
		}
		return storage;
	}

	private static char bits16highToChar(BitSet storage){
		char c = 0;
		for(int i = 7; i >= 0; --i){
			c <<= 1;
			if(storage.get(i + 8)){
				c += 1;
			}				
		}
		return c;
	}

	private static char bits16lowToChar(BitSet storage){
		char c = 0;
		for(int i = 7; i >= 0; --i){
			c <<= 1;
			if(storage.get(i)){
				c += 1;
			}				
		}
		return c;
	}

	private BitSet bytesToBits16(BitSet lobyte, BitSet hibyte){
		BitSet result = new BitSet(16);
		for(int i = 0; i < 8; ++i){
			if(lobyte.get(i)){
				result.set(i);
			}
			if(hibyte.get(i)){
				result.set(i + 8);
			}
		}
		return result;
	}

	private static void bits16toBytes(BitSet word, BitSet low, BitSet high){
		low.clear();
		high.clear();
		for(int i = 0; i < 8; ++i){
			if(word.get(i)){
				low.set(i);
			}
			if(word.get(i + 8)){
				high.set(i);
			}
		}
	}

	private static BitSet bytes4toBits32(BitSet low, BitSet b2, BitSet b3, BitSet high){// swap words
		BitSet result = new BitSet(32);
		for(int i = 0; i < 8; ++i){
			if(b3.get(i)){
				result.set(i);
			}
		}
		for(int i = 0; i < 8; ++i){
			if(high.get(i)){
				result.set(8+i);
			}
		}
		for(int i = 0; i < 8; ++i){
			if(low.get(i)){
				result.set(16+i);
			}
		}
		for(int i = 0; i < 8; ++i){
			if(b2.get(i)){
				result.set(24+i);
			}
		}
		return result;
	}

	private static BitSet bits16doubleToBits32(BitSet low, BitSet high){
		BitSet result = new BitSet(32);
		for(int i = 0; i < 16; ++i){
			if(low.get(i)){
				result.set(i);
			}
		}
		for(int i = 0; i < 16; ++i){
			if(high.get(i)){
				result.set(16+i);
			}
		}
		return result;
	}

	private static BitSet highWord(BitSet bits){
		BitSet result = new BitSet(16);
		for(int i = 16; i < 32; ++i){
			if(bits.get(i)){
				result.set(i - 16);
			}
		}
		return result;
	}

	private static BitSet lowWord(BitSet bits){
		BitSet result = new BitSet(16);
		for(int i = 0; i < 16; ++i){
			if(bits.get(i)){
				result.set(i);
			}
		}
		return result;
	}

	private static BitSet getByte(int byteNum, BitSet bits){
		int bitNum = byteNum * 8;
		BitSet result = new BitSet(8);
		for(int i = bitNum; i < bitNum + 8; ++i){
			if(bits.get(i)){
				result.set(i - bitNum);
			}
		}
		return result;
	}
	
	private static void setByte(int byteNum, BitSet bits, BitSet value){
		int bitNum = byteNum * 8;
		for(int i = bitNum; i < bitNum + 8; ++i){
			if(value.get(i - bitNum)){
				bits.set(i);
			}
			else{
				bits.clear(i);
			}
		}
	}

//	private static short swapBytes(short value){
//		BitSet storage = Macc3.shortToBits16(value);
//		char c1 = Macc3.bits16lowToChar(storage);
//		char c2 = Macc3.bits16highToChar(storage);
//		storage = chars(c2, c1);
//		return Macc3.bits16toShort(storage);
//	}

}
