package gcl;

import gcl.Codegen.*;
import gcl.SemanticActions.GCLErrorStream;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import java.io.PrintWriter;

//-------------------- Semantic Records ---------------------
/**
 * This interface is implemented by all semantic Item classes that represent
 * semantic errors. It permits a simple test to filter out all error objects
 * that appear in various semantic routines. The pattern is 'tag interface'.
 */
interface GeneralError { /* Nothing */
}

/**
 * Root of the semantic "record" hierarchy. All parameters of parsing functions
 * and semantic functions are objects from this set of classes.
 */
abstract class SemanticItem {
	// Note: Only expressions and procedures need semanticLevel, but this is the
	// only common ancestor class.
	private int level = -9; // This value should never appear

	public String toString() {
		return "Unknown semantic item. ";
	}
	
	/**
	 * Soft Cast
	 * @return "this" if it is a type and a NO_TYPE otherwise.
	 */
	public TypeDescriptor expectTypeDescriptor(final GCLErrorStream err) {
		err.semanticError(GCLError.TYPE_REQUIRED);
		return ErrorType.NO_TYPE;
	}
	
	/**
	 * Soft Cast
	 * @return "this" if it is an expression and an ErrorExpression otherwise.
	 */
	public Expression expectExpression(final GCLErrorStream err) {
		err.semanticError(GCLError.EXPRESSION_REQUIRED);
		return new ErrorExpression("$ Expression Required");
	}
	
	/**
	 * Soft Cast
	 * @return "this" if it is a module record and an ErrorModuleRecord otherwise.
	 */
	public ModuleRecord expectModuleRecord(final GCLErrorStream err) {
		err.semanticError(GCLError.MODULE_REQUIRED);
		return new ErrorModuleRecord("$ Module Required");
	}

	/**
	 * Soft Cast
	 * @return "this" if it is a module record and an ErrorModuleRecord otherwise.
	 */
	public Procedure expectProcedure(final GCLErrorStream err) {
		err.semanticError(GCLError.PROCEDURE_REQUIRED);
		return new ErrorProcedure("$ Procedure Required");
	}
	
	public int semanticLevel() {
		return level;
	}

	public SemanticItem() {
	}

	public SemanticItem(final int level) {
		this.level = level;
	}
}

/**
 * A general semantic error. There are more specific error classes also. Immutable.
 */
class SemanticError extends SemanticItem implements GeneralError {
	public SemanticError(final String message) {
		this.message = message;
		CompilerOptions.message(message);
	}

	public Expression expectExpression(final GCLErrorStream err) {
		// Don't complain on error records. The complaint previously occurred when this object was created.
		return new ErrorExpression("$ Expression Required");
	}
	
	public TypeDescriptor expectTypeDescriptor(final GCLErrorStream err) {
		// Don't complain on error records. The complaint previously occurred when this object was created.
		return ErrorType.NO_TYPE;
	}
	
	public ModuleRecord expectModuleRecord(final GCLErrorStream err) {
		// Don't complain on error records. The complaint previously occurred when this object was created.
		return new ErrorModuleRecord("$ Module Required");
	}

	public String toString() {
		return message;
	}

	private final String message;
}

/**
 * An object to represent a user defined identifier in a gcl program. Immutable.
 */
class Identifier extends SemanticItem {
	public Identifier(final String value) {
		this.value = value;
	}
	
	public static boolean isValid(final String value){
		return (-1 == value.indexOf("__"));
	}

	public String name() {
		return value;
	}

	public String toString() {
		return value;
	}

	public int hashCode() {
		return value.hashCode();
	}

	public boolean equals(Object o) {
		return (o instanceof Identifier)
				&& value.equals(((Identifier) o).value);
	}

	private final String value;
}

/** Root of the operator hierarchy */
abstract class Operator extends SemanticItem implements Mnemonic {
	
	public Operator(final String op, final SamOp opcode) {
		value = op;
		this.opcode = opcode;
	}

	public String toString() {
		return value;
	}

	public final SamOp opcode() {
		return opcode;
	}
	
	/**
	 * Tells the caller if the operands are valid and complains if necessary.
	 * NOTE: It is then the callers responsibility to return an ErrorExpression.
	 * 
	 * @param left operand to the left.
	 * @param right operand to the right.
	 * @param err GCLErrorStream on which to complain. 
	 * @return true if the operands are valid; false otherwise.
	 */
	public abstract boolean validOperands(Expression left, Expression right, GCLErrorStream err);
	/**
	 * @param left operand to the left.
	 * @param right operand to the right
	 * @return a new resultant constant expression computed during compile time.
	 */
	public abstract ConstantExpression constantFolding(ConstantExpression left, ConstantExpression right);

	private final String value;
	private final SamOp opcode;
}

/**
 * Relational operators such as = and # Typesafe enumeration pattern as well as
 * immutable
 */
abstract class RelationalOperator extends Operator {
	
	public static final RelationalOperator EQUAL = new RelationalOperator("equal", JEQ){
		public ConstantExpression constantFolding(ConstantExpression left, ConstantExpression right){
			return new ConstantExpression(BooleanType.BOOLEAN_TYPE, (((ConstantExpression)left).value() == ((ConstantExpression)right).value()) ? 1: 0);
		}
		public boolean validOperands(Expression left, Expression right, GCLErrorStream err){// may operate on all types
			if(left instanceof GeneralError || right instanceof GeneralError){
				return false;
			}
			if(!left.type().isCompatible(right.type())){
				err.semanticError(GCLError.TYPE_MISMATCH, "Equal operator expected same type");
				return false;
			}
			return true;
		}
	};
	public static final RelationalOperator NOT_EQUAL = new RelationalOperator("notequal", JNE){
		public ConstantExpression constantFolding(ConstantExpression left, ConstantExpression right){
			return new ConstantExpression(BooleanType.BOOLEAN_TYPE, (((ConstantExpression)left).value() != ((ConstantExpression)right).value()) ? 1: 0);
		}
		public boolean validOperands(Expression left, Expression right, GCLErrorStream err){// may operate on all types
			if(left instanceof GeneralError || right instanceof GeneralError){
				return false;
			}
			if(!left.type().isCompatible(right.type())){
				err.semanticError(GCLError.TYPE_MISMATCH, "Not Equal operator expected same type");
				return false;
			}
			return true;
		}
	};
	public static final RelationalOperator GREATER = new RelationalOperator("greater", JGT){
		public ConstantExpression constantFolding(ConstantExpression left, ConstantExpression right){
			return new ConstantExpression(BooleanType.BOOLEAN_TYPE, (((ConstantExpression)left).value() > ((ConstantExpression)right).value()) ? 1: 0);
		}
		public boolean validOperands(Expression left, Expression right, GCLErrorStream err){
			if(left instanceof GeneralError || right instanceof GeneralError){
				return false;
			}
			if(!operandsIntIntOrBoolBool(left, right)){
				err.semanticError(GCLError.TYPE_MISMATCH, "Greater operator expected same type");
				return false;
			}
			return true;
		}
	};
	public static final RelationalOperator GREATER_OR_EQUAL = new RelationalOperator("greaterorequal", JGE){
		public ConstantExpression constantFolding(ConstantExpression left, ConstantExpression right){
			return new ConstantExpression(BooleanType.BOOLEAN_TYPE, (((ConstantExpression)left).value() >= ((ConstantExpression)right).value()) ? 1: 0);
		}
		public boolean validOperands(Expression left, Expression right, GCLErrorStream err){
			if(left instanceof GeneralError || right instanceof GeneralError){
				return false;
			}
			if(!operandsIntIntOrBoolBool(left, right)){
				err.semanticError(GCLError.TYPE_MISMATCH, "GreaterEqual operator expected same type");
				return false;
			}
			return true;
		}
	};
	public static final RelationalOperator LESS = new RelationalOperator("less", JLT){
		public ConstantExpression constantFolding(ConstantExpression left, ConstantExpression right){
			return new ConstantExpression(BooleanType.BOOLEAN_TYPE, (((ConstantExpression)left).value() < ((ConstantExpression)right).value()) ? 1: 0);
		}
		public boolean validOperands(Expression left, Expression right, GCLErrorStream err){
			if(left instanceof GeneralError || right instanceof GeneralError){
				return false;
			}
			if(!operandsIntIntOrBoolBool(left, right)){
				err.semanticError(GCLError.TYPE_MISMATCH, "Less operator expected same type");
				return false;
			}
			return true;
		}
	};
	public static final RelationalOperator LESS_OR_EQUAL = new RelationalOperator("lessorequal", JLE){
		public ConstantExpression constantFolding(ConstantExpression left, ConstantExpression right){
			return new ConstantExpression(BooleanType.BOOLEAN_TYPE, (((ConstantExpression)left).value() <= ((ConstantExpression)right).value()) ? 1: 0);
		}
		public boolean validOperands(Expression left, Expression right, GCLErrorStream err){
			if(left instanceof GeneralError || right instanceof GeneralError){
				return false;
			}
			if(!operandsIntIntOrBoolBool(left, right)){
				err.semanticError(GCLError.TYPE_MISMATCH, "LessEqual operator expected same type");
				return false;
			}
			return true;
		}
	};
	
	private static boolean operandsIntIntOrBoolBool(Expression left, Expression right){
		return (left.type().isCompatible(IntegerType.INTEGER_TYPE) && right.type().isCompatible(IntegerType.INTEGER_TYPE)) ||
			   (left.type().isCompatible(BooleanType.BOOLEAN_TYPE) && right.type().isCompatible(BooleanType.BOOLEAN_TYPE));
	}

	private RelationalOperator(final String op, final SamOp opcode) {
		super(op, opcode);
	}
}

/**
 * Add operators such as + and - Typesafe enumeration pattern as well as
 * immutable
 */
abstract class AddOperator extends Operator {
	public static final AddOperator PLUS = new AddOperator("plus", IA){
		public ConstantExpression constantFolding(ConstantExpression left, ConstantExpression right){
			return new ConstantExpression(IntegerType.INTEGER_TYPE, left.value() + right.value());
		}
	};
	public static final AddOperator MINUS = new AddOperator("minus", IS){
		public ConstantExpression constantFolding(ConstantExpression left, ConstantExpression right){
			return new ConstantExpression(IntegerType.INTEGER_TYPE, left.value() - right.value());
		}
	};
	
	public boolean validOperands(Expression left, Expression right, GCLErrorStream err){
		if(left instanceof GeneralError || right instanceof GeneralError){
			return false;
		}
		if(!(left.type().isCompatible(IntegerType.INTEGER_TYPE) && right.type().isCompatible(IntegerType.INTEGER_TYPE))){
			err.semanticError(GCLError.INTEGER_REQUIRED, "AddOperator expected integers");
			return false;
		}
		return true;
	}

	private AddOperator(final String op, final SamOp opcode) {
		super(op, opcode);
	}
}

/**
 * Multiply operators such as * and / Typesafe enumeration pattern as well as immutable
 */
abstract class MultiplyOperator extends Operator {
	public static final MultiplyOperator TIMES = new MultiplyOperator("times", IM){
		public ConstantExpression constantFolding(ConstantExpression left, ConstantExpression right){
			return new ConstantExpression(IntegerType.INTEGER_TYPE, left.value() * right.value());
		}
	};
	public static final MultiplyOperator DIVIDE = new MultiplyOperator("divide", ID){
		public ConstantExpression constantFolding(ConstantExpression left, ConstantExpression right){
			return new ConstantExpression(IntegerType.INTEGER_TYPE, left.value() / right.value());
		}
	};
	public static final MultiplyOperator MODULO = new MultiplyOperator("modulo", ID /* <-- ID is a dummy value*/ ){
		public ConstantExpression constantFolding(ConstantExpression left, ConstantExpression right){
			return new ConstantExpression(IntegerType.INTEGER_TYPE, left.value() % right.value());
		}
	};
	//
	// The samCode field of the MODULO MultiplyOperator uses a dummy
	// value to conform to the structure of the Operator superclass.
	//
	// The multiplyExpression checks to see if MODULO is being passed, and
	// executes modulusExpression instead (does not use MODULUS.samCode)
	//
	
