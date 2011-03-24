package gcl;

public class Parser {
// GCL Version 2011
	public static final int _EOF = 0;
	public static final int _identifier = 1;
	public static final int _numeral = 2;
	public static final int _gclString = 3;
	public static final int maxT = 45;
	public static final int _option1 = 46;
	public static final int _option3 = 47;
	public static final int _option5 = 48;
	public static final int _option6 = 49;
	public static final int _option7 = 50;
	public static final int _option9 = 51;
	public static final int _option10 = 52;

	static final boolean T = true;
	static final boolean x = false;
	static final int minErrDist = 2;

	public Token t;    // last recognized token
	public Token la;   // lookahead token
	int errDist = minErrDist;
	
	private Scanner scanner;
	private Errors errors;

	static final boolean DIRECT = CodegenConstants.DIRECT;
		static final boolean INDIRECT = CodegenConstants.INDIRECT;
		IntegerType integerType = SemanticActions.INTEGER_TYPE;
		BooleanType booleanType = SemanticActions.BOOLEAN_TYPE;
		TypeDescriptor noType = SemanticActions.NO_TYPE;

/*==========================================================*/


	public Parser(Scanner scanner, SemanticActions actions, SemanticActions.GCLErrorStream err) {
		this.scanner = scanner;
		this.semantic = actions;
		errors = err;
		this.err = err;
	}
	
	private SemanticActions semantic;
	private SemanticActions.GCLErrorStream err;
	
	public Scanner scanner(){
		return scanner;
	}
	
	public Token currentToken(){return t;}

	void SynErr (int n) {
		if (errDist >= minErrDist) errors.SynErr(la.line, la.col, n);
		errDist = 0;
	}

	public void SemErr (String msg) {
		if (errDist >= minErrDist) errors.Error(t.line, t.col, msg);
		errDist = 0;
	}
	
	void Get () {
		for (;;) {
			t = la;
			la = scanner.Scan();
			if (la.kind <= maxT) { ++errDist; break; }

			if (la.kind == 46) {
				CompilerOptions.listCode = la.val.charAt(2) == '+'; 
			}
			if (la.kind == 47) {
				CompilerOptions.optimize = la.val.charAt(2) == '+'; 
			}
			if (la.kind == 48) {
				SymbolTable.dumpAll(); 
			}
			if (la.kind == 49) {
			}
			if (la.kind == 50) {
				CompilerOptions.showMessages = la.val.charAt(2) == '+'; 
			}
			if (la.kind == 51) {
				CompilerOptions.printAllocatedRegisters(); 
			}
			if (la.kind == 52) {
			}
			la = t;
		}
	}
	
	void Expect (int n) {
		if (la.kind==n) Get(); else { SynErr(n); }
	}
	
	boolean StartOf (int s) {
		return set[s][la.kind];
	}
	
	void ExpectWeak (int n, int follow) {
		if (la.kind == n) Get();
		else {
			SynErr(n);
			while (!StartOf(follow)) Get();
		}
	}
	
	boolean WeakSeparator (int n, int syFol, int repFol) {
		boolean[] s = new boolean[maxT+1];
		if (la.kind == n) { Get(); return true; }
		else if (StartOf(repFol)) return false;
		else {
			for (int i=0; i <= maxT; i++) {
				s[i] = set[syFol][i] || set[repFol][i] || set[0][i];
			}
			SynErr(n);
			while (!s[la.kind]) Get();
			return StartOf(syFol);
		}
	}
	
	void gcl() {
		semantic.startCode();  SymbolTable scope = SymbolTable.currentScope(); 
		while (!(la.kind == 0 || la.kind == 4)) {SynErr(46); Get();}
		module(scope);
		while (la.kind == 4) {
			scope = scope.openScope(true); 
			while (!(la.kind == 0 || la.kind == 4)) {SynErr(47); Get();}
			module(scope);
		}
		semantic.finishCode(); 
	}

	void module(SymbolTable scope) {
		Expect(4);
		Expect(1);
		definitionPart(scope);
		if (la.kind == 5) {
			Get();
			SymbolTable privateScope = scope.openScope(false); 
			block(privateScope);
		}
		Expect(6);
	}

	void definitionPart(SymbolTable scope) {
		while (StartOf(1)) {
			while (!(StartOf(2))) {SynErr(48); Get();}
			definition(scope);
			ExpectWeak(9, 3);
		}
	}

