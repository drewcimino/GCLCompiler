package gcl;

public class Parser {
// GCL Version 2011
	public static final int _EOF = 0;
	public static final int _identifier = 1;
	public static final int _numeral = 2;
	public static final int _gclString = 3;
	public static final int maxT = 55;
	public static final int _option1 = 56;
	public static final int _option3 = 57;
	public static final int _option5 = 58;
	public static final int _option6 = 59;
	public static final int _option7 = 60;
	public static final int _option9 = 61;
	public static final int _option10 = 62;

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

			if (la.kind == 56) {
				CompilerOptions.listCode = la.val.charAt(2) == '+'; 
			}
			if (la.kind == 57) {
				CompilerOptions.optimize = la.val.charAt(2) == '+'; 
			}
			if (la.kind == 58) {
				SymbolTable.dumpAll(); 
			}
			if (la.kind == 59) {
			}
			if (la.kind == 60) {
				CompilerOptions.showMessages = la.val.charAt(2) == '+'; 
			}
			if (la.kind == 61) {
				CompilerOptions.printAllocatedRegisters(); 
			}
			if (la.kind == 62) {
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
		while (!(la.kind == 0 || la.kind == 4)) {SynErr(56); Get();}
		module(scope);
		while (la.kind == 4) {
			scope = scope.openScope(true); 
			while (!(la.kind == 0 || la.kind == 4)) {SynErr(57); Get();}
			module(scope);
		}
		semantic.finishCode(); 
	}

	void module(SymbolTable scope) {
		String result; 
		Expect(4);
		result = validIdentifier();
		semantic.declareModule(scope, new Identifier(result)); 
		definitionPart(scope);
		if (la.kind == 5) {
			Get();
			SymbolTable privateScope = scope.openScope(false); 
			block(privateScope);
		}
		Expect(6);
	}

	String  validIdentifier() {
		String  result;
		Expect(1);
		result = currentToken().spelling();
		semantic.checkIdentifierSpelling(result);
		
		return result;
	}

	void definitionPart(SymbolTable scope) {
		while (StartOf(1)) {
			while (!(StartOf(2))) {SynErr(58); Get();}
			definition(scope);
			ExpectWeak(9, 3);
		}
	}

	void block(SymbolTable scope) {
		definitionPart(scope);
		while (!(la.kind == 0 || la.kind == 7)) {SynErr(59); Get();}
		Expect(7);
		statementPart(scope);
		Expect(8);
	}

	void statementPart(SymbolTable scope) {
		while (!(StartOf(4))) {SynErr(60); Get();}
		statement(scope);
		ExpectWeak(9, 5);
		while (StartOf(6)) {
			while (!(StartOf(4))) {SynErr(61); Get();}
			statement(scope);
			ExpectWeak(9, 5);
		}
	}

	void definition(SymbolTable scope) {
		if (StartOf(7)) {
			variableDefinition(scope, ParameterKind.NOT_PARAM);
		} else if (la.kind == 10) {
			constantDefinition(scope);
		} else if (la.kind == 13) {
			typeDefinition(scope);
		} else if (la.kind == 14) {
			procedureDefinition(scope);
		} else SynErr(62);
	}

	void statement(SymbolTable scope) {
		switch (la.kind) {
		case 28: {
			emptyStatement();
			break;
		}
		case 29: {
			readStatement(scope);
			break;
		}
		case 30: {
			writeStatement(scope);
			break;
		}
		case 1: {
			assignStatement(scope);
			break;
		}
		case 37: {
			ifStatement(scope);
			break;
		}
		case 32: {
			doStatement(scope);
			break;
		}
		case 34: {
			forStatement(scope);
			break;
		}
		default: SynErr(63); break;
		}
	}