	public boolean validOperands(Expression left, Expression right, GCLErrorStream err){
		if(left instanceof GeneralError || right instanceof GeneralError){
			return false;
		}
		if(!(left.type().isCompatible(IntegerType.INTEGER_TYPE) && right.type().isCompatible(IntegerType.INTEGER_TYPE))){
			err.semanticError(GCLError.INTEGER_REQUIRED, "MultiplyOperator expected integers");
			return false;
		}
		return true;
	}

	private MultiplyOperator(final String op, final SamOp opcode) {
		super(op, opcode);
	}
}

/**
 * Boolean operators & or |
 */
abstract class BooleanOperator extends Operator {
	
	public static final BooleanOperator AND = new BooleanOperator("and", BA){
		public ConstantExpression constantFolding(ConstantExpression left, ConstantExpression right){
			return new ConstantExpression(BooleanType.BOOLEAN_TYPE, (((ConstantExpression)left).value() == 1 && ((ConstantExpression)right).value() == 1) ? 1: 0);
		}
	};
	
	public static final BooleanOperator OR = new BooleanOperator("or", BO){
		public ConstantExpression constantFolding(ConstantExpression left, ConstantExpression right){
			return new ConstantExpression(BooleanType.BOOLEAN_TYPE, (((ConstantExpression)left).value() == 1 || ((ConstantExpression)right).value() == 1) ? 1: 0);
		}
	};
	
	public boolean validOperands(Expression left, Expression right, GCLErrorStream err){
		if(left instanceof GeneralError || right instanceof GeneralError){
			return false;
		}
		if(!(left.type().isCompatible(BooleanType.BOOLEAN_TYPE) && right.type().isCompatible(BooleanType.BOOLEAN_TYPE))){
			err.semanticError(GCLError.BOOLEAN_REQUIRED, "BooleanOperator expected booleans");
			return false;
		}
		return true;
	}
	
	private BooleanOperator(final String op, final SamOp opcode) {
		super(op, opcode);
	}
}

/** Used to represent string constants. Immutable. */
class StringConstant extends SemanticItem implements ConstantLike {
	
	private String samString;
	private String maccString;
	private int size;
	
	public StringConstant(String gclString) {
		samString = toSamString(gclString);
		maccString = toMaccString(gclString);
		size = 2*(gclString.length()/2);
	}
	
	public String samString(){
		return samString;
	}
	
	public String maccString(){
		return maccString;
	}
	
	public int size(){
		return size;
	}
	
	private final String toSamString(String gclString){
		return "\"" + gclString.substring(1, gclString.length()-1)
							   .replaceAll("\\\\", "\\")
							   .replaceAll(":", "::")
							   .replaceAll("\"", ":\"") + "\"";
	}
	
	private final String toMaccString(String gclString){
		return gclString.substring(1, gclString.length()-1);
	}
}

/**
 * Root of the expression object hierarchy. Represent integer and boolean expressions.
 */
abstract class Expression extends SemanticItem implements MaccSaveable{
	public Expression(final TypeDescriptor type, final int level) {
		super(level);
		this.type = type;
	}

	/**
	 * Polymorphically determine if an expression needs to be pushed on the run stack as part of parallel assignment.
	 * 
	 * @return true if the expression could appear as a LHS operand.
	 */
	public boolean needsToBePushed() {
		return false;
	}

	public TypeDescriptor type() {
		return type;
	}
	
	/**
	 * Soft Cast
	 * @return "this" if it is a ConstantExpression and an ErrorConstantExpression otherwise.
	 */
	public ConstantExpression expectConstantExpression(final GCLErrorStream err) {
		err.semanticError(GCLError.CONSTANT_REQUIRED);
		return new ErrorConstantExpression("$ ConstantExpression Required");
	}
	
	/**
	 * Soft Cast
	 * @return "this" if it is a VariableExpression and an ErrorVariableExpression otherwise.
	 */
	public VariableExpression expectVariableExpression(final GCLErrorStream err) {
		err.semanticError(GCLError.VARIABLE_REQUIRED);
		return new ErrorVariableExpression("$ VariableExpression Required");
	}

	public Expression expectExpression(final GCLErrorStream err) {
		return this;
	}

	public void discard() {
	} // (Function return only) default is to do nothing

	private final TypeDescriptor type;
}

/** Used to represent errors where expressions are expected. Immutable. */
class ErrorExpression extends Expression implements GeneralError, CodegenConstants {
	
	private final String message;

	public ErrorExpression(final String message) {
		super(ErrorType.NO_TYPE, GLOBAL_LEVEL);
		this.message = message;
		CompilerOptions.message(message);
	}
	
	public ConstantExpression expectConstantExpression(final GCLErrorStream err){
		return new ErrorConstantExpression("$ Requires Constant Expression");
	}
	
	public VariableExpression expectVariableExpression(final GCLErrorStream err){
		return new ErrorVariableExpression("$ Requires Variable Expression");
	}

	public String toString() {
		return message;
	}
}

/**
 * Constant expressions such as 53 and true. Immutable. Use this for boolean
 * constants also, with 1 for true and 0 for false.
 */
class ConstantExpression extends Expression implements CodegenConstants, ConstantLike {
	
	private final int value;

	public ConstantExpression(final TypeDescriptor type, final int value) {
		super(type, GLOBAL_LEVEL);
		this.value = value;
	}
	
	public ConstantExpression expectConstantExpression(final GCLErrorStream err) {
		return this;
	}
	
	public boolean needsToBePushed(){
		return false;// will never appear on the left hand side of assignment.
	}

	public String toString() {
		return "ConstantExpression: " + value + " with type " + type();
	}

	public boolean equals(Object other) {
		return (other instanceof ConstantExpression)
				&& type().baseType().isCompatible(((ConstantExpression) other).type().baseType())
				&& ((ConstantExpression) other).value == value;
	}

	public int hashCode() {
		return value * type().baseType().hashCode();
	}

	public int value() {
		return value;
	}
}

/** Used to represent errors where ConstantExpressions are expected. Immutable. */
class ErrorConstantExpression extends ConstantExpression implements GeneralError, CodegenConstants {
	
	private final String message;

	public ErrorConstantExpression(final String message) {
		super(ErrorType.NO_TYPE, GLOBAL_LEVEL);
		this.message = message;
		CompilerOptions.message(message);
	}

	public String toString() {
		return message;
	}
}

/**
 * Variable expressions such as x and y[3]. Variable here means storable.
 * Objects here are immutable. A level 0 expression is a temporary.
 */
class VariableExpression extends Expression implements CodegenConstants {
	
	private final int offset; //  offset of cell or register number
	private final boolean isDirect; // if false this is a pointer to a location.
	
	/**
	 * Create a variable expression object
	 * 
	 * @param type the type of this variable
	 * @param scope the nesting level (if >0) or 0 for a register, or -1 for stacktop
	 * @param offset the  offset of the cells of this variable, or the register number if scope is 0
	 * @param direct if false this represents a pointer to the variable
	 */
	public VariableExpression(final TypeDescriptor type, final int level, final int offset, final boolean direct) {
		super(type, level);
		this.offset = offset;
		this.isDirect = direct;
	}

	/**
	 * Create a temporary expression. The level is 0 and the offset is the register number
	 * 
	 * @param type the type of this value
	 * @param register the register number in which to hold it
	 * @param direct is the value in the register (true) or a pointer (false)
	 */
	public VariableExpression(final TypeDescriptor type, final int register, final boolean direct) {
		this(type, CPU_LEVEL, register, direct);
	}
	
	/**
	 * @return level > 0 AND direct
	 */
	public boolean case1(){
		return (semanticLevel() > CPU_LEVEL && isDirect);
	}

	/**
	 * @return level == 0 AND indirect
	 */
	public boolean case2(){
		return (semanticLevel() == CPU_LEVEL && !isDirect);
	}
	
	/**
	 * @return level > 0 AND indirect
	 */
	public boolean case3(){
		return (semanticLevel() > CPU_LEVEL && !isDirect);
	}

	public VariableExpression expectVariableExpression(final GCLErrorStream err){
		return this;
	}
	
	public boolean needsToBePushed() { // used by parallel assignment
		return true; // TODO debugging only
//		return semanticLevel() > CPU_LEVEL || (semanticLevel() == CPU_LEVEL && !isDirect); // pushes globals and locals and indirect temporary
	}

	/**
	 * The  address of the variable. What it is  to depends on
	 * its scopeLevel. If the level is 1 it is  to R15.
	 * 
	 * @return the  offset from its base register.
	 */
	public int offset() {
		return offset;
	}

	public boolean isDirect() {
		return isDirect;
	}

	public void discard(final Codegen codegen) // used for function return only
	{
		if (semanticLevel() == STACK_LEVEL) {
			codegen.gen2Address(Mnemonic.IA, STACK_POINTER, IMMED, UNUSED,
					type().size());
		}
	}

	public String toString() {
		return "VariableExpression: level(" + semanticLevel() + ") offset("
				+ offset + ") " + (isDirect ? "direct" : "indirect")
				+ ", with type " + type();
	}
}

/** Used to represent errors where ConstantExpressions are expected. Immutable. */
class ErrorVariableExpression extends VariableExpression implements GeneralError, CodegenConstants {
	
	private final String message;

	public ErrorVariableExpression(final String message) {
		super(ErrorType.NO_TYPE, -1, DIRECT);
		this.message = message;
		CompilerOptions.message(message);
	}

	public String toString() {
		return message;
	}
}

/** Carries information needed by the assignment statement */
class AssignRecord extends SemanticItem {
	
	private final ArrayList<VariableExpression> lhs = new ArrayList<VariableExpression>(3);
	private final ArrayList<Expression> rhs = new ArrayList<Expression>(3);
	
	public void left(VariableExpression left) {
		if (left == null) {
			left = new ErrorVariableExpression("$ Pushing bad lhs in assignment.");
		}
		lhs.add(left);
	}

	public void right(Expression right) {
		if (right == null) {
			right = new ErrorExpression("$ Pushing bad rhs in assignment.");
		}
		rhs.add(right);
	}

	public VariableExpression left(final int index) {
		return lhs.get(index);
	}

	public Expression right(final int index) {
		return rhs.get(index);
	}

	/**
	 * Determine whether the assignment statement is legal.
	 * 
	 * @return true if there are the same number of operands on the left and
	 *         right and the types are compatible, etc.
	 */
	public boolean verify(final SemanticActions.GCLErrorStream err) {
		boolean result = true;
		if (lhs.size() != rhs.size()) {
			result = false;
			err.semanticError(GCLError.LISTS_MUST_MATCH);
		}
		else{
			for(int variableIndex = 0; variableIndex < lhs.size(); variableIndex++){
				// do not complain again for error expressions.
				if(left(variableIndex) instanceof GeneralError || right(variableIndex) instanceof GeneralError){
					continue;
				}
				if(!left(variableIndex).type().isCompatible(right(variableIndex).type())){
					result = false;
					err.semanticError(GCLError.TYPE_MISMATCH);
				}
				else if (left(variableIndex).type() instanceof RangeType && right(variableIndex) instanceof ConstantExpression){
					RangeType leftRangeType = left(variableIndex).type().expectRangeType(err);
					ConstantExpression rightConstant = right(variableIndex).expectConstantExpression(err);
					result = leftRangeType.constantFolding(rightConstant, err);
				}
			}
		}
		return result;
	}

	/**
	 * The number of matched operands of a parallel assignment. In an incorrect
	 * input program the lhs and rhs may not match.
	 * 
	 * @return the min number of lhs, rhs variable expressions.
	 */
	public int size() {
		return Math.min(rhs.size(), lhs.size());
	}
}

/**
 * Used to pass a list of expressions around the parser/semantic area. It is
 * used in the creation of tuple expressions and may be useful elsewhere.
 */
class ExpressionList extends SemanticItem {
	/**
	 * Enter a new expression into the list
	 * 
	 * @param expression the expression to be entered
	 */
	public void enter(final Expression expression) {
		elements.add(expression);
	}

	/**
	 * Provide an enumeration service over the expressions in the list in the
	 * order they were inserted.
	 * 
	 * @return an enumeration over the expressions.
	 */
	public Iterator<Expression> elements() {
		return elements.iterator();
	}

	private final ArrayList<Expression> elements = new ArrayList<Expression>();
}

/** Loads procedure parameters polymorphically. */
abstract class Loader implements CodegenConstants, Mnemonic{
	