	void block(SymbolTable scope) {
		definitionPart(scope);
		while (!(la.kind == 0 || la.kind == 7)) {SynErr(49); Get();}
		Expect(7);
		statementPart(scope);
		Expect(8);
	}

	void statementPart(SymbolTable scope) {
		while (!(StartOf(4))) {SynErr(50); Get();}
		statement(scope);
		ExpectWeak(9, 5);
		while (StartOf(6)) {
			while (!(StartOf(4))) {SynErr(51); Get();}
			statement(scope);
			ExpectWeak(9, 5);
		}
	}

	void definition(SymbolTable scope) {
		if (la.kind == 13 || la.kind == 14 || la.kind == 15) {
			variableDefinition(scope, ParameterKind.NOT_PARAM);
		} else if (la.kind == 10) {
			constantDefinition(scope, ParameterKind.NOT_PARAM);
		} else SynErr(52);
	}

	void statement(SymbolTable scope) {
		switch (la.kind) {
		case 18: {
			emptyStatement();
			break;
		}
		case 19: {
			readStatement(scope);
			break;
		}
		case 20: {
			writeStatement(scope);
			break;
		}
		case 1: {
			assignStatement(scope);
			break;
		}
		case 24: {
			ifStatement(scope);
			break;
		}
		case 22: {
			doStatement(scope);
			break;
		}
		default: SynErr(53); break;
		}
	}

	void variableDefinition(SymbolTable scope, ParameterKind kindOfParam) {
		TypeDescriptor type; Identifier id; 
		type = type(scope);
		Expect(1);
		id = new Identifier(currentToken().spelling());
		semantic.declareVariable(scope, type, id, kindOfParam);
		
		while (la.kind == 12) {
			Get();
			Expect(1);
			id = new Identifier(currentToken().spelling());
			semantic.declareVariable(scope, type, id, kindOfParam);
			
		}
	}

	void constantDefinition(SymbolTable scope, ParameterKind kindOfParam) {
		Identifier id; Expression exp; 
		Expect(10);
		Expect(1);
		id = new Identifier(currentToken().spelling()); 
		Expect(11);
		exp = expression(scope);
		semantic.declareConstant(scope, id, exp); 
	}

	Expression  expression(SymbolTable scope) {
		Expression  left;
		Expression right; 
		left = andExpression(scope);
		while (la.kind == 28) {
			Get();
			right = andExpression(scope);
			left = semantic.orExpression(left, right); 
		}
		return left;
	}

	TypeDescriptor  type(SymbolTable scope) {
		TypeDescriptor  result;
		result = noType; 
		if (la.kind == 13 || la.kind == 14) {
			result = typeSymbol(scope);
		} else if (la.kind == 15) {
			result = tupleType(scope);
		} else SynErr(54);
		return result;
	}

	TypeDescriptor  typeSymbol(SymbolTable scope) {
		TypeDescriptor  result;
		result = null; Identifier id = null; 
		if (la.kind == 13) {
			Get();
			result = integerType; 
		} else if (la.kind == 14) {
			Get();
			result = booleanType; 
		} else SynErr(55);
		return result;
	}

	TupleType  tupleType(SymbolTable scope) {
		TupleType  result;
		TypeDescriptor type; Identifier id; 
		TypeList carrier = new TypeList(); 
		Expect(15);
		Expect(16);
		type = typeSymbol(scope);
		Expect(1);
		id = new Identifier(currentToken().spelling()); carrier.enter(type, id);
		while (la.kind == 12) {
			Get();
			type = typeSymbol(scope);
			Expect(1);
			id = new Identifier(currentToken().spelling()); carrier.enter(type, id);
		}
		Expect(17);
		result = new TupleType(carrier); 
		return result;
	}

	void emptyStatement() {
		Expect(18);
	}

	void readStatement(SymbolTable scope) {
		Expression exp; 
		Expect(19);
		exp = variableAccessEtc(scope);
		semantic.readVariable(exp); 
		while (la.kind == 12) {
			Get();
			exp = variableAccessEtc(scope);
			semantic.readVariable(exp); 
		}
	}

	void writeStatement(SymbolTable scope) {
		Expect(20);
		writeItem(scope);
		while (la.kind == 12) {
			Get();
			writeItem(scope);
		}
		semantic.genEol(); 
	}