	void variableDefinition(SymbolTable scope, ParameterKind kindOfParam) {
		String result;
		TypeDescriptor type;
		Identifier id;
		
		type = type(scope);
		result = validIdentifier();
		id = new Identifier(result);
		semantic.declareVariable(scope, type, id, kindOfParam);
		
		while (la.kind == 12) {
			Get();
			result = validIdentifier();
			id = new Identifier(result);
			semantic.declareVariable(scope, type, id, kindOfParam);
			
		}
	}

	void constantDefinition(SymbolTable scope) {
		String result;
		Identifier id;
		Expression exp;
		
		Expect(10);
		result = validIdentifier();
		id = new Identifier(result); 
		Expect(11);
		exp = expression(scope);
		semantic.declareConstant(scope, id, exp.expectConstantExpression(err)); 
	}

	void typeDefinition(SymbolTable scope) {
		TypeDescriptor type;
		String result;
		
		Expect(13);
		type = type(scope);
		result = validIdentifier();
		semantic.declareTypeDefinition(scope, type, new Identifier(result)); 
	}

	void procedureDefinition(SymbolTable scope) {
		Expect(14);
		Expect(1);
		String tupleName = currentToken().spelling();
		Identifier tupleId = new Identifier(tupleName);
		
		Expect(15);
		Expect(1);
		String procedureName = currentToken().spelling();
		Identifier procedureId = new Identifier(procedureName);
		SymbolTable procedureScope = semantic.procedureDefinition(tupleId, procedureId, scope);
		
		block(procedureScope);
		semantic.closeProcedureDefinition(); 
	}

	Expression  expression(SymbolTable scope) {
		Expression  left;
		Expression right; 
		left = andExpression(scope);
		while (la.kind == 40) {
			Get();
			right = andExpression(scope);
			left = semantic.orExpression(left, right); 
		}
		return left;
	}

	TypeDescriptor  type(SymbolTable scope) {
		TypeDescriptor  result;
		result = noType; 
		if (la.kind == 1 || la.kind == 21 || la.kind == 22) {
			result = typeSymbol(scope);
			if (la.kind == 16 || la.kind == 19) {
				if (la.kind == 16) {
					result = arrayType(result, scope);
				} else {
					result = rangeType(result, scope);
				}
			}
		} else if (la.kind == 23) {
			result = tupleType(scope);
		} else SynErr(64);
		return result;
	}

	TypeDescriptor  arrayType(TypeDescriptor componentType, SymbolTable scope) {
		TypeDescriptor  result;
		SemanticItem subscriptType;
		java.util.Stack<RangeType> subscripts = new java.util.Stack<RangeType>();
		
		Expect(16);
		Expect(17);
		subscriptType = qualifiedIdentifier(scope);
		subscripts.push(subscriptType.expectTypeDescriptor(err).expectRangeType(err)); 
		Expect(18);
		while (la.kind == 17) {
			Get();
			subscriptType = qualifiedIdentifier(scope);
			subscripts.push(subscriptType.expectTypeDescriptor(err).expectRangeType(err)); 
			Expect(18);
		}
		result = semantic.declareArrayType(subscripts, componentType); 
		return result;
	}

	SemanticItem  qualifiedIdentifier(SymbolTable scope) {
		SemanticItem  result;
		Expect(1);
		String idName = currentToken().spelling();
		Identifier id = new Identifier(idName);
		result = semantic.semanticValue(scope, id);
		
		if (la.kind == 6) {
			Get();
			Expect(1);
			String qualifiedName = currentToken().spelling();
			Identifier qualified = new Identifier(qualifiedName);
			result = semantic.semanticValue(scope, result.expectModuleRecord(err), qualified);
			
		}
		return result;
	}

	TypeDescriptor  rangeType(TypeDescriptor baseType, SymbolTable scope) {
		TypeDescriptor  result;
		Expression lowerBound; Expression upperBound; 
		Expect(19);
		Expect(17);
		lowerBound = expression(scope);
		Expect(20);
		upperBound = expression(scope);
		Expect(18);
		result = semantic.declareRangeType(lowerBound.expectConstantExpression(err), upperBound.expectConstantExpression(err), baseType); 
		return result;
	}