	protected final TypeDescriptor type;
	protected final int offset;

	public Loader(TypeDescriptor type, int offset){
		this.type = type;
		this.offset = offset;
	}
	
	/**
	 * Generates code that loads the parameter
	 * 
	 * @param parameter Expression to be loaded as the parameter
	 * @param codegen compiler's code generator
	 * @param err error stream on which to complain
	 */
	public abstract void load(Expression parameter, Codegen codegen, GCLErrorStream err);
	
	/**
	 * @return size of the parameter
	 */
	public abstract int size();
	
	/**
	 * @param other potential parameter
	 * @param err error stream on which to complain
	 * @return true if other is an acceptably typed parameter; false otherwise.
	 */
	public boolean isCompatible(Expression other, GCLErrorStream err){
		if(type.isCompatible(other.type())){
			return true;
		}
		err.semanticError(GCLError.TYPE_MISMATCH, "Expected: " + type.toString());
		return false;
	}
}

/** Loads reference parameters */
class ReferenceLoader extends Loader{
	
	public ReferenceLoader(TypeDescriptor type, int offset){
		super(type, offset);
	}

	@Override
	public void load(Expression parameter, Codegen codegen, GCLErrorStream err) {
		int referenceRegister = codegen.loadAddress(parameter);
		codegen.gen2Address(STO, referenceRegister, INDXD, STACK_POINTER, offset);
		codegen.freeTemp(DREG, referenceRegister);
	}

	@Override
	public int size() {
		return 2;
	}
	
	@Override
	public boolean isCompatible(Expression other, GCLErrorStream err){
		other.expectVariableExpression(err);
		return (other instanceof VariableExpression) && super.isCompatible(other, err);
	}
}

/** Loads 2 byte value parameters */
class ValueLoader extends Loader{
	
	public ValueLoader(TypeDescriptor type, int offset){
		super(type, offset);
	}

	@Override
	public void load(Expression parameter, Codegen codegen, GCLErrorStream err) {
		int valueRegister = codegen.loadRegister(parameter);
		codegen.gen2Address(STO, valueRegister, INDXD, STACK_POINTER, offset);
		codegen.freeTemp(DREG, valueRegister);
	}

	@Override
	public int size() {
		return type.size();
	}
}

/** Loads >2 byte value parameters */
class BlockLoader extends Loader{

	public BlockLoader(TypeDescriptor type, int offset){
		super(type, offset);
	}

	@Override
	public void load(Expression parameter, Codegen codegen, GCLErrorStream err) {
		int blockRegister = codegen.getTemp(2);
		int sizeRegister = blockRegister +1;
		Location parameterLocation = codegen.buildOperands(parameter);
		codegen.gen2Address(LD, blockRegister, parameterLocation);
		codegen.gen2Address(LD, sizeRegister, IMMED, UNUSED, size());
		codegen.gen2Address(BKT, blockRegister, INDXD, STACK_POINTER, offset);
		codegen.freeTemp(DREG, blockRegister);
		codegen.freeTemp(DREG, sizeRegister);
		codegen.freeTemp(parameterLocation);
	}

	@Override
	public int size() {
		return type.size();
	}
}

/**
 * Specifies the kind of procedure parameter. The value NOT_PARAM is used for
 * variables that are not parameters. Typesafe enumeration
 */
class ParameterKind extends SemanticItem{
	private ParameterKind(){}

	public static final ParameterKind NOT_PARAM = new ParameterKind();
	public static final ParameterKind VALUE = new ParameterKind();
	public static final ParameterKind REFERENCE = new ParameterKind();
}

/** Used to carry information for procedures */
class Procedure extends SemanticItem implements CodegenConstants, Mnemonic{
	
	static final int DEFAULT_FRAME_SIZE = 8;
	private List<Loader> parameters;
	private TupleType parentTupleType;
	private final Procedure parent;
	private final SymbolTable scope;
	private final int label;
	private boolean defined;
	private int frameSize;
	private int localDataSize;
	
	public Procedure(final Procedure parent, final SymbolTable scope, final int label, final int semanticLevel){
		super(semanticLevel);
		this.parent = parent;
		this.scope = scope;
		this.label = label;
		parentTupleType = ErrorTupleType.NO_TYPE;
		parameters = new ArrayList<Loader>();
		defined = false;
		frameSize = DEFAULT_FRAME_SIZE;
		localDataSize = DEFAULT_FRAME_SIZE;
	}
	
	public Procedure expectProcedure(final GCLErrorStream err){
		return this;
	}
	
	/**
	 * Assigns the TupleType which contains this procedure
	 * @param parentTuple TupleType which contains this procedure
	 */
	public void setParentTupleType(TupleType parentTuple){
		this.parentTupleType = parentTuple;
	}
	
	/**
	 * @return TupleType which contains this procedure
	 */
	public TupleType parentTupleType(){
		return parentTupleType;
	}
	
	/**
	 * @return returns the containing procedure
	 */
	public Procedure parent(){
		return parent;
	}
	
	/**
	 * @return this procedure's scope
	 */
	public SymbolTable scope(){
		return scope;
	}
	
	public int label(){
		return label;
	}

	/**
	 * @return true if the body of this procedure has been defined; false otherwise
	 */
	public boolean defined(){
		return defined;
	}
	
	public int frameSize(){
		return frameSize;
	}
	
	/**
	 * reserves a parameter for this procedure<br/>
	 * adds a new loader for it
	 * 
	 * @param type TypeDescriptor of the new parameter
	 * @param kind either VALUE or REFERENCE
	 * @param err error stream on which to complain
	 * @return VariableExpression representing the parameter
	 */
	public VariableExpression reserveParameterAddress(final TypeDescriptor type, final ParameterKind kind, final GCLErrorStream err){
		int offset = frameSize;
		Loader parameterLoader;
		if(kind == ParameterKind.REFERENCE){
			parameterLoader = new ReferenceLoader(type, offset);
			frameSize += parameterLoader.size();
			parameters.add(parameterLoader);
			return new VariableExpression(type, semanticLevel(), offset, INDIRECT);
		}
		if(kind == ParameterKind.VALUE){
			if(type.size() > 2){// TODO possibly check instanceof tupletype
				parameterLoader = new BlockLoader(type, offset);
			}
			else{
				parameterLoader = new ValueLoader(type, offset);
			}
			frameSize += parameterLoader.size();
			parameters.add(parameterLoader);
			return new VariableExpression(type, semanticLevel(), offset, DIRECT);
		}
		return new ErrorVariableExpression("$ Non parameter passed as such.");
	}

	/**
	 * Reserves a local variable in this procedure
	 * 
	 * @param type TypeDescriptor of the local
	 * @return VariableExpression representing the local
	 */
	public VariableExpression reserveLocalAddress(TypeDescriptor type) {
		localDataSize += type.size();
		int offset = -1 * (localDataSize - DEFAULT_FRAME_SIZE);
		return new VariableExpression(type, semanticLevel(), offset, DIRECT);
	}
	
	/**
	 * Calls this procedure with a given argumentList
	 * 
	 * @param argumentList ExpressionList of arguments
	 * @param codegen compiler's code generator
	 * @param err error stream on which to complain
	 */
	public void call(final ExpressionList argumentList, final Codegen codegen, final GCLErrorStream err){
		Iterator<Expression> arguments = argumentList.elements();
		for(Loader argumentLoader : parameters){
			if(arguments.hasNext()){
				Expression argument = arguments.next();
				if(argumentLoader.isCompatible(argument, err)){
					argumentLoader.load(argument, codegen, err);
				}
			}
			else{
				err.semanticError(GCLError.INVALID_ARGUMENTS, "Too few parameters passed.");
			}
		}
		if(arguments.hasNext()){
			err.semanticError(GCLError.INVALID_ARGUMENTS, "Too many parameters passed.");
		}
		codegen.genJumpSubroutine(STATIC_POINTER, "P" + label);
		codegen.gen2Address(LD, STATIC_POINTER, INDXD, FRAME_POINTER, 2);
		codegen.gen2Address(IA, STACK_POINTER, IMMED, UNUSED, frameSize);
	}
	
	/**
	 * 
	 * @param codegen compiler's code generator
	 */
	public void generateLinkCode(final Codegen codegen){
		defined = true;
		codegen.genLabel('P', label);
		codegen.gen2Address(STO, FRAME_POINTER, INDXD, STACK_POINTER, 0);
		codegen.gen2Address(LDA, FRAME_POINTER, INDXD, STACK_POINTER, 0);
		codegen.gen2Address(STO, STATIC_POINTER , INDXD, FRAME_POINTER, 4);
		codegen.gen2Address(LD, STATIC_POINTER, INDXD, FRAME_POINTER, 2);
		codegen.gen2Address(IS, STACK_POINTER, IMMED, UNUSED, localDataSize-DEFAULT_FRAME_SIZE);
		codegen.genPushPopToStack(PUSH);
	}

	/**
	 * 
	 * @param codegen compiler's code generator
	 */
	public void generateUnlinkCode(final Codegen codegen) {
		codegen.genLabel('U', label);
		codegen.gen2Address(IA, STACK_POINTER, IMMED, UNUSED, localDataSize - DEFAULT_FRAME_SIZE);
		codegen.gen2Address(LD, STATIC_POINTER, INDXD, FRAME_POINTER, 4);
		codegen.gen2Address(LD, FRAME_POINTER, INDXD, FRAME_POINTER, 0);
		codegen.gen1Address(JMP, IREG, STATIC_POINTER, 0);
	}
}

/** Used to represent errors where procedures are expected. Immutable. */
class ErrorProcedure extends Procedure implements GeneralError{
	
	private final String message;

	public ErrorProcedure(final String message) {
		super(null, SymbolTable.unchained(), 0, GLOBAL_LEVEL);
		this.message = message;
		CompilerOptions.message(message);
	}
	
	public Procedure parent(){
		return new ErrorProcedure("$ ErrorProcedure does not have a parent procedure.");
	}

	public String toString() {
		return message;
	}
}

/** Used to carry information for modules */
class ModuleRecord extends SemanticItem{
	
	private int label;
	
	/**
	 * @param label The label number which will be generated in the middle of this module.
	 */
	public ModuleRecord(int label){
		this.label = label;
	}
	
	public ModuleRecord expectModuleRecord(final GCLErrorStream err){
		return this;
	}
	
	public int label(){
		return label;
	}
}

/** Used to represent errors where modules are expected. Immutable. */
class ErrorModuleRecord extends ModuleRecord implements GeneralError{
	
	private final String message;

	public ErrorModuleRecord(final String message) {
		super(-1);
		this.message = message;
		CompilerOptions.message(message);
	}

	public String toString() {
		return message;
	}
}

/** Used to carry information for guarded commands such as if and do */
class GCRecord extends SemanticItem { // For guarded command statements if and do.
	private final int outLabel;
	private int nextLabel;
	
	public GCRecord(final int outLabel, final int nextLabel) {
		this.outLabel = outLabel;
		this.nextLabel = nextLabel;
	}

	/**
	 * Mutator for the internal label in an if or do.
	 * 
	 * @param label The new value for this label.
	 */
	public void nextLabel(int label) {
		nextLabel = label;
	}

	/**
	 * Returns the current value of the "internal" label of an if or do.
	 * 
	 * @return the "next" label to appear in a sequence.
	 */
	public int nextLabel() {
		return nextLabel;
	}

	/**
	 * The external label of an if or do statement.
	 * 
	 * @return the external label's numeric value.
	 */
	public int outLabel() {
		return outLabel;
	}

	public String toString() {
		return "GCRecord out: " + outLabel + " next: " + nextLabel;
	}
}

/** Used to carry information for for loops */
class ForRecord extends SemanticItem {
	private RangeType bounds;
	private int forLabel;
	private VariableExpression control;
	
	public ForRecord(RangeType bounds, int forLabel, VariableExpression control){
		this.bounds = bounds;
		this.forLabel = forLabel;
		this.control = control;
	}
	
	public RangeType bounds(){
		return bounds;
	}
	
	public int forLabel(){
		return forLabel;
	}
	
	public VariableExpression control(){
		return control;
	}
}

// --------------------- Types ---------------------------------
/**
 * Root of the type hierarchy. Objects to represent gcl types such as integer
 * and the various array and tuple types. These are immutable after they are
 * locked.
 */
