package gcl;
//TODO test0 and test8 doesn't recognize tuple type compatibility.
//TODO Require VariableExpression in test 10 (3rd error)
//TODO Deal with the runtime errors in 10_1 (there should only be 1 in the result file)
//TODO Treat subscripts all together rather than loading each dimension.
import gcl.Codegen.ConstantLike;
import gcl.Codegen.Location;
import gcl.SemanticActions.GCLErrorStream;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashMap;
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
	public TypeDescriptor expectTypeDescriptor(final SemanticActions.GCLErrorStream err) {
		err.semanticError(GCLError.TYPE_REQUIRED);
		return ErrorType.NO_TYPE;
	}
	
	/**
	 * Soft Cast
	 * @return "this" if it is an expression and an ErrorExpression otherwise.
	 */
	public Expression expectExpression(final SemanticActions.GCLErrorStream err) {
		err.semanticError(GCLError.EXPRESSION_REQUIRED);
		return new ErrorExpression("$ Expression Required");
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

	@Override
	public Expression expectExpression(final SemanticActions.GCLErrorStream err) {
		// Don't complain on error records. The complaint previously occurred when this object was created.
		return new ErrorExpression("$ Expression Required");
	}
	
	@Override
	public TypeDescriptor expectTypeDescriptor(final SemanticActions.GCLErrorStream err) {
		// Don't complain on error records. The complaint previously occurred when this object was created.
		return ErrorType.NO_TYPE;
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
	
	public abstract ConstantExpression constantFolding(ConstantExpression left, ConstantExpression right);
	/**
	 * Tells the caller if the operands are valid and complains if necessary. NOTE: It is the callers responsibility to return an ErrorExpression.
	 * @param left operand to the left.
	 * @param right operand to the right.
	 * @param err GCLErrorStream on which to complain. 
	 * @return true if the operands are valid; false otherwise.
	 */
	public abstract boolean validOperands(Expression left, Expression right, GCLErrorStream err);

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
	
	@Override
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
	
	@Override
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
	
	@Override
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

class StringConstant extends SemanticItem implements ConstantLike {
	
	public StringConstant(String gclString) {
		
		samString = convertToSamString(gclString);
		size = 2*(gclString.length()/2);
	}
	
	public String samString(){
		return samString;
	}
	
	public int size(){
		return size;
	}
	
	private final String convertToSamString(String gclString){
		
		return "\"" + gclString.substring(1, gclString.length()-1).replaceAll("\\\\", "\\").replaceAll(":", "::").replaceAll("\"", ":\"") + "\"";
	}
	
	private String samString;
	private int size;
}

/**
 * Root of the expression object hierarchy. Represent integer and boolean expressions.
 */
abstract class Expression extends SemanticItem implements Codegen.MaccSaveable {
	public Expression(final TypeDescriptor type, final int level) {
		super(level);
		this.type = type;
	}

	/**
	 * Polymorphically determine if an expression needs to be pushed on the run
	 * stack as part of parallel assignment.
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
	public ConstantExpression expectConstantExpression(final SemanticActions.GCLErrorStream err) {
		err.semanticError(GCLError.CONSTANT_REQUIRED);
		return new ErrorConstantExpression("$ ConstantExpression Required");
	}
	
	/**
	 * Soft Cast
	 * @return "this" if it is a VariableExpression and an ErrorVariableExpression otherwise.
	 */
	public VariableExpression expectVariableExpression(final SemanticActions.GCLErrorStream err) {
		err.semanticError(GCLError.VARIABLE_REQUIRED);
		return new ErrorVariableExpression("$ VariableExpression Required");
	}

	@Override
	public Expression expectExpression(final SemanticActions.GCLErrorStream err) {
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
	
	@Override
	public ConstantExpression expectConstantExpression(GCLErrorStream err){
		return new ErrorConstantExpression("$ Requires Constant Expression");
	}
	
	@Override
	public VariableExpression expectVariableExpression(GCLErrorStream err){
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
class ConstantExpression extends Expression implements CodegenConstants, Codegen.ConstantLike {
	
	private final int value;

	public ConstantExpression(final TypeDescriptor type, final int value) {
		super(type, GLOBAL_LEVEL);
		this.value = value;
	}
	
	@Override
	public ConstantExpression expectConstantExpression(final SemanticActions.GCLErrorStream err) {
		return this;
	}

	public String toString() {
		return "ConstantExpression: " + value + " with type " + type();
	}

	public boolean equals(Object other) {
		return (other instanceof ConstantExpression)
				&& type().baseType().isCompatible(
						((ConstantExpression) other).type().baseType())
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
class ErrorConstantExpression extends ConstantExpression implements GeneralError,
		CodegenConstants {
	
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
	
	private final int offset; // relative offset of cell or register number
	private final boolean isDirect; // if false this is a pointer to a location.
	
	/**
	 * Create a variable expression object
	 * 
	 * @param type the type of this variable
	 * @param scope the nesting level (if >0) or 0 for a register, or -1 for stacktop
	 * @param offset the relative offset of the cells of this variable, or the
	 *            register number if scope is 0
	 * @param direct if false this represents a pointer to the variable
	 */
	public VariableExpression(final TypeDescriptor type, final int level, final int offset,
			final boolean direct) {
		super(type, level);
		this.offset = offset;
		this.isDirect = direct;
	}

	/**
	 * Create a temporary expression. The level is 0 and the offset is the
	 * register number
	 * 
	 * @param type the type of this value
	 * @param register the register number in which to hold it
	 * @param direct is the value in the register (true) or a pointer (false)
	 */
	public VariableExpression(final TypeDescriptor type, final int register, final boolean direct) {
		this(type, 0, register, direct);
	}
	
	/**
	 * @return level > 0 AND direct
	 */
	public boolean case1(){
		return (semanticLevel() > 0 && isDirect);
	}

	/**
	 * @return level == 0 AND indirect
	 */
	public boolean case2(){
		return (semanticLevel() == 0 && !isDirect);
	}
	
	/**
	 * @return level > 0 AND indirect
	 */
	public boolean case3(){
		return (semanticLevel() > 0 && !isDirect);
	}

	@Override
	public VariableExpression expectVariableExpression(GCLErrorStream err){
		return this;
	}
	
	public boolean needsToBePushed() { // used by parallel assignment
		return semanticLevel() > CPU_LEVEL
				|| (semanticLevel() == CPU_LEVEL && !isDirect);
	}

	/**
	 * The relative address of the variable. What it is relative to depends on
	 * its scopeLevel. If the level is 1 it is relative to R15.
	 * 
	 * @return the relative offset from its base register.
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
class ErrorVariableExpression extends VariableExpression implements GeneralError,
		CodegenConstants {
	
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

/**
 * Specifies the kind of procedure parameter. The value NOT_PARAM is used for
 * variables that are not parameters. Typesafe enumeration
 */
class ParameterKind extends SemanticItem {
	private ParameterKind() {
	}

	public static final ParameterKind NOT_PARAM = new ParameterKind();
	// more later
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
	private VariableExpression counter;
	
	public ForRecord(RangeType bounds, int forLabel, VariableExpression counter){
		this.bounds = bounds;
		this.forLabel = forLabel;
		this.counter = counter;
	}
	
	public RangeType bounds(){
		return bounds;
	}
	
	public int forLabel(){
		return forLabel;
	}
	
	public VariableExpression counter(){
		return counter;
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
	
	@Override
	public TypeDescriptor expectTypeDescriptor(final SemanticActions.GCLErrorStream err){
		return this;
	}
	
	/**
	 * Soft Cast
	 * @return "this" if it is a range type and a NO_TYPE otherwise.
	 */
	public RangeType expectRangeType(final SemanticActions.GCLErrorStream err) {
		err.semanticError(GCLError.RANGE_REQUIRED);
		return ErrorRangeType.NO_TYPE;
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
	
	@Override
	public RangeType expectRangeType(final SemanticActions.GCLErrorStream err) {
		return ErrorRangeType.NO_TYPE;
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
			err.semanticError(GCLError.VALUE_OUT_OF_RANGE);
			return false;
		}
		return true;
	}
	
	@Override
	public RangeType expectRangeType(final SemanticActions.GCLErrorStream err){
		return this;
	}

	@Override
	public TypeDescriptor baseType(){
		return baseType;
	}
	
	@Override
	public String toString(){
		return "rangetype: " + baseType.toString();
	}
	
	@Override
	public boolean isCompatible(final TypeDescriptor other) {
		return baseType.isCompatible(other);
	}
}

/** Used to represent errors where ErrorRangeType are expected. Immutable. */
class ErrorRangeType extends RangeType implements GeneralError, CodegenConstants {

	public ErrorRangeType() {
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

	@Override
	public TypeDescriptor baseType(){
		return this;
	}
	
	@Override
	public String toString(){
		return "arraytype: " + componentType.toString() + " [" + subscriptType.lowerBound() + ".." + subscriptType.upperBound() + "]";
	}
	
	@Override
	public boolean isCompatible(final TypeDescriptor other) {
		return componentType.isCompatible(other);
	}
}

/**
 * Use this when you need to build a list of types and know the total size of
 * all of them. Used in creation of tuples.
 */
class TypeList extends SemanticItem {
	
	private final ArrayList<TypeDescriptor> elements = new ArrayList<TypeDescriptor>(2);
	private final ArrayList<Identifier> names = new ArrayList<Identifier>(2);
	private int size = 0; // sum of the sizes of the types
	private static int next = 0;
	
	/**
	 * Add a new type-name pair to the list and accumulate its size
	 * 
	 * @param aType the type to be added
	 * @param name the name associated with the field
	 */
	public void enter(final TypeDescriptor aType, final Identifier name) {
		elements.add(aType);
		names.add(name);
		size += aType.size();
	} // TODO check that the names are distinct.

	/**
	 * Add a new type to the list, using a default name This is used to define
	 * anonymous fields in a tuple value
	 * 
	 * @param aType the type of the entry to be added
	 */
	public void enter(final TypeDescriptor aType) {
		enter(aType, new Identifier("none_" + next));
		next++; // unique "names" for anonymous fields.
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
}

/**
 * Represents the various tuple types. Created as needed. These are built
 * incrementally and locked at the end to make them immutable afterwards.
 */
class TupleType extends TypeDescriptor { // mutable
	// TODO must override isCompatible
	
	private final HashMap<Identifier, TupleField> fields = new HashMap<Identifier, TupleField>(4);
	private final ArrayList<Identifier> names = new ArrayList<Identifier>(4);
	private SymbolTable methods = null; // later

	/**
	 * Create a tuple type from a list of its component types. We will need to
	 * add the "methods" to this later.
	 * 
	 * @param carrier the list of component types
	 */
	public TupleType(final TypeList carrier) {
		super(carrier.size());
		methods = SymbolTable.unchained();
		Iterator<TypeDescriptor> e = carrier.elements();
		Iterator<Identifier> n = carrier.names();
		int inset = 0;
		while (e.hasNext()) {
			TypeDescriptor t = e.next();
			Identifier id = n.next();
			fields.put(id, new TupleField(inset, t));
			inset += t.size();
			names.add(id);
		}
	}

	public String toString() {
		String result = "tupleType:[";
		for (int i = 0; i < fields.size(); ++i) {
			result += fields.get(names.get(i)) + " : "
					+ names.get(i) + ", ";// type);
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
	 * @param fieldName
	 *            the name of the desired component type
	 * @return the type of the named component
	 */
	public TypeDescriptor getType(final Identifier fieldName) { // null return value possible
	
		return fields.get(fieldName).type();
	}

	// TODO must override isCompatible

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
	static final GCLError INTEGER_REQUIRED = new Value(1,
			"ERROR -> Integer type required. ");
	static final GCLError ALREADY_DEFINED = new Value(2,
			"ERROR -> The item is already defined. ");
	static final GCLError NAME_NOT_DEFINED = new Value(3,
			"ERROR -> The name is not defined. ");
	static final GCLError TYPE_REQUIRED = new Value(4,
			"ERROR -> TypeReference name required. ");
	static final GCLError LISTS_MUST_MATCH = new Value(5,
			"ERROR -> List lengths must be the same. ");
	static final GCLError NOT_VARIABLE = new Value(6,
			"ERROR -> The Left Hand Side is not a variable access. ");
	static final GCLError EXPRESSION_REQUIRED = new Value(7,
			"ERROR -> Expression required. ");
	static final GCLError ILLEGAL_IDENTIFIER = new Value(8,
			"ERROR -> Illegal Spelling of Identifer. ");
	static final GCLError TYPE_MISMATCH = new Value(9,
			"ERROR -> Incompatible Types. ");
	static final GCLError CONSTANT_REQUIRED = new Value(10,
			"ERROR -> Constant expression required. ");
	static final GCLError ILLEGAL_RANGE = new Value(11,
			"ERROR -> Illegal Range. ");
	static final GCLError VALUE_OUT_OF_RANGE = new Value(12,
			"ERROR -> Value out of acceptable range. ");
	static final GCLError BOOLEAN_REQUIRED = new Value(13,
			"ERROR -> Boolean type required. ");
	static final GCLError VARIABLE_REQUIRED = new Value(14,
			"ERROR -> Variable expression required. ");
	static final GCLError ARRAY_REQUIRED = new Value(15,
			"ERROR -> Array expression required. ");
	static final GCLError RANGE_REQUIRED = new Value(16,
			"ERROR -> Range type required. ");
		
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

	/***************************************************************
	 * Auxiliary Determine if a symboltable entry can safely be
	 * redefined at this point. Only one definition is legal in a given scope.
	 * 
	 * @param entry a symbol table entry to be checked.
	 * @return true if it is ok to redefine this entry at this point.
	 */
	private boolean OKToRedefine(final SymbolTable.Entry entry) {
		if(entry == SymbolTable.NULL_ENTRY){
			return true;
		}
		return false; // more later
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
		Codegen.Location sourceLocation = codegen.buildOperands(source);
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
	private void moveBlock(final Expression source, final Codegen.Mode mode, final int base,
			final int displacement) {
		if (source instanceof ErrorExpression) {
			return;
		}
		int size = source.type().size();
		int reg = codegen.getTemp(2); // need 2 registers for BKT
		Codegen.Location sourceLocation = codegen.buildOperands(source);
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
	private void moveBlock(final Codegen.Mode mode, final int base, final int displacement,
			final Expression destination) {
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
		Codegen.Location destinationLocation = codegen
				.buildOperands(destination);
		codegen.gen2Address(BKT, reg, destinationLocation);
		codegen.freeTemp(DREG, reg);
		codegen.freeTemp(destinationLocation);
	}
	
	/** Set a bit of a word corresponding to a register number.
	 * @param reg the register to transform
	 * @return an integer with one bit set
	 */
	private int regToBits(final int reg){
		return (int)Math.pow(2, reg);
	}

	/**
	 * auxiliary Push an expression onto the run time stack
	 * 
	 * @param source the expression to be pushed
	 */
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

	/**
	 * **************** auxiliary Pop an expression from the run time stack into
	 * a given destination
	 * 
	 * @param destination
	 *            the destination for the pop
	 */
	private void popExpression(final Expression destination) {
		if (destination.type().size() == INT_SIZE) {
			int reg = codegen.getTemp(1);
			codegen.genPopRegister(reg);
			Codegen.Location destinationLocation = codegen
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

	/**
	 * auxiliary Move the value of an expression from its
	 * source to a destination
	 * 
	 * @param source the source of the expression
	 * @param destination the destination to which to move the value
	 */
	private void simpleMove(final Expression source, final Expression destination) {
		if (destination.type().size() == INT_SIZE) {
			int reg = codegen.loadRegister(source);
			Codegen.Location destinationLocation = codegen .buildOperands(destination);
			codegen.gen2Address(STO, reg, destinationLocation);
			codegen.freeTemp(DREG, reg);
			codegen.freeTemp(destinationLocation);
		} else {
			moveBlock(source, destination);
		}
	}

	/**
	 * **************** auxiliary Move the value of an expression from a source
	 * to a destination
	 * 
	 * @param source the source of the move
	 * @param mode the mode of the destination's location
	 * @param base the base of the destination location
	 * @param displacement the displacement of the destination location
	 */
	private void simpleMove(final Expression source, final Codegen.Mode mode, final int base,
			final int displacement) {
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
	 */
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
	 * Generate code for an assignment. Copy the RHS expressions to the
	 * corresponding LHS variables.
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
			if (rightExpression.needsToBePushed()) {
				pushExpression(rightExpression);
			}
		}
		// part 3. popping the items pushed in part 2 & copying the rest
		for (i = entries - 1; i >= 0; --i) {
			Expression rightExpression = expressions.right(i);
			Expression leftExpression = expressions.left(i);
			// TRNG for assignment to RangeType expressions. runtime bounds check.
			if (leftExpression.type() instanceof RangeType && !(rightExpression instanceof ConstantExpression)){
				codegen.gen2Address(TRNG, codegen.loadRegister(rightExpression), leftExpression.type().expectRangeType(err).location());
			}
			if (rightExpression.needsToBePushed()) {
				popExpression(leftExpression);
			} else { // the item wasn't pushed, so normal copy
				simpleMove(rightExpression, leftExpression);
			}
		}
	}

	/***************************************************************************
	 * Generate code to read into an integer variable. (Must be an assignable
	 * variable)
	 * 
	 * @param expression
	 *            (integer variable) expression
	 **************************************************************************/
	void readVariable(final Expression expression) {
		if (expression instanceof GeneralError) {
			return;
		}
		if (!expression.type().isCompatible(INTEGER_TYPE)) {
			err.semanticError(GCLError.INTEGER_REQUIRED, "   while Reading" + expression.type().toString());
			return;
		}
		Codegen.Location expressionLocation = codegen.buildOperands(expression);
		codegen.gen1Address(RDI, expressionLocation);
		codegen.freeTemp(expressionLocation);
	}

	/***************************************************************************
	 * Generate code to write a StringConstant.
	 * 
	 * @param expression (integer) expression
	 **************************************************************************/
	void writeString(final StringConstant stringConstant) {
		if (stringConstant instanceof GeneralError) {
			return;
		}
		Codegen.Location stringLocation = codegen.buildOperands(stringConstant);
		codegen.gen1Address(WRST, stringLocation);
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
		Codegen.Location expressionLocation = codegen.buildOperands(expression);
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
		Codegen.Location rightLocation = codegen.buildOperands(right);
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
		Codegen.Location expressionLocation = codegen.buildOperands(expression);
		int reg = codegen.getTemp(1);
		codegen.gen2Address(INEG, reg, expressionLocation);
		codegen.freeTemp(expressionLocation);
		return new VariableExpression(INTEGER_TYPE, reg, DIRECT); // temporary
	}
	
	/***************************************************************************
	 * Generate code to negate a boolean expression. Result in Register.
	 * 
	 * @param expression expression to be negated -must be boolean
	 * @return result expression ~boolean (in register)
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
		Codegen.Location expressionLocation = codegen.buildOperands(booleanExpression);
		codegen.gen2Address(IS, reg, expressionLocation);
		codegen.freeTemp(expressionLocation);
		return new VariableExpression(INTEGER_TYPE, reg, DIRECT); // temporary
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
		Codegen.Location rightLocation = codegen.buildOperands(right);
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
		
		Codegen.Location rightLocation = codegen.buildOperands(right);
		codegen.gen2Address(ID, regInner, rightLocation);
		codegen.gen2Address(IM, regInner, rightLocation);
		codegen.gen2Address(IS, regOuter, DREG, regInner, UNUSED);
		codegen.freeTemp(rightLocation);
		codegen.freeTemp(DREG, regInner);
		return new VariableExpression(INTEGER_TYPE, regOuter, DIRECT); // temporary
	}
	
	/***************************************************************************
	 * Generate code to add two boolean expressions. Result in Register.
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
		Codegen.Location rightLocation = codegen.buildOperands(right);
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
		Codegen.Location rightLocation = codegen.buildOperands(right);
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
		Codegen.Location rightLocation = codegen.buildOperands(right);
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
	 * Create a ForRecord for the for loop.
	 * Generates and initializes the counter; 
	 * Generate the for label at the beginning.
	 * 
	 * @return ForRecord entry with a counter and a label for this statement.
	 **************************************************************************/
	ForRecord startForall(Expression control, SemanticActions.GCLErrorStream err) {
		
		RangeType bounds = control.type().expectRangeType(err);
		VariableExpression forCounter = new VariableExpression(bounds.baseType(), codegen.getTemp(1), DIRECT);
		codegen.gen2Address(LD, forCounter.offset(), "#" + bounds.lowerBound());
		int forLabel = codegen.getLabel();
		codegen.genLabel('F', forLabel);
		codegen.gen2Address(STO, forCounter.offset(), codegen.buildOperands(control));
		return new ForRecord(bounds, forLabel, forCounter);
	}
	
	/***************************************************************************
	 * Increments the for counter.
	 * Tests counter in bounds.
	 * Jumps to top.
	 * Frees counter.
	 * 
	 * @param entry ForRecord holding the counter and label for this statement.
	 **************************************************************************/
	void endForall(final ForRecord entry, SemanticActions.GCLErrorStream err) {
		
		codegen.gen2Address(INC, entry.counter().offset(), "1");
		codegen.gen2Address(IC, entry.counter().offset(), "#" + entry.bounds().upperBound());
		codegen.genJumpLabel(JLE, 'F', entry.forLabel());
		codegen.freeTemp(codegen.buildOperands(entry.counter()));
	}

	/***************************************************************************
	 * If the expr represents true, jump to the next else part.
	 * 
	 * @param expression Expression to be tested: must be boolean
	 * @param entry GCRecord with the associated labels. This is updated
	 **************************************************************************/
	void ifTest(final Expression expression, final GCRecord entry) {
		
		int resultreg = codegen.loadRegister(expression);
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
	 * Create a tuple from a list of expressions Both the type and the value
	 * must be created.
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
			types.enter(aType);
			int size = aType.size();
			int where = codegen.reserveGlobalAddress(size);
			CompilerOptions.message("Tuple component of size " + size + " at "
					+ where);
			// Now bring all the components together into a contiguous block
			simpleMove(field, INDXD, VARIABLE_BASE, where);
		}
		TupleType tupleType = new TupleType(types);
		return new VariableExpression(tupleType, GLOBAL_LEVEL, address, DIRECT);
	}

	/***************************************************************************
	 * Enter the identifier into the symbol table, marking it as a constant of
	 * the given type.
	 * 
	 * @param scope the current symbol table
	 * @param type the type to be of the constant being defined
	 * @param ID identifier to be defined
	 **************************************************************************/
	void declareConstant(final SymbolTable scope, final Identifier id, final ConstantExpression expr) {
		
		complainIfDefinedHere(scope, id);
		codegen.buildOperands(expr);
		SymbolTable.Entry constant = scope.newEntry("constant", id, expr);
		CompilerOptions.message("Entering: " + constant);
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
		} else { // may be param or local in a proc
			// more later -- for now throw an exception
			throw new IllegalStateException("Missing code in declareVariable.");
		}
		SymbolTable.Entry variable = scope.newEntry("variable", id, expr);
		CompilerOptions.message("Entering: " + variable);
	}

	/***************************************************************************
	 * Declare Array Type.
	 **************************************************************************/
	TypeDescriptor declareArrayType(java.util.Stack<RangeType> subscripts, final TypeDescriptor componentType){
		
		ArrayType result = new ArrayType(subscripts.pop(), componentType);
		while(!subscripts.empty()){
			result = new ArrayType(subscripts.pop(), result);// recursively chain the ArrayTypes
		}
		return result;
	}
	
	/***************************************************************************
	 * Subscript.
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
						codegen.gen2Address(IA, arrayAddressOffset, "#" + inset);
					}
					return new VariableExpression(arrayType.componentType(), arrayExpression.semanticLevel(), arrayAddressOffset, INDIRECT);
				}
				if(arrayExpression.case3()){
					int arrayAddressOffset = codegen.loadPointer(arrayExpression);
					if(inset != 0){ 
						codegen.gen2Address(IA, arrayAddressOffset, "#" + inset);
					}
					return new VariableExpression(arrayType.componentType(), 0, arrayAddressOffset, INDIRECT);
				}
				err.semanticError(GCLError.ILLEGAL_ARRAY_ACCESS);
				return new ErrorExpression("$ array[constant subscript] - Case 4");
			}
			// variable subscript
			else{
				// bounds check
				VariableExpression variableSubscript = subscript.expectVariableExpression(err);
				int subscriptRegister = codegen.loadRegister(variableSubscript);
				codegen.gen2Address(TRNG, subscriptRegister, arrayType.subscriptType().location());
				// inset value (offset is stored in arrayRegister)
				codegen.gen2Address(IS, subscriptRegister, arrayType.subscriptType().location());
				codegen.gen2Address(IM, subscriptRegister, "#" + arrayType.componentType().size());
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
	 * Declare Range Type.
	 **************************************************************************/
	TypeDescriptor declareRangeType(final Expression lowerBound, final Expression upperBound, final TypeDescriptor baseType){
		
		boolean valid = true;
		
		// complain on type mismatch
		if(!lowerBound.type().isCompatible(baseType)){
			valid = false;
			err.semanticError(GCLError.TYPE_MISMATCH, baseType.toString() + " expected as lower bound");
		}
		if(!upperBound.type().isCompatible(baseType)){
			valid = false;
			err.semanticError(GCLError.TYPE_MISMATCH, baseType.toString() + " expected as upper bound");
		}
		// complain lower not constant
		if(!(lowerBound instanceof ConstantExpression)){
			valid = false;
			err.semanticError(GCLError.CONSTANT_REQUIRED, "lower bound");
		}
		// complain upper not constant
		if(!(upperBound instanceof ConstantExpression)){
			valid = false;
			err.semanticError(GCLError.CONSTANT_REQUIRED, "upper bound");
		}
		if(valid){
			// safe hard cast
			ConstantExpression lower = (ConstantExpression)lowerBound;
			ConstantExpression upper = (ConstantExpression)upperBound;
			
			// complain lower > upper
			if(lower.value() > upper.value()){
				err.semanticError(GCLError.ILLEGAL_RANGE, "lower bound (" + lower.value() + ")cannot be greater than upper bound (" + upper.value() + ")"); 
				valid = false;
			}
			if(valid){
				// declare lower and upper constants
				Location lowerLocation = codegen.buildOperands(lower);
				codegen.buildOperands(upper);
				
				// declare and return rangetype
				return new RangeType(baseType, lower.value(), upper.value(), lowerLocation);
			}
			return ErrorType.NO_TYPE;
		}
		return ErrorType.NO_TYPE;
	}
	
	/***************************************************************************
	 * Enter the identifier into the symbol table, marking it as a type of
	 * the given type.
	 * 
	 * @param scope the current symbol table
	 * @param type the type to be of the typedefinition being defined
	 * @param ID identifier to be defined
	 **************************************************************************/
	void declareTypeDefinition(final SymbolTable scope, final TypeDescriptor type, final Identifier id){
		
		complainIfDefinedHere(scope, id);
		scope.newEntry("type", id, type);
		CompilerOptions.message("Entering: " + type);
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

	/**
	 * Get a reference to the object that maintains the current semantic
	 * (procedure nesting) level.
	 * 
	 * @return the current semantic level object.
	 */
	SemanticLevel currentLevel() {
		
		return currentLevel;
	}

	/**
	 * Objects of this class represent the semantic level at which the compiler
	 * is currently translating. The global level is the level of modules. Level
	 * 2 is the level of procedures. Level 3... are the levels of nested
	 * procedures. Each item declared at a level is tagged with the level number
	 * at its declaration. These numbers are used by the compiler to set up the
	 * runtime so that non-local variables (and other items) can be found at
	 * runtime.
	 */
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
		 * @return true if the level is global. False if it is procedural at any
		 *         level.
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