	TypeDescriptor  typeSymbol(SymbolTable scope) {
		TypeDescriptor  result;
		result = null; SemanticItem typeItem = null; 
		if (la.kind == 21) {
			Get();
			result = integerType; 
		} else if (la.kind == 22) {
			Get();
			result = booleanType; 
		} else if (la.kind == 1) {
			typeItem = qualifiedIdentifier(scope);
			result = typeItem.expectTypeDescriptor(err); 
		} else SynErr(65);
		return result;
	}

	TupleType  tupleType(SymbolTable scope) {
		TupleType  result;
		semantic.insertComment("tupleType"); 
		TypeList carrier = new TypeList(); 
		Expect(23);
		Expect(17);
		if (la.kind == 14) {
			carrier = justProcedures(carrier, scope);
		} else if (la.kind == 1 || la.kind == 21 || la.kind == 22) {
			carrier = fieldsAndProcedures(carrier, scope);
		} else SynErr(66);
		Expect(18);
		result = new TupleType(carrier); 
		semantic.insertComment(result.toString()); 
		return result;
	}

	TypeList  justProcedures(TypeList carrier, SymbolTable scope) {
		TypeList  result;
		semantic.insertComment("justProcs"); 
		carrier = procedureDeclaration(carrier, scope);
		while (la.kind == 12) {
			Get();
			carrier = procedureDeclaration(carrier, scope);
		}
		result = carrier; 
		return result;
	}

	TypeList  fieldsAndProcedures(TypeList carrier, SymbolTable scope) {
		TypeList  result;
		semantic.insertComment("fieldsAndProcs"); 
		TypeDescriptor fieldType;
		String fieldName;
		
		fieldType = typeSymbol(scope);
		fieldName = validIdentifier();
		Identifier fieldId = new Identifier(fieldName);
		carrier.enter(fieldType, fieldId);
		
		result = moreFieldsAndProcedures(carrier, scope);
		return result;
	}

	TypeList  procedureDeclaration(TypeList carrier, SymbolTable outerScope) {
		TypeList  result;
		semantic.insertComment("procDec"); 
		String procedureName; 
		Expect(14);
		procedureName = validIdentifier();
		Identifier procedureId = new Identifier(procedureName);
		SymbolTable procedureScope = semantic.procedureDeclaration(procedureId, carrier, outerScope);
		
		parameterPart(procedureScope);
		semantic.closeProcedureDeclaration();
		result = carrier;
		
		return result;
	}

	TypeList  moreFieldsAndProcedures(TypeList carrier, SymbolTable scope) {
		TypeList  result;
		semantic.insertComment("moreFieldsAndProcs"); 
		result = carrier; 
		if (la.kind == 12 || la.kind == 14) {
			if (la.kind == 12) {
				Get();
				TypeDescriptor fieldType;
				String fieldName;
				
				fieldType = typeSymbol(scope);
				fieldName = validIdentifier();
				Identifier fieldId = new Identifier(fieldName);
				carrier.enter(fieldType, fieldId);
				
				result = moreFieldsAndProcedures(carrier, scope);
			} else {
				result = justProcedures(carrier, scope);
			}
		}
		return result;
	}

	void parameterPart(SymbolTable procedureScope) {
		semantic.insertComment("paramPart"); 
		Expect(24);
		if (la.kind == 26 || la.kind == 27) {
			parameterDefinition(procedureScope);
			while (la.kind == 9) {
				Get();
				parameterDefinition(procedureScope);
			}
		}
		Expect(25);
	}

	void parameterDefinition(SymbolTable procedureScope) {
		if (la.kind == 26) {
			semantic.insertComment("paramDef"); 
			Get();
			variableDefinition(procedureScope, ParameterKind.VALUE);
		} else if (la.kind == 27) {
			Get();
			variableDefinition(procedureScope, ParameterKind.REFERENCE);
		} else SynErr(67);
	}