abstract class TypeDescriptor extends SemanticItem implements Cloneable {
	
	private int size = 0; // default size. This varies in subclasses.

	public TypeDescriptor(final int size) {
		this.size = size;
	}
	
	public TypeDescriptor expectTypeDescriptor(final GCLErrorStream err){
		return this;
	}
	
	/**
	 * Soft Cast
	 * @return "this" if it is a range type and a NO_TYPE otherwise.
	 */
	public RangeType expectRangeType(final GCLErrorStream err) {
		err.semanticError(GCLError.RANGE_REQUIRED);
		return ErrorRangeType.NO_TYPE;
	}
	
	/**
	 * Soft Cast
	 * @return "this" if it is a tuple type and a NO_TYPE otherwise.
	 */
	public TupleType expectTupleType(final GCLErrorStream err) {
		err.semanticError(GCLError.TUPLE_REQUIRED);
		return ErrorTupleType.NO_TYPE;
	}

	/**
	 * The number of bytes required to store a variable of this type.
	 * 
	 * @return the byte size.
	 */
	public int size() {
		return size;
	}

	/**
	 * Determine if two types are assignment (or other) compatible. This must be
	 * a reflexive, symmetric, and transitive relation.
	 * 
	 * @param other the other type to be compared to this.
	 * @return true if they are compatible.
	 */
	public boolean isCompatible(final TypeDescriptor other) { // override this.

		return false;
	}

	/**
	 * Polymorphically determine the underlying type of this type. Useful mostly
	 * for range types.
	 * 
	 * @return this for non-ranges. The base type for ranges.
	 */
	public TypeDescriptor baseType() {
		return this;
	}

	public Object clone() {
		return this;
	}// Default version. Override in mutable subclasses.

	public String toString() {
		return "Unknown type.";
	}
}

/** Represents an error where a type is expected. Singleton. Immutable. */
class ErrorType extends TypeDescriptor implements GeneralError {
	
	private ErrorType() {
		super(0);
	}
	
	public RangeType expectRangeType(final GCLErrorStream err) {
		return ErrorRangeType.NO_TYPE;
	}
	
	public TupleType expectTupleType(final GCLErrorStream err) {
		return ErrorTupleType.NO_TYPE;
	}

	public String toString() {
		return "Error type.";
	}

	public static final ErrorType NO_TYPE = new ErrorType();
}

/** Integer type. Created at initialization. Singleton. Immutable. */
class IntegerType extends TypeDescriptor implements CodegenConstants {
	private IntegerType() {
		super(INT_SIZE);
	}

	public String toString() {
		return "integer type.";
	}

	static public final IntegerType INTEGER_TYPE = new IntegerType();

	public boolean isCompatible(final TypeDescriptor other) {
		return other != null && other.baseType() instanceof IntegerType;
	}
}

/** Boolean type. Created at initialization. Singleton. Immutable. */
class BooleanType extends TypeDescriptor implements CodegenConstants {
	private BooleanType() {
		super(INT_SIZE);
	}

	public String toString() {
		return "Boolean type.";
	}

	public boolean isCompatible(final TypeDescriptor other) {
		return other != null && other.baseType() instanceof BooleanType;
	}

	static public final BooleanType BOOLEAN_TYPE = new BooleanType();
}

/**Range type**/
class RangeType extends TypeDescriptor implements CodegenConstants {
	
	private final int lowerBound, upperBound;
	private final Location location;
	private final TypeDescriptor baseType;
	
	public RangeType(final TypeDescriptor baseType, final int lowerBound, final int upperBound, final Location location){
		super(INT_SIZE);
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
		this.location = location;
		this.baseType = baseType;
	}
	
	public int upperBound() {
		return upperBound;
	}

	public int lowerBound() {
		return lowerBound;
	}
	
	public Location location() {
		return location;
	}
	
	/**
	 * @param subscript index value being accessed.
	 * @param err error stream on which to complain.
	 * @return true if subscript is valid; false otherwise.
	 */
	public boolean constantFolding(ConstantExpression subscript, GCLErrorStream err){
		
		if(subscript.value() < lowerBound || subscript.value() > upperBound){
			err.semanticError(GCLError.VALUE_OUT_OF_RANGE, String.valueOf(subscript.value()) + " out of range: [" + lowerBound + ".." + upperBound + "]" );
			return false;
		}
		return true;
	}
	
	public RangeType expectRangeType(final GCLErrorStream err){
		return this;
	}

	public TypeDescriptor baseType(){
		return baseType;
	}
	
	public String toString(){
		return "rangetype: " + baseType.toString();
	}
	
	public boolean isCompatible(final TypeDescriptor other) {
		return baseType.isCompatible(other);
	}
}

/** Used to represent errors where RangeType is expected. Immutable. */
class ErrorRangeType extends RangeType implements GeneralError, CodegenConstants {

	private ErrorRangeType() {
		super(ErrorType.NO_TYPE, -1, -1, new Location(DMEM, UNUSED, UNUSED));
	}

	public String toString() {
		return "error range type.";
	}
	
	public static final ErrorRangeType NO_TYPE = new ErrorRangeType();
}

/**Array type**/
class ArrayType extends TypeDescriptor implements CodegenConstants {
	
	private final RangeType subscriptType;
	private final TypeDescriptor componentType;
	
	public ArrayType(final RangeType subscriptType, final TypeDescriptor componentType){
		super(componentType.size()*(1+subscriptType.upperBound()-subscriptType.lowerBound()));
		this.subscriptType = subscriptType;
		this.componentType = componentType;
	}
	
	public RangeType subscriptType(){
		return subscriptType;
	}
	
	public TypeDescriptor componentType(){
		return componentType;
	}

	public TypeDescriptor baseType(){
		return this;
	}
	
	public String toString(){
		return "arraytype: " + componentType.toString() + " [" + subscriptType.lowerBound() + ".." + subscriptType.upperBound() + "]";
	}
	
	public boolean isCompatible(final TypeDescriptor other) {// uses short circuiting to avoid a dangerous hard cast.
		return (other instanceof ArrayType) &&
			   (componentType.isCompatible(((ArrayType)other).componentType)) &&
			   (subscriptType.isCompatible(((ArrayType)other).subscriptType)) &&
			   (subscriptType.lowerBound() == ((ArrayType)other).subscriptType.lowerBound()) &&
			   (subscriptType.upperBound() == ((ArrayType)other).subscriptType.upperBound());
	}
}

/**
 * Use this when you need to build a list of types and know the total size of
 * all of them. Used in creation of tuples.
 */
class TypeList extends SemanticItem {
	
	private final ArrayList<TypeDescriptor> elements = new ArrayList<TypeDescriptor>(2);
	private final ArrayList<Identifier> names = new ArrayList<Identifier>(2);
	private final ArrayList<Procedure> procedures = new ArrayList<Procedure>(2);
	private final ArrayList<Identifier> procedureNames = new ArrayList<Identifier>(2);
	private int size = 0; // sum of the sizes of the types
	private static int next = 0;
	
	/**
	 * Add a new type-name pair to the list and accumulate its size
	 * 
	 * @param aType the type to be added
	 * @param name the name associated with the field
	 */
	public void enter(final TypeDescriptor aType, final Identifier name, GCLErrorStream err) {
		if(!names.contains(name)){
			elements.add(aType);
			names.add(name);
			size += aType.size();
		}
		else{
			err.semanticError(GCLError.ALREADY_DEFINED, name.name());
		}
	}

	/**
	 * Add a new type to the list, using a default name This is used to define
	 * anonymous fields in a tuple value
	 * 
	 * @param aType the type of the entry to be added
	 */
	public void enter(final TypeDescriptor aType, GCLErrorStream err) {
		enter(aType, new Identifier("none_" + next), err);
		next++; // unique "names" for anonymous fields.
	}
	
	/**
	 * Add a new procedure record to the list.
	 * @param procedure A new procedure record.
	 */
	public void enter(final Procedure procedure, final Identifier procedureName) {
		procedures.add(procedure);
		procedureNames.add(procedureName);
	}

	/**
	 * The total size of the types in the list
	 * 
	 * @return the sum of the sizes
	 */
	public int size() {
		return size;
	}

	/**
	 * An enumeration service for the types in the list in order of insertion
	 * 
	 * @return an enumeration over the type descriptors.
	 */
	public Iterator<TypeDescriptor> elements() {
		return elements.iterator();
	}

	/**
	 * An enumeration service for the names of the fields
	 * 
	 * @return an enumeration over the identifiers
	 */
	public Iterator<Identifier> names() {
		return names.iterator();
	}
	
	/**
	 * An enumeration service for the procedures in the list in order of insertion
	 * 
	 * @return an enumeration over the procedure records.
	 */
	public Iterator<Procedure> procedures() {
		return procedures.iterator();
	}
	
	/**
	 * An enumeration service for the names of the procedures
	 * 
	 * @return an enumeration over the identifiers
	 */
	public Iterator<Identifier> procedureNames() {
		return procedureNames.iterator();
	}
}

/**
 * Represents the various tuple types. Created as needed. These are built
 * incrementally and locked at the end to make them immutable afterwards.
 */
class TupleType extends TypeDescriptor { // mutable
	
	private final HashMap<Identifier, TupleField> fields = new HashMap<Identifier, TupleField>(4);
	private final ArrayList<Identifier> names = new ArrayList<Identifier>(4);
	private SymbolTable methods = null; // later

	/**
	 * Create a tuple type from a list of its component types.
	 * 
	 * @param carrier the list of component types
	 */
	public TupleType(final TypeList carrier) {
		super(carrier.size());
		methods = SymbolTable.unchained();
		Iterator<TypeDescriptor> e = carrier.elements();
		Iterator<Identifier> n = carrier.names();
		Iterator<Procedure> p = carrier.procedures();
		Iterator<Identifier> pn = carrier.procedureNames();
		int inset = 0;
		while (e.hasNext()) {
			TypeDescriptor t = e.next();
			Identifier id = n.next();
			fields.put(id, new TupleField(inset, t));
			inset += t.size();
			names.add(id);
		}
		while (p.hasNext()) {
			Procedure nextProcedure = p.next();
			nextProcedure.setParentTupleType(this);
			methods.newEntry("procedure", pn.next(), nextProcedure);
		}
	}
	
	public SymbolTable methods(){
		return methods;
	}
	
	public boolean isCompatible(TypeDescriptor other){
		return (other instanceof TupleType) &&
			   (fieldCount() == ((TupleType)other).fieldCount()) &&
			   (fieldsAreCompatible((TupleType)other));
	}
	
	/** @return true if all field types are compatible between this and other. */
	private boolean fieldsAreCompatible(TupleType other){
		Iterator<TupleField> thisFields = fields.values().iterator();
		Iterator<TupleField> otherFields = other.fields.values().iterator();
		while(thisFields.hasNext()){
			if(!thisFields.next().type.isCompatible(otherFields.next().type)){
				return false;
			}
		}
		return true;
	}
	
	public TupleType expectTupleType(final GCLErrorStream err){
		return this;
	}

	public String toString() {
		String result = "tupleType:[";
		for (int i = 0; i < fields.size(); ++i) {
			result += fields.get(names.get(i)) + " : " + names.get(i) + ", ";// type);
		}
		result += "] with size: " + size();
		return result;
	}

	/**
	 * Get the number of data fields in this tuple
	 * 
	 * @return the number of fields in this tuple
	 */
	public int fieldCount() {
		return names.size();
	}

	/**
	 * Retrieve a named component type of the tuple. It might throw
	 * NoSuchElementException if the argument is invalid.
	 * 
	 * @param fieldName the name of the desired component type
	 * @return the type of the named component
	 */
	public TypeDescriptor getType(final Identifier fieldName) { // null return value possible
	
		return fields.get(fieldName).type();
	}
	
	/**
	 * Retrieve a named component inset of the tuple. It might throw
	 * NoSuchElementException if the argument is invalid.
	 * 
	 * @param fieldName the name of the desired component inset
	 * @return the inset of the named component
	 */
	public int getInset(final Identifier fieldName) { // null return value possible
	
		return fields.get(fieldName).inset();
	}

	private class TupleField {
		public TupleField(final int inset, final TypeDescriptor type) {
			this.inset = inset;
			this.type = type;
		}