	void assignStatement(SymbolTable scope) {
		AssignRecord expressions = new AssignRecord(); Expression exp; 
		exp = variableAccessEtc(scope);
		expressions.left(exp); 
		while (la.kind == 12) {
			Get();
			exp = variableAccessEtc(scope);
			expressions.left(exp); 
		}
		Expect(21);
		exp = expression(scope);
		expressions.right(exp); 
		while (la.kind == 12) {
			Get();
			exp = expression(scope);
			expressions.right(exp); 
		}
		semantic.parallelAssign(expressions); 
	}

	void ifStatement(SymbolTable scope) {
		GCRecord ifRecord; 
		Expect(24);
		ifRecord = semantic.startIf(); 
		guardedCommandList(scope, ifRecord );
		Expect(25);
		semantic.endIf(ifRecord); 
	}

	void doStatement(SymbolTable scope) {
		GCRecord doRecord; 
		Expect(22);
		doRecord = semantic.startDo(); 
		guardedCommandList(scope, doRecord );
		Expect(23);
		semantic.endDo(doRecord); 
	}

	Expression  variableAccessEtc(SymbolTable scope) {
		Expression  result;
		SemanticItem workValue; 
		workValue = qualifiedIdentifier(scope);
		result = workValue.expectExpression(err); 
		return result;
	}

	void writeItem(SymbolTable scope) {
		Expression exp; 
		if (StartOf(7)) {
			exp = expression(scope);
			semantic.writeExpression(exp); 
		} else if (la.kind == 3) {
			Get();
			semantic.writeString(new StringConstant(currentToken().spelling())); 
		} else SynErr(56);
	}

	void guardedCommandList(SymbolTable scope, GCRecord ifRecord) {
		guardedCommand(scope, ifRecord);
		while (la.kind == 26) {
			Get();
			guardedCommand(scope, ifRecord);
		}
	}

	void guardedCommand(SymbolTable scope, GCRecord  ifRecord ) {
		Expression expr; 
		expr = expression(scope);
		semantic.ifTest(expr, ifRecord); 
		Expect(27);
		statementPart(scope);
		semantic.elseIf(ifRecord); 
	}

	Expression  andExpression(SymbolTable scope) {
		Expression  left;
		Expression right; 
		left = relationalExpr(scope);
		while (la.kind == 29) {
			Get();
			right = relationalExpr(scope);
			left = semantic.andExpression(left, right); 
		}
		return left;
	}

	Expression  relationalExpr(SymbolTable scope) {
		Expression  left;
		Expression right; RelationalOperator op; 
		left = simpleExpr(scope);
		if (StartOf(8)) {
			op = relationalOperator();
			right = simpleExpr(scope);
			left = semantic.compareExpression(left, op, right); 
		}
		return left;
	}

	Expression  simpleExpr(SymbolTable scope) {
		Expression  left;
		Expression right; AddOperator op; left = null; 
		if (StartOf(9)) {
			if (la.kind == 30) {
				Get();
			}
			left = term(scope);
		} else if (la.kind == 31) {
			Get();
			left = term(scope);
			left = semantic.negateExpression(left); 
		} else if (la.kind == 32) {
			Get();
			left = term(scope);
			left = semantic.negateBooleanExpression(left); 
		} else SynErr(57);
		while (la.kind == 30 || la.kind == 31) {
			op = addOperator();
			right = term(scope);
			left = semantic.addExpression(left, op, right); 
		}
		return left;
	}

	RelationalOperator  relationalOperator() {
		RelationalOperator  op;
		op = null; 
		switch (la.kind) {
		case 11: {
			Get();
			op = RelationalOperator.EQUAL; 
			break;
		}
		case 35: {
			Get();
			op = RelationalOperator.NOT_EQUAL; 
			break;
		}
		case 36: {
			Get();
			op = RelationalOperator.GREATER; 
			break;
		}
		case 37: {
			Get();
			op = RelationalOperator.GREATER_OR_EQUAL; 
			break;
		}
		case 38: {
			Get();
			op = RelationalOperator.LESS; 
			break;
		}
		case 39: {
			Get();
			op = RelationalOperator.LESS_OR_EQUAL; 
			break;
		}
		default: SynErr(58); break;
		}
		return op;
	}