	void emptyStatement() {
		Expect(28);
	}

	void readStatement(SymbolTable scope) {
		Expression exp; 
		Expect(29);
		exp = variableAccessEtc(scope);
		semantic.readVariable(exp); 
		while (la.kind == 12) {
			Get();
			exp = variableAccessEtc(scope);
			semantic.readVariable(exp); 
		}
	}

	void writeStatement(SymbolTable scope) {
		Expect(30);
		writeItem(scope);
		while (la.kind == 12) {
			Get();
			writeItem(scope);
		}
		semantic.genEol(); 
	}

	void assignStatement(SymbolTable scope) {
		AssignRecord expressions = new AssignRecord();
		Expression exp;
		
		exp = variableAccessEtc(scope);
		expressions.left(exp.expectVariableExpression(err)); 
		while (la.kind == 12) {
			Get();
			exp = variableAccessEtc(scope);
			expressions.left(exp.expectVariableExpression(err)); 
		}
		Expect(31);
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
		Expect(37);
		ifRecord = semantic.startIf(); 
		guardedCommandList(scope, ifRecord );
		Expect(38);
		semantic.endIf(ifRecord); 
	}

	void doStatement(SymbolTable scope) {
		GCRecord doRecord; 
		Expect(32);
		doRecord = semantic.startDo(); 
		guardedCommandList(scope, doRecord );
		Expect(33);
	}

	void forStatement(SymbolTable scope) {
		ForRecord forRecord; Expression control; 
		Expect(34);
		control = variableAccessEtc(scope);
		forRecord = semantic.startForall(control.expectVariableExpression(err)); 
		Expect(35);
		statementPart(scope);
		Expect(36);
		semantic.endForall(forRecord); 
	}

	Expression  variableAccessEtc(SymbolTable scope) {
		Expression  result;
		SemanticItem workValue; 
		workValue = qualifiedIdentifier(scope);
		result = subsAndCompons(workValue, scope);
		return result;
	}

	void writeItem(SymbolTable scope) {
		Expression exp; 
		if (StartOf(8)) {
			exp = expression(scope);
			semantic.writeExpression(exp); 
		} else if (la.kind == 3) {
			Get();
			semantic.writeString(new StringConstant(currentToken().spelling())); 
		} else SynErr(68);
	}

	void guardedCommandList(SymbolTable scope, GCRecord ifRecord) {
		guardedCommand(scope, ifRecord);
		while (la.kind == 39) {
			Get();
			guardedCommand(scope, ifRecord);
		}
	}

	void guardedCommand(SymbolTable scope, GCRecord  ifRecord ) {
		Expression expr; 
		expr = expression(scope);
		semantic.ifTest(expr, ifRecord); 
		Expect(35);
		statementPart(scope);
		semantic.elseIf(ifRecord); 
	}

	Expression  andExpression(SymbolTable scope) {
		Expression  left;
		Expression right; 
		left = relationalExpr(scope);
		while (la.kind == 41) {
			Get();
			right = relationalExpr(scope);
			left = semantic.andExpression(left, right); 
		}
		return left;
	}

	Expression  relationalExpr(SymbolTable scope) {
		Expression  left;
		Expression right;
		RelationalOperator op;
		
		left = simpleExpr(scope);
		if (StartOf(9)) {
			op = relationalOperator();
			right = simpleExpr(scope);
			left = semantic.compareExpression(left, op, right); 
		}
		return left;
	}