		public String toString() {
			return type.toString();
		}

		public TypeDescriptor type() {
			return type;
		}

		public int inset() {
			return inset;
		}

		private final int inset;
		private final TypeDescriptor type;
	}
}

/** Used to represent errors where TupleType is expected. Immutable. */
class ErrorTupleType extends TupleType implements GeneralError, CodegenConstants {

	private ErrorTupleType() {
		super(new TypeList());
	}

	public String toString() {
		return "error tuple type.";
	}
	
	public static final ErrorTupleType NO_TYPE = new ErrorTupleType();
}

// --------------------- Semantic Error Values ----------------------------

/**
 * Represents the various gcl errors User errors represent an error in the input
 * program. They must be reported.
 * <p>
 * Compiler errors represent an error in the compiler itself. They must be
 * fixed. These are used to report errors to the user.
 */
abstract class GCLError {
	// The following are user errors. Report them.
	static final GCLError TYPE_REQUIRED = new Value(1,
			"ERROR -> Type descriptor required. ");
	static final GCLError INTEGER_REQUIRED = new Value(2,
			"ERROR -> Integer type required. ");
	static final GCLError BOOLEAN_REQUIRED = new Value(3,
			"ERROR -> Boolean type required. ");
	static final GCLError RANGE_REQUIRED = new Value(4,
			"ERROR -> Range type required. ");
	static final GCLError ARRAY_REQUIRED = new Value(5,
			"ERROR -> Array type required. ");
	static final GCLError TUPLE_REQUIRED = new Value(6,
			"ERROR -> Tuple type required. ");
	static final GCLError EXPRESSION_REQUIRED = new Value(7,
			"ERROR -> Expression required. ");
	static final GCLError VARIABLE_REQUIRED = new Value(8,
			"ERROR -> Variable expression required. ");
	static final GCLError CONSTANT_REQUIRED = new Value(9,
			"ERROR -> Constant expression required. ");
	static final GCLError MODULE_REQUIRED = new Value(10,
			"ERROR -> Module required. ");
	static final GCLError PROCEDURE_REQUIRED = new Value(11,
			"ERROR -> Procedure required. ");
	static final GCLError ALREADY_DEFINED = new Value(12,
			"ERROR -> The item is already defined. ");
	static final GCLError NAME_NOT_DEFINED = new Value(13,
			"ERROR -> The name is not defined. ");
	static final GCLError LISTS_MUST_MATCH = new Value(14,
			"ERROR -> List lengths must match. ");
	static final GCLError ILLEGAL_IDENTIFIER = new Value(15,
			"ERROR -> Illegal spelling of an identifer. ");
	static final GCLError TYPE_MISMATCH = new Value(16,
			"ERROR -> Incompatible types. ");
	static final GCLError ILLEGAL_RANGE = new Value(17,
			"ERROR -> Illegal range. ");
	static final GCLError VALUE_OUT_OF_RANGE = new Value(18,
			"ERROR -> Value out of range. ");
	static final GCLError PROCEDURE_NOT_DEFINED = new Value(19,
			"ERROR -> Procedure must be defined before it is called. ");
	static final GCLError INVALID_ARGUMENTS = new Value(20,
			"ERROR -> Procedure invoked incorrectly. ");
	static final GCLError INVALID_RETURN = new Value(21,
			"ERROR -> Cannot return value from the global level. ");
		
	// The following are compiler errors. Repair them.
	static final GCLError ILLEGAL_LOAD = new Value(91,
			"COMPILER ERROR -> The expression is null. ");
	static final GCLError NOT_A_POINTER = new Value(92,
			"COMPILER ERROR -> LoadPointer saw a non-pointer. ");
	static final GCLError ILLEGAL_MODE = new Value(93,
			"COMPILER ERROR -> Sam mode out of range. ");
	static final GCLError NO_REGISTER_AVAILABLE = new Value(94,
			"COMPILER ERROR -> There is no available register. ");
	static final GCLError ILLEGAL_LOAD_ADDRESS = new Value(95,
			"COMPILER ERROR -> Attempt to LoadAddress not a variable. ");
	static final GCLError ILLEGAL_LOAD_SIZE = new Value(96,
			"COMPILER ERROR -> Attempt to load value with size > 4 bytes. ");
	static final GCLError UNKNOWN_ENTRY = new Value(97,
			"COMPILER ERROR -> An unknown entry was found. ");
	static final GCLError ILLEGAL_ARRAY_ACCESS = new Value(98,
			"COMPILER ERROR -> array[constant subscript] - Case 4. ");
	static final GCLError ILLEGAL_TUPLE_ACCESS = new Value(98,
			"COMPILER ERROR -> extractTupleComponent - Case 4. ");
	static final GCLError LABEL_UNDEFINED = new Value(99,
			"COMPILER ERROR -> label undefined: ");

	// More of each kind of error as you go along building the language.

	public abstract int value();

	public abstract String message();

	static class Value extends GCLError {
		private Value(int value, String msg) {
			this.message = msg;
			this.value = value;
		}

		public int value() {
			return value;
		}

		public String message() {
			return message;
		}

		private final int value;
		private final String message;
	}
} // end GCLError

// --------------------- SemanticActions ---------------------------------

public class SemanticActions implements Mnemonic, CodegenConstants {
	
	private final Codegen codegen;

	static final IntegerType INTEGER_TYPE = IntegerType.INTEGER_TYPE;
	static final BooleanType BOOLEAN_TYPE = BooleanType.BOOLEAN_TYPE;
	static final TypeDescriptor NO_TYPE = ErrorType.NO_TYPE;
	private SemanticLevel currentLevel = new SemanticLevel();
	private GCLErrorStream err = null;
	private Procedure currentProcedure = new ErrorProcedure("$ Global level.");

	SemanticActions(final Codegen codeGenerator, final GCLErrorStream err) {
		this.codegen = codeGenerator;
		codegen.setSemanticLevel(currentLevel());
		this.err = err;
		init();
	}

	/** Used to produce messages when an error occurs */
	static class GCLErrorStream extends Errors { // Errors is defined in Parser
		GCLErrorStream(final Scanner scanner) {
			super(scanner);
		}

		void semanticError(final GCLError errNum) {
			PrintWriter out = scanner.outFile();
			out.print("At ");
			semanticError(errNum.value(), scanner.currentToken().line(), scanner.currentToken().column());
			out.println(errNum.message());
			out.println();
			CompilerOptions.genHalt();
		}

		void semanticError(final GCLError errNum, final String extra) {
			scanner.outFile().println(extra);
			semanticError(errNum);
		}
	} // end GCLErrorStream

	/***************************************************************************
	 * Declares a new module, registering it as the new current module.
	 * 
	 * @param scope This module's associated scope.
	 * @return a record which contains...
	 **************************************************************************/
	public ModuleRecord declareModule(final SymbolTable scope, final Identifier id){
		
		ModuleRecord newModule = new ModuleRecord(codegen.getLabel());
		codegen.genJumpLabel(JMP, 'M', newModule.label());
		SymbolTable.setCurrentModule(newModule);
		scope.newEntry("module", id, newModule);
		return newModule;
	}
	
	/***************************************************************************
	 * Auxiliary Determine if a symboltable entry can safely be
	 * redefined at this point. Only one definition is legal in a given scope.
	 * 
	 * @param entry a symbol table entry to be checked.
	 * @return true if it is ok to redefine this entry at this point.
	 **************************************************************************/
	private boolean OKToRedefine(final SymbolTable.Entry entry) {
		
		if(entry == SymbolTable.NULL_ENTRY || entry.module() != SymbolTable.currentModule() || entry.semanticRecord().semanticLevel() != currentLevel.value()){
			return true;
		}
		return false;
	}

	/***************************************************************************
	 * Auxiliary Report that the identifier is already
	 * defined in this scope if it is. Called from most declarations.
	 * 
	 * @param ID an Identifier
	 * @param scope the symbol table used to find the identifier.
	 **************************************************************************/
	private void complainIfDefinedHere(final SymbolTable scope, final Identifier id) {
		
		SymbolTable.Entry entry = scope.lookupIdentifier(id);
		if (!OKToRedefine(entry)) {
			err.semanticError(GCLError.ALREADY_DEFINED);
		}
	}

	/***************************************************************************
	 * auxiliary moveBlock moves a block (using blocktransfer)
	 * from source to dest. Both source and destination refer to expr entries .
	 **************************************************************************/
	private void moveBlock(final Expression source, final Expression destination) {
		
		if (source instanceof ErrorExpression) {
			return;
		}
		if (destination instanceof ErrorExpression) {
			return;
		}
		int size = source.type().size();
		int reg = codegen.getTemp(2); // need 2 registers for BKT
		Location sourceLocation = codegen.buildOperands(source);
		codegen.gen2Address(LDA, reg, sourceLocation);
		codegen.gen2Address(LD, reg + 1, IMMED, UNUSED, size);
		sourceLocation = codegen.buildOperands(destination);
		codegen.gen2Address(BKT, reg, sourceLocation);
		codegen.freeTemp(DREG, reg);
		codegen.freeTemp(sourceLocation);
	}

	/***************************************************************************
	 * auxiliary moveBlock moves a block (using blocktransfer)
	 * from source to dest. Source refers to an expr entry. mode, base, and
	 * displacement give the dest.
	 **************************************************************************/
	private void moveBlock(final Expression source, final Mode mode, final int base, final int displacement) {
		
		if (source instanceof ErrorExpression) {
			return;
		}
		int size = source.type().size();
		int reg = codegen.getTemp(2); // need 2 registers for BKT
		Location sourceLocation = codegen.buildOperands(source);
		codegen.gen2Address(LDA, reg, sourceLocation);
		codegen.gen2Address(LD, reg + 1, IMMED, UNUSED, size);
		codegen.gen2Address(BKT, reg, mode, base, displacement);
		codegen.freeTemp(DREG, reg);
		codegen.freeTemp(sourceLocation);
	}

	/***************************************************************************
	 * auxiliary moveBlock moves a block (using blocktransfer)
	 * from source to destination. Source is given by mode, base, displacement
	 * and destination refers to an expr entry .
	 **************************************************************************/
	private void moveBlock(final Mode mode, final int base, final int displacement, final Expression destination) {
		
		if (destination instanceof ErrorExpression) {
			return;
		}
		int size = destination.type().size();
		int reg = codegen.getTemp(2); // need 2 registers for BKT
		if (mode == IREG) {// already have an address
			codegen.gen2Address(LD, reg, DREG, base, UNUSED);
		} else {
			codegen.gen2Address(LDA, reg, mode, base, displacement);
		}
		codegen.gen2Address(LD, reg + 1, IMMED, UNUSED, size);
		Location destinationLocation = codegen
				.buildOperands(destination);
		codegen.gen2Address(BKT, reg, destinationLocation);
		codegen.freeTemp(DREG, reg);
		codegen.freeTemp(destinationLocation);
	}

	/***************************************************************************
	 * auxiliary Push an expression onto the run time stack
	 * 
	 * @param source the expression to be pushed
	 **************************************************************************/
	private void pushExpression(final Expression source) {
		
		if (source.type().size() == INT_SIZE) {
			int reg = codegen.loadRegister(source);
			codegen.genPushRegister(reg);
			codegen.freeTemp(DREG, reg);
		} else { // blockmove
			int size = source.type().size();
			codegen.gen2Address(IS, STACK_POINTER, IMMED, UNUSED, size);
			moveBlock(source, IREG, STACK_POINTER, UNUSED);
		}
	}

	/***************************************************************************
	 * auxiliary Pop an expression from the run time stack into a given destination
	 * @param destination the destination for the pop
	 **************************************************************************/
	private void popExpression(final Expression destination) {
		
		if (destination.type().size() == INT_SIZE) {
			int reg = codegen.getTemp(1);
			codegen.genPopRegister(reg);
			Location destinationLocation = codegen
					.buildOperands(destination);
			codegen.gen2Address(STO, reg, destinationLocation);
			codegen.freeTemp(DREG, reg);
			codegen.freeTemp(destinationLocation);
		} else { // blockmove
			moveBlock(IREG, STACK_POINTER, UNUSED, destination);
			codegen.gen2Address(IA, STACK_POINTER, IMMED, UNUSED, destination
					.type().size());
		}
	}