	Expression  term(SymbolTable scope) {
		Expression  left;
		Expression right; MultiplyOperator op; left = null; 
		left = factor(scope);
		while (la.kind == 40 || la.kind == 41 || la.kind == 42) {
			op = multiplyOperator();
			right = factor(scope);
			left = semantic.multiplyExpression(left, op, right); 
		}
		return left;
	}

	AddOperator  addOperator() {
		AddOperator  op;
		op = null; 
		if (la.kind == 30) {
			Get();
			op = AddOperator.PLUS; 
		} else if (la.kind == 31) {
			Get();
			op = AddOperator.MINUS; 
		} else SynErr(59);
		return op;
	}

	Expression  factor(SymbolTable scope) {
		Expression  result;
		result = null; 
		if (la.kind == 1) {
			result = variableAccessEtc(scope);
		} else if (la.kind == 2) {
			Get();
			result = new ConstantExpression (integerType, Integer.parseInt(currentToken().spelling()));
			
		} else if (la.kind == 43 || la.kind == 44) {
			booleanConstant();
			result = new ConstantExpression (booleanType, (Boolean.parseBoolean(currentToken().spelling())) ? 1 : 0);
			
		} else if (la.kind == 33) {
			Get();
			result = expression(scope);
			Expect(34);
		} else if (la.kind == 16) {
			Expression exp;
			ExpressionList tupleFields = new ExpressionList();
			
			Get();
			exp = expression(scope);
			tupleFields.enter(exp); 
			while (la.kind == 12) {
				Get();
				exp = expression(scope);
				tupleFields.enter(exp); 
			}
			Expect(17);
			result = semantic.buildTuple(tupleFields); 
		} else SynErr(60);
		return result;
	}

	MultiplyOperator  multiplyOperator() {
		MultiplyOperator  op;
		op = null; 
		if (la.kind == 40) {
			Get();
			op = MultiplyOperator.TIMES; 
		} else if (la.kind == 41) {
			Get();
			op = MultiplyOperator.DIVIDE; 
		} else if (la.kind == 42) {
			Get();
			op = MultiplyOperator.MODULO; 
		} else SynErr(61);
		return op;
	}

	void booleanConstant() {
		if (la.kind == 43) {
			Get();
		} else if (la.kind == 44) {
			Get();
		} else SynErr(62);
	}

	SemanticItem  qualifiedIdentifier(SymbolTable scope) {
		SemanticItem  result;
		Expect(1);
		Identifier id = new Identifier(currentToken().spelling()); 
		result = semantic.semanticValue(scope, id); 
		return result;
	}



	public void Parse() {
		la = new Token();
		la.val = "";		
		Get();
		gcl();

		Expect(0);
	}

	private boolean[][] set = {
		{T,T,x,x, T,x,x,T, x,x,T,x, x,T,T,T, x,x,T,T, T,x,T,x, T,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x},
		{x,x,x,x, x,x,x,x, x,x,T,x, x,T,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x},
		{T,x,x,x, x,x,x,x, x,x,T,x, x,T,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x},
		{T,T,x,x, T,T,T,T, x,x,T,x, x,T,T,T, x,x,T,T, T,x,T,x, T,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x},
		{T,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,x,T,x, T,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x},
		{T,T,x,x, T,x,x,T, T,x,T,x, x,T,T,T, x,x,T,T, T,x,T,T, T,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x},
		{x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,x,T,x, T,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x},
		{x,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, T,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,T,x,x, x,x,x,x, x,x,x,T, T,x,x},
		{x,x,x,x, x,x,x,x, x,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, T,T,T,T, x,x,x,x, x,x,x},
		{x,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, T,x,x,x, x,x,x,x, x,x,x,x, x,x,T,x, x,T,x,x, x,x,x,x, x,x,x,T, T,x,x}

	};
} // end Parser


class Errors {
	public int count = 0;
	public String errMsgFormat = "-- line {0} col {1}: {2}";
	Scanner scanner;
	
	public Errors(Scanner scanner)
	{
		this.scanner = scanner;
	}

	private void printMsg(int line, int column, String msg) {
		StringBuffer b = new StringBuffer(errMsgFormat);
		int pos = b.indexOf("{0}");
		if (pos >= 0) { b.delete(pos, pos+3); b.insert(pos, line); }
		pos = b.indexOf("{1}");
		if (pos >= 0) { b.delete(pos, pos+3); b.insert(pos, column); }
		pos = b.indexOf("{2}");
		if (pos >= 0) b.replace(pos, pos+3, msg);
		scanner.outFile().println(b.toString());
	}
	