	Expression  simpleExpr(SymbolTable scope) {
		Expression  left;
		Expression right;
		AddOperator op;
		left = null;
		
		if (StartOf(10)) {
			if (la.kind == 42) {
				Get();
			}
			left = term(scope);
		} else if (la.kind == 43) {
			Get();
			left = term(scope);
			left = semantic.negateExpression(left); 
		} else if (la.kind == 44) {
			Get();
			left = term(scope);
			left = semantic.negateBooleanExpression(left); 
		} else SynErr(69);
		while (la.kind == 42 || la.kind == 43) {
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
		case 45: {
			Get();
			op = RelationalOperator.NOT_EQUAL; 
			break;
		}
		case 46: {
			Get();
			op = RelationalOperator.GREATER; 
			break;
		}
		case 47: {
			Get();
			op = RelationalOperator.GREATER_OR_EQUAL; 
			break;
		}
		case 48: {
			Get();
			op = RelationalOperator.LESS; 
			break;
		}
		case 49: {
			Get();
			op = RelationalOperator.LESS_OR_EQUAL; 
			break;
		}
		default: SynErr(70); break;
		}
		return op;
	}

	Expression  term(SymbolTable scope) {
		Expression  left;
		Expression right;
		MultiplyOperator op;
		left = null;
		
		left = factor(scope);
		while (la.kind == 50 || la.kind == 51 || la.kind == 52) {
			op = multiplyOperator();
			right = factor(scope);
			left = semantic.multiplyExpression(left, op, right); 
		}
		return left;
	}