	/***************************************************************************
	 * auxiliary Move the value of an expression from its
	 * source to a destination
	 * 
	 * @param source the source of the expression
	 * @param destination the destination to which to move the value
	 **************************************************************************/
	private void simpleMove(final Expression source, final Expression destination) {
		
		if (destination.type().size() == INT_SIZE) {
			int reg = codegen.loadRegister(source);
			Location destinationLocation = codegen .buildOperands(destination);
			codegen.gen2Address(STO, reg, destinationLocation);
			codegen.freeTemp(DREG, reg);
			codegen.freeTemp(destinationLocation);
		} else {
			moveBlock(source, destination);
		}
	}

	/***************************************************************************
	 * auxiliary Move the value of an expression from a source to a destination
	 * 
	 * @param source the source of the move
	 * @param mode the mode of the destination's location
	 * @param base the base of the destination location
	 * @param displacement the displacement of the destination location
	 **************************************************************************/
	private void simpleMove(final Expression source, final Mode mode, final int base, final int displacement) {
		
		if (source.type().size() == INT_SIZE) {
			int reg = codegen.loadRegister(source);
			codegen.gen2Address(STO, reg, mode, base, displacement);
			codegen.freeTemp(DREG, reg);
			codegen.freeTemp(mode, base);
		} else {
			moveBlock(source, mode, base, displacement);
		}
	}

	/***************************************************************************
	 * Transform an identifier into the semantic item that it represents
	 * 
	 * @param scope the current scope
	 * @param ID and identifier to be transformed
	 * @return the semantic item that the identifier represents.
	 **************************************************************************/
	SemanticItem semanticValue(final SymbolTable scope, final Identifier id) {
		
		SymbolTable.Entry symbol = scope.lookupIdentifier(id);
		if (symbol == SymbolTable.NULL_ENTRY) {
			err.semanticError(GCLError.NAME_NOT_DEFINED);
			return new SemanticError("Identifier not found in symbol table.");
		} else {
			return symbol.semanticRecord();
		}
	}
	
	/***************************************************************************
	 * Transform an identifier into the semantic item that it represents
	 * 
	 * @param scope the current scope
	 * @param module the module which has access to ID
	 * @param ID and identifier to be transformed
	 * @return the semantic item that the identifier represents.
	 **************************************************************************/
	SemanticItem semanticValue(final SymbolTable scope, final ModuleRecord module, final Identifier id) {
		
		if(module instanceof GeneralError){
			return new SemanticError("Identifier not found in symbol table.");
		}
		SymbolTable.Entry symbol = scope.lookupIdentifier(module, id);
		if (symbol == SymbolTable.NULL_ENTRY) {
			err.semanticError(GCLError.NAME_NOT_DEFINED);
			return new SemanticError("Identifier not found in symbol table.");
		} else {
			return symbol.semanticRecord();
		}
	}

	/***************************************************************************
	 * Generate code for an assignment. Copy the RHS expressions to the corresponding LHS variables.
	 * 
	 * @param expressions an assignment record with two expr vectors (RHSs, LHSs )
	 **************************************************************************/
	void parallelAssign(final AssignRecord expressions) {
		
		int i;
		// part 1. checks and optimizations
		if (!expressions.verify(err)) {
			return;
		}
		int entries = expressions.size(); // number of entries to process
		if (CompilerOptions.optimize && entries == 1) {
			simpleMove(expressions.right(0), expressions.left(0));
			return; // Optimized to skip push/pop for one item
		}
		// part 2. pushing except consts, temps, and stackvariables
		for (i = 0; i < entries; ++i) {
			Expression rightExpression = expressions.right(i);
			if (rightExpression.needsToBePushed()){
				pushExpression(rightExpression);
			}
		}
		// part 3. popping the items pushed in part 2 & copying the rest
		for (i = entries - 1; i >= 0; --i){
			Expression rightExpression = expressions.right(i);
			Expression leftExpression = expressions.left(i);
			// runtime bounds check.
			if (leftExpression.type() instanceof RangeType && !(rightExpression instanceof ConstantExpression)){
				codegen.gen2Address(TRNG, codegen.loadRegister(rightExpression), leftExpression.type().expectRangeType(err).location());
			}
			if (rightExpression.needsToBePushed()){
				popExpression(leftExpression);
			} else { // the item wasn't pushed, so normal copy
				simpleMove(rightExpression, leftExpression);
			}
		}
	}

	/***************************************************************************
	 * Generate code to read into an integer variable. (Must be an assignable variable)
	 * 
	 * @param expression (integer variable) expression
	 **************************************************************************/
	void readVariable(final Expression expression) {
		
		if (expression instanceof GeneralError) {
			return;
		}
		if (!expression.type().isCompatible(INTEGER_TYPE)) {
			err.semanticError(GCLError.INTEGER_REQUIRED, "   while Reading" + expression.type().toString());
			return;
		}
		Location expressionLocation = codegen.buildOperands(expression);
		codegen.gen1Address(RDI, expressionLocation);
		// bounds check
		if (expression.type() instanceof RangeType){
			codegen.gen2Address(TRNG, codegen.loadRegister(expression), expression.type().expectRangeType(err).location());
		}
		codegen.freeTemp(expressionLocation);
	}

	/***************************************************************************
	 * Generate code to write a StringConstant.
	 * 
	 * @param stringConstant value to be printed.
	 **************************************************************************/
	void writeString(final StringConstant stringConstant) {
		
		if (stringConstant instanceof GeneralError) {
			return;
		}
		codegen.gen1Address(WRST, codegen.buildOperands(stringConstant));
	}
	
	/***************************************************************************
	 * Generate code to write an integer expression.
	 * 
	 * @param expression (integer) expression
	 **************************************************************************/
	void writeExpression(final Expression expression) {
		
		if (expression instanceof GeneralError) {
			return;
		}
		if (!expression.type().isCompatible(INTEGER_TYPE)) {
			err.semanticError(GCLError.INTEGER_REQUIRED, "   while Writing");
			return;
		}
		Location expressionLocation = codegen.buildOperands(expression);
		codegen.gen1Address(WRI, expressionLocation);
		codegen.freeTemp(expressionLocation);
	}

	/***************************************************************************
	 * Generate code to write an end of line mark.
	 **************************************************************************/
	void genEol() {
		
		codegen.gen0Address(WRNL);
	}

	/***************************************************************************
	 * Generate code to add two integer expressions. Result in Register.
	 * 
	 * @param left an expression (lhs)Must be integer
	 * @param op an add operator
	 * @param right an expression (rhs)Must be integer
	 * @return result expression -integer (in register)
	 **************************************************************************/
	Expression addExpression(final Expression left, final AddOperator op, final Expression right) {
		
		if(!op.validOperands(left, right, err)){
			return new ErrorExpression("$ Incompatible Types");
		}
		if(left instanceof ConstantExpression && right instanceof ConstantExpression) {
			return op.constantFolding((ConstantExpression)left, (ConstantExpression)right);
		}
		int reg = codegen.loadRegister(left);
		Location rightLocation = codegen.buildOperands(right);
		codegen.gen2Address(op.opcode(), reg, rightLocation);
		codegen.freeTemp(rightLocation);
		return new VariableExpression(INTEGER_TYPE, reg, DIRECT); // temporary
	}

	/***************************************************************************
	 * Generate code to negate an integer expression. Result in Register.
	 * 
	 * @param expression expression to be negated -must be integer
	 * @return result expression -integer (in register)
	 **************************************************************************/
	Expression negateExpression(final Expression expression) {
		
		if(expression instanceof GeneralError){
			return new ErrorExpression("$ Incompatible Types");
		}
		if(!expression.type().isCompatible(INTEGER_TYPE)){
			err.semanticError(GCLError.INTEGER_REQUIRED, "IntegerNegate expected integer");
			return new ErrorExpression("$ Incompatible Types");
		}
		if(expression instanceof ConstantExpression) {
			return new ConstantExpression(expression.type(), -((ConstantExpression)expression).value());
		}
		Location expressionLocation = codegen.buildOperands(expression);
		int reg = codegen.getTemp(1);
		codegen.gen2Address(INEG, reg, expressionLocation);
		codegen.freeTemp(expressionLocation);
		return new VariableExpression(INTEGER_TYPE, reg, DIRECT); // temporary
	}
	
	/***************************************************************************
	 * Generate code to negate a boolean expression. Result in Register.
	 * 
	 * @param booleanExpression expression to be negated -must be boolean
	 * @return result expression -boolean (in register)
	 **************************************************************************/
	Expression negateBooleanExpression(final Expression booleanExpression){
		
		if(booleanExpression instanceof GeneralError){
			return new ErrorExpression("$ Incompatible Types");
		}
		if(!booleanExpression.type().isCompatible(BOOLEAN_TYPE)){
			err.semanticError(GCLError.BOOLEAN_REQUIRED, "BooleanNegate expected boolean");
			return new ErrorExpression("$ Incompatible Types");
		}
		if(booleanExpression instanceof ConstantExpression) {
			return new ConstantExpression(booleanExpression.type(), 1-((ConstantExpression)booleanExpression).value());
		}
		int reg = codegen.loadRegister(new ConstantExpression(BOOLEAN_TYPE, 1));
		Location expressionLocation = codegen.buildOperands(booleanExpression);
		codegen.gen2Address(IS, reg, expressionLocation);
		codegen.freeTemp(expressionLocation);
		return new VariableExpression(BOOLEAN_TYPE, reg, DIRECT); // temporary
	}

	/***************************************************************************
	 * Generate code to multiply two integer expressions. Result in Register.
	 * 
	 * @param left an expression (lhs)Must be integer
	 * @param op a multiplicative operator
	 * @param right an expression (rhs)Must be integer
	 * @return result expression -integer (in register)
	 **************************************************************************/
	Expression multiplyExpression(final Expression left, final MultiplyOperator op, final Expression right) {
		
		if(!op.validOperands(left, right, err)){
			return new ErrorExpression("$ Incompatible Types");
		}
		if(left instanceof ConstantExpression && right instanceof ConstantExpression) {
			return op.constantFolding((ConstantExpression)left, (ConstantExpression)right);
		}

		if (op == MultiplyOperator.MODULO){
			return moduloExpression(left,right);
		}
		int reg = codegen.loadRegister(left);
		Location rightLocation = codegen.buildOperands(right);
		codegen.gen2Address(op.opcode(), reg, rightLocation);
		codegen.freeTemp(rightLocation);
		return new VariableExpression(INTEGER_TYPE, reg, DIRECT); // temporary
	}
	
	/***************************************************************************
	 * Generate code to calculate left modulo right. Result in Register.
	 * 
	 * @param left an expression (lhs)Must be integer
	 * @param right an expression (rhs)Must be integer
	 * @return result expression left modulo right (in register)
	 **************************************************************************/
	Expression moduloExpression(final Expression left, final Expression right) {
		
		int regInner = codegen.loadRegister(left);
		int regOuter = codegen.getTemp(1);
		codegen.gen2Address(LD, regOuter, DREG, regInner, UNUSED);
		
		Location rightLocation = codegen.buildOperands(right);
		codegen.gen2Address(ID, regInner, rightLocation);
		codegen.gen2Address(IM, regInner, rightLocation);
		codegen.gen2Address(IS, regOuter, DREG, regInner, UNUSED);
		codegen.freeTemp(rightLocation);
		codegen.freeTemp(DREG, regInner);
		return new VariableExpression(INTEGER_TYPE, regOuter, DIRECT); // temporary
	}
	
	/***************************************************************************
	 * Generate code to and two boolean expressions. Result in Register.
	 * 
	 * @param left an expression (lhs)Must be boolean
	 * @param right an expression (rhs)Must be boolean
	 * @return result expression -boolean (in register)
	 **************************************************************************/
	Expression andExpression(final Expression left, final Expression right) {
		
		Operator op = BooleanOperator.AND;
		if(!op.validOperands(left, right, err)){
			return new ErrorExpression("$ Incompatible Types");
		}
		if (left instanceof ConstantExpression && right instanceof ConstantExpression){
			return op.constantFolding((ConstantExpression)left, (ConstantExpression)right);
		}
		int reg = codegen.loadRegister(left);
		Location rightLocation = codegen.buildOperands(right);
		codegen.gen2Address(op.opcode(), reg, rightLocation);
		codegen.freeTemp(rightLocation);
		return new VariableExpression(BOOLEAN_TYPE, reg, DIRECT); // temporary
	}
	