	public void SynErr (int line, int col, int n) {
			String s;
			switch (n) {
			case 0: s = "EOF expected"; break;
			case 1: s = "identifier expected"; break;
			case 2: s = "numeral expected"; break;
			case 3: s = "gclString expected"; break;
			case 4: s = "\"module\" expected"; break;
			case 5: s = "\"private\" expected"; break;
			case 6: s = "\".\" expected"; break;
			case 7: s = "\"begin\" expected"; break;
			case 8: s = "\"end\" expected"; break;
			case 9: s = "\";\" expected"; break;
			case 10: s = "\"constant\" expected"; break;
			case 11: s = "\"=\" expected"; break;
			case 12: s = "\",\" expected"; break;
			case 13: s = "\"integer\" expected"; break;
			case 14: s = "\"Boolean\" expected"; break;
			case 15: s = "\"tuple\" expected"; break;
			case 16: s = "\"[\" expected"; break;
			case 17: s = "\"]\" expected"; break;
			case 18: s = "\"skip\" expected"; break;
			case 19: s = "\"read\" expected"; break;
			case 20: s = "\"write\" expected"; break;
			case 21: s = "\":=\" expected"; break;
			case 22: s = "\"do\" expected"; break;
			case 23: s = "\"od\" expected"; break;
			case 24: s = "\"if\" expected"; break;
			case 25: s = "\"fi\" expected"; break;
			case 26: s = "\"[]\" expected"; break;
			case 27: s = "\"->\" expected"; break;
			case 28: s = "\"|\" expected"; break;
			case 29: s = "\"&\" expected"; break;
			case 30: s = "\"+\" expected"; break;
			case 31: s = "\"-\" expected"; break;
			case 32: s = "\"~\" expected"; break;
			case 33: s = "\"(\" expected"; break;
			case 34: s = "\")\" expected"; break;
			case 35: s = "\"#\" expected"; break;
			case 36: s = "\">\" expected"; break;
			case 37: s = "\">=\" expected"; break;
			case 38: s = "\"<\" expected"; break;
			case 39: s = "\"<=\" expected"; break;
			case 40: s = "\"*\" expected"; break;
			case 41: s = "\"/\" expected"; break;
			case 42: s = "\"\\\\\" expected"; break;
			case 43: s = "\"true\" expected"; break;
			case 44: s = "\"false\" expected"; break;
			case 45: s = "??? expected"; break;
			case 46: s = "this symbol not expected in gcl"; break;
			case 47: s = "this symbol not expected in gcl"; break;
			case 48: s = "this symbol not expected in definitionPart"; break;
			case 49: s = "this symbol not expected in block"; break;
			case 50: s = "this symbol not expected in statementPart"; break;
			case 51: s = "this symbol not expected in statementPart"; break;
			case 52: s = "invalid definition"; break;
			case 53: s = "invalid statement"; break;
			case 54: s = "invalid type"; break;
			case 55: s = "invalid typeSymbol"; break;
			case 56: s = "invalid writeItem"; break;
			case 57: s = "invalid simpleExpr"; break;
			case 58: s = "invalid relationalOperator"; break;
			case 59: s = "invalid addOperator"; break;
			case 60: s = "invalid factor"; break;
			case 61: s = "invalid multiplyOperator"; break;
			case 62: s = "invalid booleanConstant"; break;
				default: s = "error " + n; break;
			}
			printMsg(line, col, s);
			count++;
			CompilerOptions.genHalt();
	}

	public void SemErr (int line, int col, int n) {
		printMsg(line, col, "error " + n);
		count++;
	}

	void semanticError(int n){
		SemErr(scanner.t.line, scanner.t.col, n); 
	}

	void semanticError(int n, int line, int col){
		SemErr(line, col, n);
	}

	void semanticError(int n, int line, int col, String message){
		scanner.outFile().print(message + ": ");
		semanticError(n, line, col);
	}

	void semanticError(int n, String message){
		scanner.outFile().print(message + ": ");
		semanticError(n);
	}

	public void Error (int line, int col, String s) {	
		printMsg(line, col, s);
		count++;
	}

	public void Exception (String s) {
		scanner.outFile().println(s); 
		System.exit(1);
	}
} // Errors

class FatalError extends RuntimeException {
	public static final long serialVersionUID = 1L;
	public FatalError(String s) { super(s); }
}