	AddOperator  addOperator() {
		AddOperator  op;
		op = null; 
		if (la.kind == 42) {
			Get();
			op = AddOperator.PLUS; 
		} else if (la.kind == 43) {
			Get();
			op = AddOperator.MINUS; 
		} else SynErr(71);
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
		} else if (la.kind == 53 || la.kind == 54) {
			booleanConstant();
			result = new ConstantExpression (booleanType, (Boolean.parseBoolean(currentToken().spelling())) ? 1 : 0); 
		} else if (la.kind == 24) {
			Get();
			result = expression(scope);
			Expect(25);
		} else if (la.kind == 17) {
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
			Expect(18);
			result = semantic.buildTuple(tupleFields); 
		} else SynErr(72);
		return result;
	}

	MultiplyOperator  multiplyOperator() {
		MultiplyOperator  op;
		op = null; 
		if (la.kind == 50) {
			Get();
			op = MultiplyOperator.TIMES; 
		} else if (la.kind == 51) {
			Get();
			op = MultiplyOperator.DIVIDE; 
		} else if (la.kind == 52) {
			Get();
			op = MultiplyOperator.MODULO; 
		} else SynErr(73);
		return op;
	}

	void booleanConstant() {
		if (la.kind == 53) {
			Get();
		} else if (la.kind == 54) {
			Get();
		} else SynErr(74);
	}

	Expression  subsAndCompons(SemanticItem identifier, SymbolTable scope) {
		Expression  result;
		result = identifier.expectExpression(err);
		Expression subscript;
		
		while (la.kind == 15 || la.kind == 17) {
			if (la.kind == 17) {
				Get();
				subscript = expression(scope);
				Expect(18);
				result = semantic.subscript(result.expectVariableExpression(err), subscript); 
			} else {
				Get();
				Expect(1);
				String component = currentToken().spelling();
				Identifier fieldName = new Identifier(component);
				result = semantic.tupleComponent(result.expectVariableExpression(err), fieldName);
				
			}
		}
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
		{T,T,x,x, T,x,x,T, x,x,T,x, x,T,T,x, x,x,x,x, x,T,T,T, x,x,x,x, T,T,T,x, T,x,T,x, x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x},
		{x,T,x,x, x,x,x,x, x,x,T,x, x,T,T,x, x,x,x,x, x,T,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x},
		{T,T,x,x, x,x,x,x, x,x,T,x, x,T,T,x, x,x,x,x, x,T,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x},
		{T,T,x,x, T,T,T,T, x,x,T,x, x,T,T,x, x,x,x,x, x,T,T,T, x,x,x,x, T,T,T,x, T,x,T,x, x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x},
		{T,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, T,T,T,x, T,x,T,x, x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x},
		{T,T,x,x, T,x,x,T, T,x,T,x, x,T,T,x, x,x,x,x, x,T,T,T, x,x,x,x, T,T,T,x, T,T,T,x, T,T,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x},
		{x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, T,T,T,x, T,x,T,x, x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x},
		{x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,T,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x},
		{x,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,T,x,x, x,x,x,x, T,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,T, T,x,x,x, x,x,x,x, x,T,T,x, x},
		{x,x,x,x, x,x,x,x, x,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,T,T,T, T,T,x,x, x,x,x,x, x},
		{x,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,T,x,x, x,x,x,x, T,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,T,x, x,x,x,x, x,x,x,x, x,T,T,x, x}

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
			case 13: s = "\"typedefinition\" expected"; break;
			case 14: s = "\"procedure\" expected"; break;
			case 15: s = "\"@\" expected"; break;
			case 16: s = "\"array\" expected"; break;
			case 17: s = "\"[\" expected"; break;
			case 18: s = "\"]\" expected"; break;
			case 19: s = "\"range\" expected"; break;
			case 20: s = "\"..\" expected"; break;
			case 21: s = "\"integer\" expected"; break;
			case 22: s = "\"Boolean\" expected"; break;
			case 23: s = "\"tuple\" expected"; break;
			case 24: s = "\"(\" expected"; break;
			case 25: s = "\")\" expected"; break;
			case 26: s = "\"value\" expected"; break;
			case 27: s = "\"reference\" expected"; break;
			case 28: s = "\"skip\" expected"; break;
			case 29: s = "\"read\" expected"; break;
			case 30: s = "\"write\" expected"; break;
			case 31: s = "\":=\" expected"; break;
			case 32: s = "\"do\" expected"; break;
			case 33: s = "\"od\" expected"; break;
			case 34: s = "\"forall\" expected"; break;
			case 35: s = "\"->\" expected"; break;
			case 36: s = "\"llarof\" expected"; break;
			case 37: s = "\"if\" expected"; break;
			case 38: s = "\"fi\" expected"; break;
			case 39: s = "\"[]\" expected"; break;
			case 40: s = "\"|\" expected"; break;
			case 41: s = "\"&\" expected"; break;
			case 42: s = "\"+\" expected"; break;
			case 43: s = "\"-\" expected"; break;
			case 44: s = "\"~\" expected"; break;
			case 45: s = "\"#\" expected"; break;
			case 46: s = "\">\" expected"; break;
			case 47: s = "\">=\" expected"; break;
			case 48: s = "\"<\" expected"; break;
			case 49: s = "\"<=\" expected"; break;
			case 50: s = "\"*\" expected"; break;
			case 51: s = "\"/\" expected"; break;
			case 52: s = "\"\\\\\" expected"; break;
			case 53: s = "\"true\" expected"; break;
			case 54: s = "\"false\" expected"; break;
			case 55: s = "??? expected"; break;
			case 56: s = "this symbol not expected in gcl"; break;
			case 57: s = "this symbol not expected in gcl"; break;
			case 58: s = "this symbol not expected in definitionPart"; break;
			case 59: s = "this symbol not expected in block"; break;
			case 60: s = "this symbol not expected in statementPart"; break;
			case 61: s = "this symbol not expected in statementPart"; break;
			case 62: s = "invalid definition"; break;
			case 63: s = "invalid statement"; break;
			case 64: s = "invalid type"; break;
			case 65: s = "invalid typeSymbol"; break;
			case 66: s = "invalid tupleType"; break;
			case 67: s = "invalid parameterDefinition"; break;
			case 68: s = "invalid writeItem"; break;
			case 69: s = "invalid simpleExpr"; break;
			case 70: s = "invalid relationalOperator"; break;
			case 71: s = "invalid addOperator"; break;
			case 72: s = "invalid factor"; break;
			case 73: s = "invalid multiplyOperator"; break;
			case 74: s = "invalid booleanConstant"; break;
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