	/***************************************************************************
	 * Generate code to or two boolean expressions. Result in Register.
	 * 
	 * @param left an expression (lhs)Must be boolean
	 * @param right an expression (rhs)Must be boolean
	 * @return result expression -boolean (in register)
	 **************************************************************************/
	Expression orExpression(final Expression left, final Expression right) {
		
		Operator op = BooleanOperator.OR;
		if(!op.validOperands(left, right, err)){
			return new ErrorExpression("$ Incompatible Types");
		}
		if (left instanceof ConstantExpression && right instanceof ConstantExpression){
			return op.constantFolding((ConstantExpression)left, (ConstantExpression)right);
		}
		int reg = codegen.loadRegister(left);
		Location rightLocation = codegen.buildOperands(right);
		codegen.gen2Address(op.opcode(), reg, rightLocation);
		codegen.freeTemp(rightLocation);
		return new VariableExpression(BOOLEAN_TYPE, reg, DIRECT); // temporary
	}

	/***************************************************************************
	 * Generate code to compare two expressions. Result (0-1) in Register.
	 * 
	 * @param left an expression (lhs)
	 * @param op a relational operator
	 * @param right an expression (rhs)
	 * @return result expression -0(false) or 1(true) (in register)
	 **************************************************************************/
	Expression compareExpression(final Expression left, final RelationalOperator op, final Expression right) {

		if(!op.validOperands(left, right, err)){
			return new ErrorExpression("$ Incompatible Types");
		}
		if (left instanceof ConstantExpression && right instanceof ConstantExpression){
			op.constantFolding((ConstantExpression)left, (ConstantExpression)right);
		}
		int booleanreg = codegen.getTemp(1);
		int resultreg = codegen.loadRegister(left);
		Location rightLocation = codegen.buildOperands(right);
		codegen.gen2Address(LD, booleanreg, IMMED, UNUSED, 1);
		codegen.gen2Address(IC, resultreg, rightLocation);
		codegen.gen1Address(op.opcode(), PCREL, UNUSED, 4);
		codegen.gen2Address(LD, booleanreg, IMMED, UNUSED, 0);
		codegen.freeTemp(DREG, resultreg);
		codegen.freeTemp(rightLocation);
		return new VariableExpression(BOOLEAN_TYPE, booleanreg, DIRECT); // temporary
	}

	/***************************************************************************
	 * Create a label record with the outlabel for an IF statement.
	 * 
	 * @return GCRecord entry with two label slots for this statement.
	 **************************************************************************/
	GCRecord startIf() {
		
		return new GCRecord(codegen.getLabel(), 0);
	}

	/***************************************************************************
	 * Generate the final label for an IF. (Halt if we fall through to here).
	 * 
	 * @param entry GCRecord holding the labels for this statement.
	 **************************************************************************/
	void endIf(final GCRecord entry) {
		
		codegen.gen0Address(HALT);
		codegen.genLabel('J', entry.outLabel());
	}
	
	/***************************************************************************
	 * Create a label record with the outlabel for a DO statement.
	 * Generate the out label at the beginning.
	 * 
	 * @return GCRecord entry with two label slots for this statement.
	 **************************************************************************/
	GCRecord startDo() {
		
		int startLabel = codegen.getLabel();
		codegen.genLabel('J', startLabel);
		return new GCRecord(startLabel, 0);
	}
	
	/***************************************************************************
	 * Generates code to begin a forall loop.
	 * 
	 * @param control expression over which the loop iterates. Must be RangeType.
	 * @return ForRecord entry with a counter and a label for this statement.
	 **************************************************************************/
	ForRecord startForall(VariableExpression control) {
		
		RangeType bounds = control.type().expectRangeType(err);
		VariableExpression forCounter = new VariableExpression(bounds.baseType(), codegen.loadRegister(control), DIRECT);
		// Initialize the control.
		codegen.gen2Address(LD, forCounter.offset(), IMMED, UNUSED, bounds.lowerBound());
		// Label top of loop.
		int forLabel = codegen.getLabel();
		codegen.genLabel('F', forLabel);
		// Store the counter value to the control address.
		codegen.gen2Address(STO, forCounter.offset(), codegen.buildOperands(control));
		return new ForRecord(bounds, forLabel, forCounter);
	}
	
	/***************************************************************************
	 * Generates code to end a forall loop.
	 * 
	 * @param entry ForRecord holding the counter and label for this statement.
	 **************************************************************************/
	void endForall(final ForRecord entry) {
		
		int controlReg = codegen.loadRegister(entry.control());
		// Increment the control.
		codegen.gen2Address(IA, controlReg, IMMED, UNUSED, 1);
		// Test control in bounds.
		codegen.gen2Address(IC, controlReg, IMMED, UNUSED, entry.bounds().upperBound());
		// Jump to top.
		codegen.genJumpLabel(JLE, 'F', entry.forLabel());
		// Free control register.
		codegen.freeTemp(codegen.buildOperands(entry.control()));
	}

	/***************************************************************************
	 * If the expression represents true, jump to the next else part.
	 * 
	 * @param expression Expression to be tested: must be boolean
	 * @param entry GCRecord with the associated labels. This is updated
	 **************************************************************************/
	void ifTest(final Expression expression, final GCRecord entry) {
		
		int resultreg;
		if(!(expression instanceof GeneralError) && !expression.type().isCompatible(BOOLEAN_TYPE)){
			err.semanticError(GCLError.BOOLEAN_REQUIRED);
			resultreg = codegen.loadRegister(new ErrorExpression("$ Required Boolean Type"));
		}
		else{
			resultreg = codegen.loadRegister(expression);
		}	
		int nextElse = codegen.getLabel();
		entry.nextLabel(nextElse);
		codegen.gen2Address(IC, resultreg, IMMED, UNUSED, 1);
		codegen.genJumpLabel(JNE, 'J', nextElse);
		codegen.freeTemp(DREG, resultreg);
	}

	/***************************************************************************
	 * Generate a jump to the out label and insert the next else label.
	 * 
	 * @param entry GCRecord with the labels
	 **************************************************************************/
	void elseIf(final GCRecord entry) {
		
		codegen.genJumpLabel(JMP, 'J', entry.outLabel());
		codegen.genLabel('J', entry.nextLabel());
	}

	/***************************************************************************
	 * Create a tuple from a list of expressions Both the type and the value must be created.
	 * 
	 * @param tupleFields an expression list with the fields of the tuple
	 * @return an expression representing the tuple value as a whole.
	 **************************************************************************/
	Expression buildTuple(final ExpressionList tupleFields) {
		
		Iterator<Expression> elements = tupleFields.elements();
		TypeList types = new TypeList();
		int address = codegen.variableBlockSize(); // beginning of the tuple
		while (elements.hasNext()) {
			Expression field = elements.next();
			TypeDescriptor aType = field.type();
			types.enter(aType, err);
			int size = aType.size();
			int where = codegen.reserveGlobalAddress(size);
			CompilerOptions.message("Tuple component of size " + size + " at " + where);
			// Now bring all the components together into a contiguous block
			simpleMove(field, INDXD, VARIABLE_BASE, where);
		}
		TupleType tupleType = new TupleType(types);
		return new VariableExpression(tupleType, GLOBAL_LEVEL, address, DIRECT);
	}
	
	/***************************************************************************
	 * Enter the identifier into the symbol table, marking it as a variable of
	 * the given type. This method handles global variables as well as local
	 * variables and procedure parameters.
	 * 
	 * @param scope the current symbol table
	 * @param type the type to be of the variable being defined
	 * @param ID identifier to be defined
	 * @param procParam the kind of procedure param it is (if any).
	 **************************************************************************/
	void declareVariable(final SymbolTable scope, final TypeDescriptor type, final Identifier id, final ParameterKind procParam) {
		
		complainIfDefinedHere(scope, id);
		VariableExpression expr = null;
		if (currentLevel().isGlobal()) { // Global variable
			int addressOffset = codegen.reserveGlobalAddress(type.size());
			expr = new VariableExpression(type, currentLevel().value(), addressOffset, DIRECT);
		}
		else if(procParam == ParameterKind.NOT_PARAM){ // Procedure local
			expr = currentProcedure.reserveLocalAddress(type);
		}
		else{ // Procedure parameter
			expr = currentProcedure.reserveParameterAddress(type, procParam, err);
		}
		SymbolTable.Entry variable = scope.newEntry("variable", id, expr);
		CompilerOptions.message("Entering: " + variable);
	}

	/***************************************************************************
	 * Enter the identifier into the symbol table along with its constant expression.
	 * 
	 * @param scope SymbolTable into which this entry should be added.
	 * @param id Identifier of this constant expression.
	 * @param expression The defined constant expression.
	 **************************************************************************/
	void declareConstant(final SymbolTable scope, final Identifier id, final ConstantExpression expression) {
		
		complainIfDefinedHere(scope, id);
		codegen.buildOperands(expression);
		SymbolTable.Entry constant = scope.newEntry("constant", id, expression);
		CompilerOptions.message("Entering: " + constant);
	}
	
	/***************************************************************************
	 * Enter id into the symbol table, associating it with the given type.
	 * 
	 * @param scope the current symbol table
	 * @param type TypeDescriptor to be entered with id.
	 * @param id Identifier representing type in the symbol table.
	 **************************************************************************/
	void declareTypeDefinition(final SymbolTable scope, final TypeDescriptor type, final Identifier id){
		
		complainIfDefinedHere(scope, id);
		scope.newEntry("type", id, type);
		CompilerOptions.message("Entering: " + type);
	}
	
	/***************************************************************************
	 * Declares a new procedure with its own scope, entering it into the carrier for its tuple.
	 * 
	 * @param procedureId identifier of the new procedure.
	 * @param carrier TypeList used to build this procedure's tuple.
	 * @param outerScope The scope where this procedure's tuple is defined.
	 * @return The new scope of the procedure.
	 **************************************************************************/
	SymbolTable openProcedureDeclaration(final Identifier procedureId, TypeList carrier, final SymbolTable outerScope){
		
		currentLevel.increment();
		currentProcedure = new Procedure(currentProcedure, outerScope.openScope(false), codegen.getLabel(), currentLevel().value());
		carrier.enter(currentProcedure, procedureId);
		return currentProcedure.scope();
	}
	
	/***************************************************************************
	 * Closes procedure declaration and returns to the previous scope.
	 **************************************************************************/
	void closeProcedureDeclaration(){
		
		currentLevel.decrement();
		currentProcedure.scope().closeScope();
		currentProcedure = currentProcedure.parent(); 
	}
	
	/***************************************************************************
	 * Opens the definition block for a procedure
	 * 
	 * @param tupleId the id of the tuple who's procedure is being defined
	 * @param procedureId the id of the procedure which is being defined
	 * @param outerScope the scope in which the tuple is defined
	 * 
	 * @return scope of the procedure to be defined
	 **************************************************************************/
	SymbolTable openProcedureDefinition(final Identifier tupleId, final Identifier procedureId, final SymbolTable outerScope){
				
		SymbolTable tupleScope = tupleScope(semanticValue(outerScope, tupleId));
		SemanticItem procedureItem = semanticValue(tupleScope, procedureId);
		if(procedureItem instanceof GeneralError){ // complain once
			return SymbolTable.unchained();
		}
		Procedure procedure = procedureItem.expectProcedure(err);
		
		if(procedure.defined()){
			err.semanticError(GCLError.ALREADY_DEFINED, tupleId.name() + "@" + procedureId.name() + " already defined.");
			return SymbolTable.unchained();
		}
		
		currentLevel.increment();
		currentProcedure = procedure;
		procedure.scope().restoreProcedureScope(procedure.scope());
		return procedure.scope();
	}
	
	/***************************************************************************
	 * Closes the definition block for the current procedure
	 **************************************************************************/
	void closeProcedureDefinition(){
		
		currentLevel.decrement();
		currentProcedure.generateUnlinkCode(codegen);
		currentProcedure.scope().closeScope();
		currentProcedure = currentProcedure.parent();
	}
	
