/* :folding=explicit:collapseFolds=1: */

/*
 * $Id$
 *
 * Copyright (C) 2004 Slava Pestov.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * DEVELOPERS AND CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package factor.compiler;

import factor.*;
import java.lang.reflect.*;
import java.util.*;
import org.objectweb.asm.*;

public class FactorCompiler implements Constants
{
	public final FactorInterpreter interp;

	public final FactorWord word;
	public final String className;
	public final FactorClassLoader loader;
	public String method;

	private int base;
	private int allotD;
	private int allotR;

	public FactorArray datastack;
	public FactorArray callstack;

	private int literalCount;

	private Map literals;

	public StackEffect effect;

	/**
	 * getStackEffect() turns these into arrays and places them in the
	 * returned object.
	 */
	public Cons inDtypes, inRtypes;

	private Cons aux;
	private int auxCount;

	//{{{ FactorCompiler constructor
	/**
	 * For balancing.
	 */
	public FactorCompiler(FactorInterpreter interp)
	{
		this(interp,null,null,null);
		init(0,0,0,null);
	} //}}}

	//{{{ FactorCompiler constructor
	/**
	 * For compiling.
	 */
	public FactorCompiler(FactorInterpreter interp,
		FactorWord word, String className,
		FactorClassLoader loader)
	{
		this.interp = interp;
		this.word = word;
		this.className = className;
		this.loader = loader;

		literals = new HashMap();

		datastack = new FactorArray();
		callstack = new FactorArray();
	} //}}}

	//{{{ getInterpreter() method
	public FactorInterpreter getInterpreter()
	{
		return interp;
	} //}}}

	//{{{ init() method
	public void init(int base, int allotD, int allotR, String method)
	{
		effect = new StackEffect();

		this.base = base;

		datastack.top = 0;
		callstack.top = 0;

		for(int i = 0; i < allotD; i++)
		{
			Result r = new Result(base + i,this,null,
				Object.class,true);
			datastack.push(r);
			inDtypes = new Cons(r,inDtypes);
		}

		for(int i = 0; i < allotR; i++)
		{
			Result r = new Result(base + allotD + i,this,null,
				Object.class,true);
			callstack.push(r);
			inRtypes = new Cons(r,inRtypes);
		}

		this.allotD = allotD;
		this.allotR = allotR;
		effect.inD = allotD;
		effect.inR = allotR;

		this.method = method;
	} //}}}

	//{{{ getAllotedEffect() method
	public StackEffect getAllotedEffect()
	{
		return new StackEffect(allotD,allotR,0,0);
	} //}}}

	//{{{ ensure() method
	public void ensure(FactorArray stack, Class type)
	{
		if(stack.top == 0)
		{
			Result r = new Result(allocate(),this,null,type);
			if(stack == datastack)
			{
				inDtypes = new Cons(r,inDtypes);
				effect.inD++;
			}
			else if(stack == callstack)
			{
				inRtypes = new Cons(r,inRtypes);
				effect.inR++;
			}
			stack.push(r);
		}
	} //}}}

	//{{{ ensure() method
	/**
	 * Ensure stack has at least 'count' elements.
	 * Eg, if count is 4 and stack is A B,
	 * stack will become RESULT RESULT A B.
	 * Used when deducing stack effects.
	 */
	public void ensure(FactorArray stack, Class[] types)
	{
		int top = stack.top;
		if(top < types.length)
		{
			Cons typespec = null;

			if(stack == datastack)
				effect.inD += (types.length - top);
			else if(stack == callstack)
				effect.inR += (types.length - top);

			stack.ensurePush(types.length - top);
			System.arraycopy(stack.stack,0,stack.stack,
				types.length - top,top);
			for(int i = 0; i < types.length - top; i++)
			{
				int local = allocate();
				Result r = new Result(
					local,this,null,types[i]);
				stack.stack[i] = r;
				typespec = new Cons(r,typespec);
			}
			stack.top = types.length;

			if(stack == datastack)
				inDtypes = Cons.nappend(inDtypes,typespec);
			else if(stack == callstack)
				inRtypes = Cons.nappend(inRtypes,typespec);
		}
	} //}}}

	//{{{ ensure() method
	/**
	 * Ensure stack has at least 'count' elements.
	 * Eg, if count is 4 and stack is A B,
	 * stack will become RESULT RESULT A B.
	 * Used when deducing stack effects.
	 */
	public void ensure(FactorArray stack, int count)
	{
		Class[] types = new Class[count];
		for(int i = 0; i < types.length; i++)
			types[i] = Object.class;
		ensure(stack,types);
	} //}}}

	//{{{ consume() method
	public void consume(FactorArray stack, int count)
	{
		ensure(stack,count);
		stack.top -= count;
	} //}}}

	//{{{ produce() method
	public void produce(FactorArray stack, int count)
	{
		for(int i = 0; i < count; i++)
		{
			int local = allocate();
			stack.push(new Result(local,this,null,Object.class));
		}
	} //}}}

	//{{{ apply() method
	public void apply(StackEffect se)
	{
		consume(datastack,se.inD);
		produce(datastack,se.outD);
		consume(callstack,se.inR);
		produce(callstack,se.outR);
	} //}}}

	//{{{ getTypeSpec() method
	private Class[] getTypeSpec(Cons list)
	{
		if(list == null)
			return new Class[0];

		int length = list.length();
		Class[] typespec = new Class[length];
		int i = 0;
		while(list != null)
		{
			typespec[length - i - 1]
				= ((FlowObject)list.car).getType();
			i++;
			list = list.next();
		}

		return typespec;
	} //}}}

	//{{{ getStackEffect() method
	public StackEffect getStackEffect()
	{
		effect.inDtypes = getTypeSpec(inDtypes);

		effect.outD = datastack.top;

		effect.outDtypes = new Class[datastack.top];
		for(int i = 0; i < datastack.top; i++)
		{
			effect.outDtypes[i] = ((FlowObject)datastack.stack[i])
				.getType();
		}

		effect.inRtypes = getTypeSpec(inRtypes);

		effect.outR = callstack.top;

		effect.outRtypes = new Class[callstack.top];
		for(int i = 0; i < callstack.top; i++)
		{
			effect.outRtypes[i] = ((FlowObject)callstack.stack[i])
				.getType();
		}

		return (StackEffect)effect.clone();
	} //}}}

	//{{{ compileCore() method
	public void compileCore(Cons definition, ClassWriter cw,
		StackEffect effect, RecursiveState recursiveCheck)
		throws Exception
	{
		RecursiveForm last = recursiveCheck.last();
		last.method = "core";
		last.className = className;
		last.loader = loader;

		compileMethod(definition,cw,"core",effect,word,recursiveCheck,true);
	} //}}}

	//{{{ compileFieldInit() method
	private void compileFieldInit(CodeVisitor mw, Label start)
	{
		mw.visitFieldInsn(GETSTATIC,className,"initialized","Z");
		mw.visitJumpInsn(IFNE,start);
		mw.visitInsn(ICONST_1);
		mw.visitFieldInsn(PUTSTATIC,className,"initialized","Z");
		mw.visitVarInsn(ALOAD,0);
		mw.visitMethodInsn(INVOKESTATIC,className,"setFields",
			"(Lfactor/FactorInterpreter;)V");
	} //}}}

	//{{{ compileReturn() method
	/**
	 * Once the word finishes executing, any return values need to be
	 * passed up.
	 */
	private void compileReturn(CodeVisitor mw, Label end,
		StackEffect effect) throws Exception
	{
		// special case where return value is passed on
		// JVM operand stack

		// note: in each branch, must visit end label before RETURN!
		if(effect.outD == 0 && effect.outR == 0)
		{
			mw.visitLabel(end);
			mw.visitInsn(RETURN);
		}
		else if(effect.outD == 1 && effect.outR == 0)
		{
			pop(datastack,mw,Object.class);
			mw.visitLabel(end);
			mw.visitInsn(ARETURN);
		}
		else
		{
			// store datastack in a local
			mw.visitVarInsn(ALOAD,0);
			mw.visitFieldInsn(GETFIELD,
				"factor/FactorInterpreter",
				"datastack",
				"Lfactor/FactorArray;");
			int datastackLocal = allocate();
			mw.visitVarInsn(ASTORE,datastackLocal);

			for(int i = 0; i < datastack.top; i++)
			{
				mw.visitVarInsn(ALOAD,datastackLocal);
				((FlowObject)datastack.stack[i])
					.pop(mw,Object.class);
				mw.visitMethodInsn(INVOKEVIRTUAL,
					"factor/FactorArray",
					"push",
					"(Ljava/lang/Object;)V");
			}

			datastack.top = 0;

			// store callstack in a local
			mw.visitVarInsn(ALOAD,0);
			mw.visitFieldInsn(GETFIELD,
				"factor/FactorInterpreter",
				"callstack",
				"Lfactor/FactorArray;");
			int callstackLocal = allocate();
			mw.visitVarInsn(ASTORE,callstackLocal);

			for(int i = 0; i < callstack.top; i++)
			{
				mw.visitVarInsn(ALOAD,callstackLocal);
				((FlowObject)callstack.stack[i])
					.pop(mw,Object.class);
				mw.visitMethodInsn(INVOKEVIRTUAL,
					"factor/FactorArray",
					"push",
					"(Ljava/lang/Object;)V");
			}

			callstack.top = 0;

			mw.visitLabel(end);
			mw.visitInsn(RETURN);
		}
	} //}}}

	//{{{ compileMethod() method
	/**
	 * Compiles a method.
	 */
	public void compileMethod(Cons definition, ClassWriter cw,
		String methodName, StackEffect effect, FactorWord word,
		RecursiveState recursiveCheck, boolean fieldInit)
		throws Exception
	{
		String signature = effect.getCorePrototype();

		CodeVisitor mw = cw.visitMethod(ACC_PUBLIC | ACC_STATIC,
			methodName,signature,null,null);

		Label start = recursiveCheck.get(word).label;

		if(fieldInit)
			compileFieldInit(mw,start);

		mw.visitLabel(start);

		compile(definition,mw,recursiveCheck);

		Label end = new Label();

		compileReturn(mw,end,effect);

		compileExceptionHandler(mw,start,end,word);

		mw.visitMaxs(0,0);
	} //}}}

	//{{{ compileExceptionHandler() method
	private void compileExceptionHandler(CodeVisitor mw,
		Label start, Label end, FactorWord word)
	{
		// otherwise no code can throw exception etc
		if(start.getOffset() != end.getOffset())
		{
			// Now compile exception handler.

			Label target = new Label();
			mw.visitLabel(target);

			mw.visitVarInsn(ASTORE,1);
			mw.visitVarInsn(ALOAD,0);
			mw.visitFieldInsn(GETSTATIC,className,literal(word),
				"Ljava/lang/Object;");
			mw.visitTypeInsn(CHECKCAST,"factor/FactorWord");
			mw.visitVarInsn(ALOAD,1);

			mw.visitMethodInsn(INVOKEVIRTUAL,"factor/FactorInterpreter",
				"compiledException",
				"(Lfactor/FactorWord;Ljava/lang/Throwable;)V");

			mw.visitVarInsn(ALOAD,1);
			mw.visitInsn(ATHROW);

			mw.visitTryCatchBlock(start,end,target,"java/lang/Throwable");
		}
	} //}}}

	//{{{ compile() method
	/**
	 * Compiles a quotation.
	 */
	public void compile(Cons definition, CodeVisitor mw,
		RecursiveState recursiveCheck) throws Exception
	{
		while(definition != null)
		{
			Object obj = definition.car;
			if(obj instanceof FactorWord)
			{
				compileWord((FactorWord)obj,mw,recursiveCheck,
					definition.cdr == null);
			}
			else
				pushLiteral(obj,recursiveCheck);

			definition = definition.next();
		}
	} //}}}

	//{{{ compileWord() method
	private void compileWord(FactorWord w, CodeVisitor mw,
		RecursiveState recursiveCheck,
		boolean tail) throws Exception
	{
		if(tail && interp != null && interp.verboseCompile)
			System.err.println("Tail: " + recursiveCheck.last());
		recursiveCheck.last().tail = tail;

		RecursiveForm rec = recursiveCheck.get(w);

		try
		{
			boolean recursiveCall;
			if(rec == null)
			{
				recursiveCall = false;
				recursiveCheck.add(w,
					new StackEffect(),
					className,loader,
					"core");
				recursiveCheck.last().tail
					= (w.def instanceof FactorPrimitiveDefinition);
			}
			else
			{
				recursiveCall = true;
				rec.active = true;
			}

			compileCallTo(w,mw,recursiveCheck,recursiveCall);
		}
		finally
		{
			if(rec == null)
				recursiveCheck.remove(w);
			else
			{
				rec.active = false;
				rec.tail = false;
			}
		}
	} //}}}

	//{{{ compileCallTo() method
	private void compileCallTo(FactorWord w, CodeVisitor mw,
		RecursiveState recursiveCheck,
		boolean recursiveCall) throws Exception
	{
		if(w.def == null)
			throw new FactorUndefinedWordException(w.name);

		FactorWordDefinition d = w.def;

		if(mw == null)
		{
			recursiveCheck.last().effect = getStackEffect();
			d.getStackEffect(recursiveCheck,this);
			return;
		}

		if(!recursiveCall)
		{
			if(w.inline)
			{
				recursiveCheck.last().effect = getStackEffect();
				d.compileImmediate(mw,this,recursiveCheck);
				return;
			}
			else if(d instanceof FactorCompoundDefinition)
			{
				w.compile(interp,recursiveCheck);
				if(d == w.def)
				{
					throw new FactorCompilerException(word + " depends on " + w + " which cannot be compiled");
				}
				d = w.def;
			}
		}

		d.compileCallTo(mw,this,recursiveCheck);
	} //}}}

	//{{{ push() method
	/**
	 * Generates code for pushing the top of the JVM stack onto the
	 * data stack. Also generates code for converting this value to
	 * the given type.
	 */
	public void push(FactorArray stack, CodeVisitor mw, Class type)
		throws Exception
	{
		int local = allocate();
		Result r = new Result(local,this,null,type);
		stack.push(r);
		r.push(mw,type);
	} //}}}

	//{{{ pushLiteral() method
	public void pushLiteral(Object literal, RecursiveState recursiveCheck)
	{
		RecursiveForm word = recursiveCheck.last();

		if(literal == null)
			datastack.push(new Null(this,word));
		else if(literal instanceof Cons)
		{
			datastack.push(new CompiledList((Cons)literal,this,
				recursiveCheck));
		}
		else if(literal instanceof String)
		{
			datastack.push(new ConstantPoolString((String)literal,
				this,word));
		}
		else
			datastack.push(new Literal(literal,this, word));
	} //}}}

	//{{{ pop() method
	/**
	 * Generates code for popping the top of the data stack onto
	 * the JVM stack. Also generates code for converting this value to
	 * the given type.
	 */
	public void pop(FactorArray stack, CodeVisitor mw, Class type)
		throws Exception
	{
		FlowObject obj = (FlowObject)datastack.pop();
		obj.pop(mw,type);
	} //}}}

	//{{{ popLiteral() method
	/**
	 * Pops a literal off the datastack or throws an exception.
	 */
	public Object popLiteral() throws FactorException
	{
		FlowObject obj = (FlowObject)datastack.pop();
		return obj.getLiteral();
	} //}}}

	//{{{ allocate() method
	/**
	 * Allocate a local variable.
	 */
	public int allocate()
	{
		// inefficient!
		int i = base;
		for(;;)
		{
			if(allocate(i,datastack) && allocate(i,callstack))
				return i;
			else
				i++;
		}
	} //}}}

	//{{{ allocate() method
	/**
	 * Return true if not in use, false if in use.
	 */
	private boolean allocate(int local, FactorArray stack)
	{
		for(int i = 0; i < stack.top; i++)
		{
			FlowObject obj = (FlowObject)stack.stack[i];
			if(obj.usingLocal(local))
				return false;
		}
		return true;
	} //}}}

	//{{{ literal() method
	public String literal(Object obj)
	{
		Integer i = (Integer)literals.get(obj);
		int literal;
		if(i == null)
		{
			literal = literalCount++;
			literals.put(obj,new Integer(literal));
		}
		else
			literal = i.intValue();

		return "literal_" + literal;
	} //}}}

	//{{{ auxiliary() method
	public String auxiliary(FactorWord word, Cons code, StackEffect effect,
		RecursiveState recursiveCheck) throws Exception
	{
		FactorArray savedDatastack = (FactorArray)
			datastack.clone();
		FactorArray savedCallstack = (FactorArray)
			callstack.clone();

		String method = "aux_" + FactorJava.getSanitizedName(word.name)
			+ "_" + (auxCount++);

		recursiveCheck.last().method = method;
		aux = new Cons(new AuxiliaryQuotation(
			method,savedDatastack,savedCallstack,
			code,effect,word,this,recursiveCheck),aux);

		return method;
	} //}}}

	//{{{ generateAuxiliary() method
	public void generateAuxiliary(ClassWriter cw) throws Exception
	{
		while(aux != null)
		{
			AuxiliaryQuotation q = (AuxiliaryQuotation)aux.car;
			// order of these two important, in case
			// compilation of q adds more quotations to aux list
			aux = aux.next();
			q.compile(this,cw);
		}
	} //}}}

	//{{{ normalizeStacks() method
	public void normalizeStacks(CodeVisitor mw)
		throws Exception
	{
		int datastackTop = datastack.top;
		datastack.top = 0;
		int callstackTop = callstack.top;
		callstack.top = 0;

		localsToStack(callstack,callstackTop,mw);
		localsToStack(datastack,datastackTop,mw);
		stackToLocals(datastack,datastackTop,mw);
		stackToLocals(callstack,callstackTop,mw);
	} //}}}

	//{{{ localsToStack() method
	private void localsToStack(FactorArray stack, int top,
		CodeVisitor mw) throws Exception
	{
		for(int i = top - 1; i >= 0; i--)
		{
			FlowObject obj = (FlowObject)stack.stack[i];
			obj.pop(mw,Object.class);
		}
	} //}}}

	//{{{ stackToLocals() method
	private void stackToLocals(FactorArray stack, int top,
		CodeVisitor mw) throws Exception
	{
		for(int i = 0; i < top; i++)
			push(stack,mw,Object.class);
	} //}}}

	//{{{ generateArgs() method
	/**
	 * Generate instructions for copying arguments from the allocated
	 * local variables to the JVM stack, doing type conversion in the
	 * process.
	 */
	public void generateArgs(CodeVisitor mw, int inD, int inR, Class[] args)
		throws Exception
	{
		for(int i = 0; i < inD; i++)
		{
			FlowObject obj = (FlowObject)datastack.stack[
				datastack.top - inD + i];
			obj.pop(mw,args == null ? Object.class : args[i]);
		}

		datastack.top -= inD;

		for(int i = 0; i < inR; i++)
		{
			FlowObject obj = (FlowObject)callstack.stack[
				callstack.top - inR + i];
			obj.pop(mw,args == null ? Object.class : args[i]);
		}

		callstack.top -= inR;
	} //}}}

	//{{{ generateReturn() method
	public void generateReturn(CodeVisitor mw, int outD, int outR)
		throws Exception
	{
		if(outD == 0 && outR == 0)
		{
			// do nothing
		}
		else if(outD == 1 && outR == 0)
		{
			push(datastack,mw,Object.class);
		}
		else
		{
			// transfer from data stack to JVM locals

			// allocate the appropriate number of locals

			if(outD != 0)
			{
				produce(datastack,outD);

				// store the datastack instance somewhere
				mw.visitVarInsn(ALOAD,0);
				mw.visitFieldInsn(GETFIELD,
					"factor/FactorInterpreter",
					"datastack",
					"Lfactor/FactorArray;");
				int datastackLocal = allocate();
				mw.visitVarInsn(ASTORE,datastackLocal);
	
				// put all elements from the real datastack
				// into locals
				for(int i = 0; i < outD; i++)
				{
					mw.visitVarInsn(ALOAD,datastackLocal);
					mw.visitMethodInsn(INVOKEVIRTUAL,
						"factor/FactorArray",
						"pop",
						"()Ljava/lang/Object;");
	
					Result destination = (Result)
						datastack.stack[
						datastack.top - i - 1];
	
					destination.push(mw,Object.class);
				}
			}

			if(outR != 0)
			{
				produce(callstack,outR);

				mw.visitVarInsn(ALOAD,0);
				mw.visitFieldInsn(GETFIELD,
					"factor/FactorInterpreter",
					"callstack",
					"Lfactor/FactorArray;");
				int callstackLocal = allocate();
				mw.visitVarInsn(ASTORE,callstackLocal);

				// put all elements from the real callstack
				// into locals
				for(int i = 0; i < outR; i++)
				{
					mw.visitVarInsn(ALOAD,callstackLocal);
					mw.visitMethodInsn(INVOKEVIRTUAL,
						"factor/FactorArray",
						"pop",
						"()Ljava/lang/Object;");
	
					Result destination = (Result)
						callstack.stack[
						callstack.top - i - 1];
	
					destination.push(mw,Object.class);
				}
			}
		}
	} //}}}

	//{{{ generateFields() method
	public void generateFields(ClassWriter cw)
		throws Exception
	{
		for(int i = 0; i < literalCount; i++)
		{
			cw.visitField(ACC_PRIVATE | ACC_STATIC,"literal_" + i,
				"Ljava/lang/Object;",null,null);
		}

		CodeVisitor mw = cw.visitMethod(ACC_PRIVATE | ACC_STATIC,
			"setFields","(Lfactor/FactorInterpreter;)V",null,null);

		Iterator entries = literals.entrySet().iterator();
		while(entries.hasNext())
		{
			Map.Entry entry = (Map.Entry)entries.next();
			Object literal = entry.getKey();
			int index = ((Integer)entry.getValue()).intValue();

			generateParse(mw,literal,0);
			mw.visitFieldInsn(PUTSTATIC,
				className,
				"literal_" + index,
				"Ljava/lang/Object;");
		}

		mw.visitInsn(RETURN);

		mw.visitMaxs(0,0);
	} //}}}

	//{{{ generateParse() method
	public void generateParse(CodeVisitor mw, Object obj, int interpLocal)
	{
		mw.visitLdcInsn(FactorReader.getVocabularyDeclaration(obj)
			+ FactorReader.unparseObject(obj));
		mw.visitVarInsn(ALOAD,interpLocal);
		mw.visitMethodInsn(INVOKESTATIC,
			"factor/FactorReader",
			"parseObject",
			"(Ljava/lang/String;Lfactor/FactorInterpreter;)"
			+ "Ljava/lang/Object;");
	} //}}}

	//{{{ getStackEffectOrNull() method
	public StackEffect getStackEffectOrNull(FactorWordDefinition def)
	{
		try
		{
			return def.getStackEffect(interp);
		}
		catch(Exception e)
		{
			//System.err.println("WARNING: " + e);
			//System.err.println(def);
			return null;
		}
	} //}}}
}
