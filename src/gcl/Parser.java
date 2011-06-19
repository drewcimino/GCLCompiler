package gcl;

public class Parser {
// GCL Version 2011
	public static final int _EOF = 0;
	public static final int _identifier = 1;
	public static final int _numeral = 2;
	public static final int _gclString = 3;
	public static final int maxT = 58;
	public static final int _option1 = 59;
	public static final int _option3 = 60;
	public static final int _option5 = 61;
	public static final int _option6 = 62;
	public static final int _option7 = 63;
	public static final int _option9 = 64;
	public static final int _option10 = 65;

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

			if (la.kind == 59) {
				CompilerOptions.listCode = la.val.charAt(2) == '+'; 
			}
			if (la.kind == 60) {
				CompilerOptions.optimize = la.val.charAt(2) == '+'; 
			}
			if (la.kind == 61) {
				SymbolTable.dumpAll(); 
			}
			if (la.kind == 62) {
			}
			if (la.kind == 63) {
				CompilerOptions.showMessages = la.val.charAt(2) == '+'; 
			}
			if (la.kind == 64) {
				CompilerOptions.printAllocatedRegisters(); 
			}
			if (la.kind == 65) {
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
		while (!(la.kind == 0 || la.kind == 4)) {SynErr(59); Get();}
		module(scope);
		while (la.kind == 4) {
			scope = scope.openScope(true); 
			while (!(la.kind == 0 || la.kind == 4)) {SynErr(60); Get();}
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
		} else if (la.kind == 6) {
			semantic.doLink(); 
		} else SynErr(61);
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
			while (!(StartOf(2))) {SynErr(62); Get();}
			definition(scope);
			ExpectWeak(9, 3);
		}
	}

	void block(SymbolTable scope) {
		definitionPart(scope);
		while (!(la.kind == 0 || la.kind == 7)) {SynErr(63); Get();}
		Expect(7);
		semantic.doLink(); 
		statementPart(scope);
		Expect(8);
	}

	void statementPart(SymbolTable scope) {
		while (!(StartOf(4))) {SynErr(64); Get();}
		statement(scope);
		ExpectWeak(9, 5);
		while (StartOf(6)) {
			while (!(StartOf(4))) {SynErr(65); Get();}
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
		} else SynErr(66);
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
		case 1: case 55: {
			variableAccessStatement(scope);
			break;
		}
		case 33: {
			returnStatement();
			break;
		}
		case 39: {
			ifStatement(scope);
			break;
		}
		case 34: {
			doStatement(scope);
			break;
		}
		case 36: {
			forStatement(scope);
			break;
		}
		default: SynErr(67); break;
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
		SymbolTable procedureScope = semantic.openProcedureDefinition(tupleId, procedureId, scope);
		
		block(procedureScope);
		semantic.closeProcedureDefinition(); 
	}

	Expression  expression(SymbolTable scope) {
		Expression  left;
		Expression right; 
		left = andExpression(scope);
		while (la.kind == 42) {
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
		} else SynErr(68);
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
		} else SynErr(69);
		return result;
	}

	TupleType  tupleType(SymbolTable scope) {
		TupleType  result;
		TypeList carrier = new TypeList(); 
		Expect(23);
		Expect(17);
		if (la.kind == 14) {
			carrier = justProcedures(carrier, scope);
		} else if (la.kind == 1 || la.kind == 21 || la.kind == 22) {
			carrier = fieldsAndProcedures(carrier, scope);
		} else SynErr(70);
		Expect(18);
		result = new TupleType(carrier); 
		return result;
	}

	TypeList  justProcedures(TypeList carrier, SymbolTable outerScope) {
		TypeList  result;
		carrier = procedureDeclaration(carrier, outerScope);
		while (la.kind == 12) {
			Get();
			carrier = procedureDeclaration(carrier, outerScope);
		}
		result = carrier; 
		return result;
	}

	TypeList  fieldsAndProcedures(TypeList carrier, SymbolTable outerScope) {
		TypeList  result;
		TypeDescriptor fieldType;
		String fieldName;
		
		fieldType = typeSymbol(outerScope);
		fieldName = validIdentifier();
		Identifier fieldId = new Identifier(fieldName);
		carrier.enter(fieldType, fieldId, err);
		
		result = moreFieldsAndProcedures(carrier, outerScope);
		return result;
	}

	TypeList  procedureDeclaration(TypeList carrier, SymbolTable outerScope) {
		TypeList  result;
		String procedureName; 
		Expect(14);
		procedureName = validIdentifier();
		Identifier procedureId = new Identifier(procedureName);
		SymbolTable procedureScope = semantic.openProcedureDeclaration(procedureId, carrier, outerScope);
		
		parameterPart(procedureScope);
		semantic.closeProcedureDeclaration();
		result = carrier;
		
		return result;
	}

	TypeList  moreFieldsAndProcedures(TypeList carrier, SymbolTable outerScope) {
		TypeList  result;
		result = carrier; 
		if (la.kind == 12) {
			Get();
			if (la.kind == 1 || la.kind == 21 || la.kind == 22) {
				TypeDescriptor fieldType;
				String fieldName;
				
				fieldType = typeSymbol(outerScope);
				fieldName = validIdentifier();
				Identifier fieldId = new Identifier(fieldName);
				carrier.enter(fieldType, fieldId, err);
				
				result = moreFieldsAndProcedures(carrier, outerScope);
			} else if (la.kind == 14) {
				result = justProcedures(carrier, outerScope);
			} else SynErr(71);
		}
		return result;
	}

	void parameterPart(SymbolTable procedureScope) {
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
			Get();
			variableDefinition(procedureScope, ParameterKind.VALUE);
		} else if (la.kind == 27) {
			Get();
			variableDefinition(procedureScope, ParameterKind.REFERENCE);
		} else SynErr(72);
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

	void variableAccessStatement(SymbolTable scope) {
		Expression exp; 
		exp = variableAccessEtc(scope);
		if (la.kind == 12 || la.kind == 31) {
			assignStatement(exp, scope);
		} else if (la.kind == 32) {
			callStatement(exp, scope);
		} else SynErr(73);
	}

	void returnStatement() {
		Expect(33);
		semantic.doReturn(); 
	}

	void ifStatement(SymbolTable scope) {
		GCRecord ifRecord; 
		Expect(39);
		ifRecord = semantic.startIf(); 
		guardedCommandList(scope, ifRecord );
		Expect(40);
		semantic.endIf(ifRecord); 
	}

	void doStatement(SymbolTable scope) {
		GCRecord doRecord; 
		Expect(34);
		doRecord = semantic.startDo(); 
		guardedCommandList(scope, doRecord );
		Expect(35);
	}

	void forStatement(SymbolTable scope) {
		ForRecord forRecord; Expression control; 
		Expect(36);
		control = variableAccessEtc(scope);
		forRecord = semantic.startForall(control.expectVariableExpression(err)); 
		Expect(37);
		statementPart(scope);
		Expect(38);
		semantic.endForall(forRecord); 
	}

	Expression  variableAccessEtc(SymbolTable scope) {
		Expression  result;
		SemanticItem workValue = null; 
		if (la.kind == 1) {
			workValue = qualifiedIdentifier(scope);
		} else if (la.kind == 55) {
			Get();
			workValue = semantic.currentProcedureThis(); 
		} else SynErr(74);
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
		} else SynErr(75);
	}

	ExpressionList  expressionList(SymbolTable scope) {
		ExpressionList  expressions;
		expressions = new ExpressionList();
		Expression exp;
		
		exp = expression(scope);
		expressions.enter(exp); 
		while (la.kind == 12) {
			Get();
			exp = expression(scope);
			expressions.enter(exp); 
		}
		return expressions;
	}

	void assignStatement(Expression exp, SymbolTable scope) {
		AssignRecord expressions = new AssignRecord();
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

	void callStatement(Expression tupleExpression, SymbolTable scope) {
		TupleType tuple;
		Identifier procedureId;
		Procedure procedure;
		ExpressionList arguments;
		
		Expect(32);
		Expect(1);
		tuple = tupleExpression.type().expectTupleType(err);
		procedureId = new Identifier(currentToken().spelling());
		SemanticItem procedureItem = semantic.semanticValue(tuple.methods(), procedureId);
		if(procedureItem instanceof GeneralError){
			procedure = new ErrorProcedure("$ Procedure ID not found. ");
		}
		else{
			procedure = procedureItem.expectProcedure(err);
		}
		
		arguments = argumentList(scope);
		semantic.callProcedure(tupleExpression, procedure, arguments); 
	}

	ExpressionList  argumentList(SymbolTable scope) {
		ExpressionList  arguments;
		arguments = new ExpressionList(); 
		Expect(24);
		if (StartOf(8)) {
			arguments = expressionList(scope);
		}
		Expect(25);
		return arguments;
	}

	void guardedCommandList(SymbolTable scope, GCRecord ifRecord) {
		guardedCommand(scope, ifRecord);
		while (la.kind == 41) {
			Get();
			guardedCommand(scope, ifRecord);
		}
	}

	void guardedCommand(SymbolTable scope, GCRecord  ifRecord ) {
		Expression expr; 
		expr = expression(scope);
		semantic.ifTest(expr, ifRecord); 
		Expect(37);
		statementPart(scope);
		semantic.elseIf(ifRecord); 
	}

	Expression  andExpression(SymbolTable scope) {
		Expression  left;
		Expression right; 
		left = relationalExpr(scope);
		while (la.kind == 43) {
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
			if (la.kind == 44) {
				Get();
			}
			left = term(scope);
		} else if (la.kind == 45) {
			Get();
			left = term(scope);
			left = semantic.negateExpression(left); 
		} else if (la.kind == 46) {
			Get();
			left = term(scope);
			left = semantic.negateBooleanExpression(left); 
		} else SynErr(76);
		while (la.kind == 44 || la.kind == 45) {
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
		case 47: {
			Get();
			op = RelationalOperator.NOT_EQUAL; 
			break;
		}
		case 48: {
			Get();
			op = RelationalOperator.GREATER; 
			break;
		}
		case 49: {
			Get();
			op = RelationalOperator.GREATER_OR_EQUAL; 
			break;
		}
		case 50: {
			Get();
			op = RelationalOperator.LESS; 
			break;
		}
		case 51: {
			Get();
			op = RelationalOperator.LESS_OR_EQUAL; 
			break;
		}
		default: SynErr(77); break;
		}
		return op;
	}

	Expression  term(SymbolTable scope) {
		Expression  left;
		Expression right;
		MultiplyOperator op;
		left = null;
		
		left = factor(scope);
		while (la.kind == 52 || la.kind == 53 || la.kind == 54) {
			op = multiplyOperator();
			right = factor(scope);
			left = semantic.multiplyExpression(left, op, right); 
		}
		return left;
	}

	AddOperator  addOperator() {
		AddOperator  op;
		op = null; 
		if (la.kind == 44) {
			Get();
			op = AddOperator.PLUS; 
		} else if (la.kind == 45) {
			Get();
			op = AddOperator.MINUS; 
		} else SynErr(78);
		return op;
	}

	Expression  factor(SymbolTable scope) {
		Expression  result;
		result = null; 
		if (la.kind == 1 || la.kind == 55) {
			result = variableAccessEtc(scope);
		} else if (la.kind == 2) {
			Get();
			result = new ConstantExpression (integerType, Integer.parseInt(currentToken().spelling())); 
		} else if (la.kind == 56 || la.kind == 57) {
			booleanConstant();
			result = new ConstantExpression (booleanType, (Boolean.parseBoolean(currentToken().spelling())) ? 1 : 0); 
		} else if (la.kind == 24) {
			Get();
			result = expression(scope);
			Expect(25);
		} else if (la.kind == 17) {
			ExpressionList tupleFields; 
			Get();
			tupleFields = expressionList(scope);
			Expect(18);
			result = semantic.buildTuple(tupleFields); 
		} else SynErr(79);
		return result;
	}

	MultiplyOperator  multiplyOperator() {
		MultiplyOperator  op;
		op = null; 
		if (la.kind == 52) {
			Get();
			op = MultiplyOperator.TIMES; 
		} else if (la.kind == 53) {
			Get();
			op = MultiplyOperator.DIVIDE; 
		} else if (la.kind == 54) {
			Get();
			op = MultiplyOperator.MODULO; 
		} else SynErr(80);
		return op;
	}

	void booleanConstant() {
		if (la.kind == 56) {
			Get();
		} else if (la.kind == 57) {
			Get();
		} else SynErr(81);
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
				result = semantic.subscript(result.expectVariableExpression(err), subscript); semantic.insertComment("}"); 
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
		{T,T,x,x, T,x,x,T, x,x,T,x, x,T,T,x, x,x,x,x, x,T,T,T, x,x,x,x, T,T,T,x, x,T,T,x, T,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, x,x,x,x},
		{x,T,x,x, x,x,x,x, x,x,T,x, x,T,T,x, x,x,x,x, x,T,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x},
		{T,T,x,x, x,x,x,x, x,x,T,x, x,T,T,x, x,x,x,x, x,T,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x},
		{T,T,x,x, T,T,T,T, x,x,T,x, x,T,T,x, x,x,x,x, x,T,T,T, x,x,x,x, T,T,T,x, x,T,T,x, T,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, x,x,x,x},
		{T,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, T,T,T,x, x,T,T,x, T,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, x,x,x,x},
		{T,T,x,x, T,x,x,T, T,x,T,x, x,T,T,x, x,x,x,x, x,T,T,T, x,x,x,x, T,T,T,x, x,T,T,T, T,x,T,T, T,T,x,x, x,x,x,x, x,x,x,x, x,x,x,T, x,x,x,x},
		{x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, T,T,T,x, x,T,T,x, T,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, x,x,x,x},
		{x,T,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,T,T,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x},
		{x,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,T,x,x, x,x,x,x, T,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, T,T,T,x, x,x,x,x, x,x,x,T, T,T,x,x},
		{x,x,x,x, x,x,x,x, x,x,x,T, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,T, T,T,T,T, x,x,x,x, x,x,x,x},
		{x,T,T,x, x,x,x,x, x,x,x,x, x,x,x,x, x,T,x,x, x,x,x,x, T,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, x,x,x,x, T,x,x,x, x,x,x,x, x,x,x,T, T,T,x,x}

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
			case 32: s = "\"!\" expected"; break;
			case 33: s = "\"return\" expected"; break;
			case 34: s = "\"do\" expected"; break;
			case 35: s = "\"od\" expected"; break;
			case 36: s = "\"forall\" expected"; break;
			case 37: s = "\"->\" expected"; break;
			case 38: s = "\"llarof\" expected"; break;
			case 39: s = "\"if\" expected"; break;
			case 40: s = "\"fi\" expected"; break;
			case 41: s = "\"[]\" expected"; break;
			case 42: s = "\"|\" expected"; break;
			case 43: s = "\"&\" expected"; break;
			case 44: s = "\"+\" expected"; break;
			case 45: s = "\"-\" expected"; break;
			case 46: s = "\"~\" expected"; break;
			case 47: s = "\"#\" expected"; break;
			case 48: s = "\">\" expected"; break;
			case 49: s = "\">=\" expected"; break;
			case 50: s = "\"<\" expected"; break;
			case 51: s = "\"<=\" expected"; break;
			case 52: s = "\"*\" expected"; break;
			case 53: s = "\"/\" expected"; break;
			case 54: s = "\"\\\\\" expected"; break;
			case 55: s = "\"this\" expected"; break;
			case 56: s = "\"true\" expected"; break;
			case 57: s = "\"false\" expected"; break;
			case 58: s = "??? expected"; break;
			case 59: s = "this symbol not expected in gcl"; break;
			case 60: s = "this symbol not expected in gcl"; break;
			case 61: s = "invalid module"; break;
			case 62: s = "this symbol not expected in definitionPart"; break;
			case 63: s = "this symbol not expected in block"; break;
			case 64: s = "this symbol not expected in statementPart"; break;
			case 65: s = "this symbol not expected in statementPart"; break;
			case 66: s = "invalid definition"; break;
			case 67: s = "invalid statement"; break;
			case 68: s = "invalid type"; break;
			case 69: s = "invalid typeSymbol"; break;
			case 70: s = "invalid tupleType"; break;
			case 71: s = "invalid moreFieldsAndProcedures"; break;
			case 72: s = "invalid parameterDefinition"; break;
			case 73: s = "invalid variableAccessStatement"; break;
			case 74: s = "invalid variableAccessEtc"; break;
			case 75: s = "invalid writeItem"; break;
			case 76: s = "invalid simpleExpr"; break;
			case 77: s = "invalid relationalOperator"; break;
			case 78: s = "invalid addOperator"; break;
			case 79: s = "invalid factor"; break;
			case 80: s = "invalid multiplyOperator"; break;
			case 81: s = "invalid booleanConstant"; break;
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