	/***************************************************************************
	 * Generates the link
	 **************************************************************************/
	void doLink(){
		
		if (currentLevel().isGlobal()){
			codegen.genLabel('M', SymbolTable.currentModule().label());
		}
		else{
			currentProcedure.generateLinkCode(codegen);
		}
	}
	
	/***************************************************************************
	 * Calls a procedure with a list arguments.
	 * 
	 * @param procedure procedure to be called.
	 * @param arguments
	 **************************************************************************/
	void callProcedure(final Expression tuple, final Procedure procedure, final ExpressionList arguments){
		
		if(tuple instanceof GeneralError || procedure instanceof GeneralError){
			return;
		}
		if(!procedure.defined()){
			err.semanticError(GCLError.PROCEDURE_NOT_DEFINED); 
			return;
		}

		int tupleReg = codegen.loadAddress(tuple);
		codegen.gen2Address(IS, STACK_POINTER, IMMED, UNUSED, procedure.frameSize());
		codegen.gen2Address(STO, tupleReg, INDXD, STACK_POINTER, 6);
		codegen.freeTemp(DREG, tupleReg);
		
		int levelDifference = currentLevel().value() - procedure.semanticLevel();
		Location persistedStaticInNewFrame = new Location(INDXD, STACK_POINTER, 2);
		if(levelDifference < 1) {
			codegen.gen2Address(STO, FRAME_POINTER, persistedStaticInNewFrame);
		}
		else if(levelDifference == 1) {
			codegen.gen2Address(STO, STATIC_POINTER, persistedStaticInNewFrame);
		}
		else if(levelDifference > 1) {
			for(int i = 0; i < levelDifference - 1; i++) {
				codegen.gen2Address(LD, STATIC_POINTER, INDXD, STATIC_POINTER, 2);
			}
			codegen.gen2Address(STO, STATIC_POINTER, persistedStaticInNewFrame);
		}

		procedure.call(arguments, codegen, err);
	}
	
	/***************************************************************************
	 * returns a value from a procedure call.
	 **************************************************************************/
	void doReturn(){
		
		if(!currentLevel().isGlobal()){
			codegen.genJumpLabel(JMP, 'U', currentProcedure.label());
		}
		else{
			err.semanticError(GCLError.INVALID_RETURN);
		}
	}
	
	/***************************************************************************
	 * @return this, the tuple parent of the current procedure.
	 **************************************************************************/
	VariableExpression currentProcedureThis(){
		return new VariableExpression(currentProcedure.parentTupleType(), currentProcedure.semanticLevel(), 6, INDIRECT);
	}
	
	/***************************************************************************
	 * Declare Array Type.
	 * 
	 * @param subscripts Stack<RangeType> representing the successive dimensions bounds of this array.
	 * @param componentType The type of elements this ArrayType contains.
	 **************************************************************************/
	TypeDescriptor declareArrayType(java.util.Stack<RangeType> subscripts, final TypeDescriptor componentType){
		
		ArrayType result = new ArrayType(subscripts.pop(), componentType);
		while(!subscripts.empty()){
			result = new ArrayType(subscripts.pop(), result);// recursively chain the ArrayTypes
		}
		return result;
	}
	
	/***************************************************************************
	 * Declare Range Type.
	 * 
	 * @param lowerBound ConstantExpression: lower bound of the range.
	 * @param upperBound ConstantExpression: upper bound of the range.
	 * @param baseType Must be same as lower and upper bounds.
	 **************************************************************************/
	TypeDescriptor declareRangeType(final ConstantExpression lowerBound, final ConstantExpression upperBound, final TypeDescriptor baseType){
		
		boolean valid = true;
		
		// complain on type mismatch
		if(!lowerBound.type().isCompatible(baseType)){
			valid = false;
			err.semanticError(GCLError.TYPE_MISMATCH, baseType.toString() + " expected as lower bound");
		}
		// complain on type mismatch
		if(!upperBound.type().isCompatible(baseType)){
			valid = false;
			err.semanticError(GCLError.TYPE_MISMATCH, baseType.toString() + " expected as upper bound");
		}
		// complain lower > upper
		if(lowerBound.value() > upperBound.value()){
			valid = false;
			err.semanticError(GCLError.ILLEGAL_RANGE, "lower bound (" + lowerBound.value() + ")cannot be greater than upper bound (" + upperBound.value() + ")");
		}
		if(valid){
			// declare lower and upper constants
			Location lowerLocation = codegen.buildOperands(lowerBound);
			codegen.buildOperands(upperBound);
			
			// declare and return rangetype
			return new RangeType(baseType, lowerBound.value(), upperBound.value(), lowerLocation);
		}
		return ErrorType.NO_TYPE;
	}
	
	/***************************************************************************
	 * Subscript.
	 * 
	 * @param arrayExpression an expression of type array.
	 * @param subscript index within array.
	 * @return an expression indicated by subscript from array.
	 **************************************************************************/
	Expression subscript(VariableExpression arrayExpression, Expression subscript){
		
		if(arrayExpression instanceof GeneralError || subscript instanceof GeneralError){
			return new ErrorExpression("$ Incompatible Types.");
		}
		if(arrayExpression.type() instanceof ArrayType){
			ArrayType arrayType = (ArrayType)arrayExpression.type();
			// complain on subscript type mismatch.
			if(!arrayType.subscriptType().isCompatible(subscript.type())){
				err.semanticError(GCLError.TYPE_MISMATCH);
				return new ErrorExpression("$ Incompatible Subscript Type.");
			}
			// constant subscript
			if(subscript instanceof ConstantExpression){
				// bounds check
				ConstantExpression constantSubscript = subscript.expectConstantExpression(err);
				if(!arrayType.subscriptType().constantFolding(constantSubscript, err)){
					return new ErrorExpression("$ Subscript out of range.");
				}
				// inset value
				int inset = (constantSubscript.value() - arrayType.subscriptType().lowerBound()) * arrayType.subscriptType().size();
				// access value
				if(arrayExpression.case1()){
					return new VariableExpression(arrayType.componentType(), arrayExpression.semanticLevel(), arrayExpression.offset() + inset, DIRECT);
				}
				if(arrayExpression.case2()){
					int arrayAddressOffset = codegen.loadAddress(arrayExpression);
					if(inset != 0){ 
						codegen.gen2Address(IA, arrayAddressOffset, IMMED, UNUSED, inset);
					}
					return new VariableExpression(arrayType.componentType(), arrayExpression.semanticLevel(), arrayAddressOffset, INDIRECT);
				}
				if(arrayExpression.case3()){
					int arrayAddressOffset = codegen.loadPointer(arrayExpression);
					if(inset != 0){ 
						codegen.gen2Address(IA, arrayAddressOffset, IMMED, UNUSED, inset);
					}
					return new VariableExpression(arrayType.componentType(), 0, arrayAddressOffset, INDIRECT);
				}
				err.semanticError(GCLError.ILLEGAL_ARRAY_ACCESS);
				return new ErrorExpression("$ Unable to access array member.");
			}
			// variable subscript
			else{
				// bounds check
				VariableExpression variableSubscript = subscript.expectVariableExpression(err);
				int subscriptRegister = codegen.loadRegister(variableSubscript);
				codegen.gen2Address(TRNG, subscriptRegister, arrayType.subscriptType().location());
				// inset value (offset is stored in arrayRegister)
				codegen.gen2Address(IS, subscriptRegister, arrayType.subscriptType().location());
				codegen.gen2Address(IM, subscriptRegister, IMMED, UNUSED, arrayType.componentType().size());
				int arrayRegister = codegen.loadAddress(arrayExpression);
				codegen.gen2Address(IA, arrayRegister, DREG, subscriptRegister, UNUSED);
				codegen.freeTemp(DREG, subscriptRegister);
				// access value
				return new VariableExpression(arrayType.componentType(), arrayRegister, INDIRECT);
			}
		}
		err.semanticError(GCLError.ARRAY_REQUIRED);
		return new ErrorExpression("$ Array required.");
	}
	
	/***************************************************************************
	 * tuple field.
	 * 
	 * @param tupleExpression an expression of type tuple.
	 * @param fieldName identifier of a tuple member.
	 * @return tupleExpression@fieldName
	 **************************************************************************/
	Expression tupleComponent(VariableExpression tupleExpression, Identifier fieldName){
		
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
			int tupleAddressOffset = codegen.loadAddress(tupleExpression);
			if(fieldInset != 0){
				codegen.gen2Address(IA, tupleAddressOffset, IMMED, 0, fieldInset);
			}
			return new VariableExpression(fieldType, tupleExpression.semanticLevel(), tupleAddressOffset, INDIRECT);
		}
		if(tupleExpression.case3()){
			int tupleAddressOffset = codegen.loadPointer(tupleExpression);
			if(fieldInset != 0){
				codegen.gen2Address(IA, tupleAddressOffset, IMMED, 0, fieldInset);
			}
			return new VariableExpression(fieldType, 0, tupleAddressOffset, INDIRECT);
		}
		err.semanticError(GCLError.ILLEGAL_TUPLE_ACCESS);
		return new ErrorExpression("$ Unable to extract tuple component.");
	}
	
	/***************************************************************************
	 * Complain if invalid spelling.
	 **************************************************************************/
	void checkIdentifierSpelling(final String identifier){
		
		if(!Identifier.isValid(identifier)){
	   		err.semanticError(GCLError.ILLEGAL_IDENTIFIER);
	    }
	}
	
	/***************************************************************************
	 * Inserts a comment in the listing file.
	 * TODO remove after debugging.
	 **************************************************************************/
	void insertComment(String message){
		
		codegen.genCodeComment(message);
	}
	
	/***************************************************************************
	 * @param tupleTypeOrInstance Either a TupleType or an expression with type, TupleType
	 * @return the tuple scope of either a tuple type or instance
	 **************************************************************************/
	private SymbolTable tupleScope(SemanticItem tupleTypeOrInstance){
		if(tupleTypeOrInstance instanceof Expression){
			return tupleTypeOrInstance.expectExpression(err)
								      .expectVariableExpression(err)
								      .type()
								      .expectTupleType(err)
								      .methods();
		}
		if(tupleTypeOrInstance instanceof TupleType){
			return tupleTypeOrInstance.expectTypeDescriptor(err)
									  .expectTupleType(err)
									  .methods();
		}
		err.semanticError(GCLError.TUPLE_REQUIRED, "tuple type or instance required.");
		return SymbolTable.unchained();
	}

	/***************************************************************************
	 * Set up the registers and other run time initializations.
	 **************************************************************************/
	void startCode() {
		
		codegen.genCodePreamble();
	}

	/***************************************************************************
	 * Write out the termination code, Including constant defs and global
	 * variables.
	 **************************************************************************/
	void finishCode() {
		
		codegen.genCodePostamble();
	}

	/***************************************************************************
	 * Get a reference to the object that maintains the current semantic (procedure nesting) level.
	 * 
	 * @return the current semantic level object.
	 **************************************************************************/
	SemanticLevel currentLevel() {
		
		return currentLevel;
	}

	/***************************************************************************
	 * Objects of this class represent the semantic level at which the compiler
	 * is currently translating. The global level is the level of modules. Level
	 * 2 is the level of procedures. Level 3... are the levels of nested
	 * procedures. Each item declared at a level is tagged with the level number
	 * at its declaration. These numbers are used by the compiler to set up the
	 * runtime so that non-local variables (and other items) can be found at
	 * runtime.
	 **************************************************************************/
	static class SemanticLevel {
		private int currentLevel = GLOBAL_LEVEL;// Never less than one. Current

		// procedure nest level

		/**
		 * The semantic level's integer value
		 * 
		 * @return the semantic level as an int. Never less than one.
		 */
		public int value() {
			return currentLevel;
		}

		/**
		 * Determine if the semantic level represents the global (i.e. 1) level.
		 * 
		 * @return true if the level is global. False if it is procedural at any level.
		 */
		public boolean isGlobal() {
			return currentLevel == GLOBAL_LEVEL;
		}

		private void increment() {
			currentLevel++;
		}

		private void decrement() {
			currentLevel--;
		}

		private SemanticLevel() {
		// nothing. 
		}
		// can create a new object only within the containing class
	}

	GCLErrorStream err() {
		return err;
	}

	public final void init() {
		currentLevel = new SemanticLevel();
		codegen.setSemanticLevel(currentLevel());
	}
}// SemanticActions