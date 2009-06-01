//
// Copyright (c) 2007 Tridium, Inc.
// Licensed under the Academic Free License version 3.0
//
// History:
//   2 Apr 07  Brian Frank  Creation
//

package sedonac.steps;

import java.io.*;
import java.util.*;
import sedona.Env;
import sedona.util.*;
import sedonac.*;
import sedonac.Compiler;
import sedonac.ir.*;
import sedonac.namespace.*;

/**
 * GenNativeTable generates nativetable.c which contains
 * the native lookup tables for a VM stage.
 */
public class GenNativeTable
  extends CompilerStep
{

  public GenNativeTable(Compiler compiler)
  {
    super(compiler);       
    
    for (int i=0; i<compiler.platform.nativePatches.length; ++i)
      patches.put(compiler.platform.nativePatches[i], "");
  }

  public void run()
  {
    File file = new File(compiler.outDir, "nativetable.h");
    log.message("  GenNativeTable [" + file + "]");

    try
    {
      Printer out = new Printer(new PrintWriter(new FileWriter(file)));
      generateH(out);
      out.close();
    }
    catch (Exception e)
    {
      throw err("Cannot write stub file", new Location(file), e);
    }

    file = new File(compiler.outDir, "nativetable.c");
    log.message("  GenNativeTable [" + file + "]");

    try
    {
      Printer out = new Printer(new PrintWriter(new FileWriter(file)));
      generate(out);
      out.close();
    }
    catch (Exception e)
    {
      throw err("Cannot write stub file", new Location(file), e);
    }
  }

  private void generateH(Printer out)
  {
    // header
    out.w("//").nl();
    out.w("// Generated by sedonac ").w(Env.version).nl();
    out.w("// ").w(Env.timestamp()).nl();
    out.w("//").nl();
    out.nl();
    out.w("#include \"sedona.h\"").nl();
    out.nl();

    // Fill in actual native checksum here (in place of 0)
    out.w("#define NATIVE_CHECKSUM_STRING \"0\"").nl(); 
    out.nl();
  }


  private void generate(Printer out)
  {
    findNativeMethods();
    orderKits();
    orderMethods();

    // header
    out.w("//").nl();
    out.w("// Generated by sedonac ").w(Env.version).nl();
    out.w("// ").w(Env.timestamp()).nl();
    out.w("//").nl();
    out.nl();
    out.w("#include \"sedona.h\"").nl();
    out.nl();

    // process each kit
    for (int i=0; i<nativeKits.length; ++i)
    {
      NativeKit kit = nativeKits[i];
      if (kit == null) continue;

      // header comment
      out.w("////////////////////////////////////////////////////////////////").nl();
      out.w("// ").w(kit.kitName).w(" (kitId=").w(kit.kitId).w(")").nl();
      out.w("////////////////////////////////////////////////////////////////").nl();
      out.nl();

      // forwards
      for (int j=0; j<kit.methods.length; ++j)
        forward(out, kit.methods[j]);

      // table for kit
      out.w("// native table for kit ").w(i).nl();
      out.w("NativeMethod ").w("kitNatives").w(i).w("[] = ").nl();
      out.w("{").nl();
      for (int j=0; j<kit.methods.length; ++j)
      {
        IrMethod m = kit.methods[j];
        out.w("  ").w(TextUtil.pad(toFuncName(m)+",", 30))
            .w("  // ").w(kit.kitId).w("::").w(j).nl();
      }
      out.w("};").nl();
      out.nl();
    }


    // native method table
    out.w("////////////////////////////////////////////////////////////////").nl();
    out.w("// Native Table").nl();
    out.w("////////////////////////////////////////////////////////////////").nl();
    out.nl();
    out.w("NativeMethod* ").w("nativeTable[] = ").nl();
    out.w("{").nl();
    for (int i=0; i<nativeKits.length; ++i)
    {
      NativeKit kit = nativeKits[i];
      String s = kit == null ? "NULL," : "kitNatives"+i+",";
      out.w("  ").w(TextUtil.pad(s, 15)).w("  // " + i).nl();
    }
    out.w("};").nl();
    out.nl();                                  

    // native id check
    out.w("////////////////////////////////////////////////////////////////").nl();
    out.w("// Native Id Check").nl();
    out.w("////////////////////////////////////////////////////////////////").nl();
    out.nl();            
    out.w("#ifdef SCODE_DEBUG").nl();                      
    out.w("int isNativeIdValid(int kitId, int methodId)").nl();
    out.w("{").nl();
    out.w("  switch(kitId)").nl();
    out.w("  {").nl();
    for (int i=0; i<nativeKits.length; ++i)
    {
      NativeKit kit = nativeKits[i];                     
      if (kit == null) continue;
      
      IrMethod[] methods = kit.methods;
      if (methods.length == 0) continue;
      if (methods[methods.length-1] == null)
        throw new IllegalStateException();
      
      out.w("    case ").w(kit.kitId).w(":").nl();
      out.w("      if (methodId >= ").w(methods.length).w(") return 0;").nl();
      out.w("      else return kitNatives").w(kit.kitId).w("[methodId] != NULL;").nl();
    }
    out.w("    default:").nl();
    out.w("       return 0;").nl();
    out.w("  }").nl();
    out.w("}").nl();
    out.w("#endif").nl();                      

    out.nl().nl();
  }

  private void forward(Printer out, IrMethod m)
  {
    if (m == null) return;

    // comment
    out.w("// ").w(m.ret).w(" ")
       .w(m.parent.name).w(".").w(m.name).w("(");
    for (int i=0; i<m.params.length; ++i)
    {
      Type p = m.params[i];
      if (i > 0) out.w(", ");
      out.w(p);
    }
    out.w(")").nl();

    // declaration
    out.w("Cell ").w(toFuncName(m)).w("(SedonaVM* vm, Cell* params);").nl().nl();
  }

  private String toFuncName(IrMethod m)
  {
    if (m == null) return "NULL";
    String s = m.parent.kit.name + "_" + m.parent.name + "_" + m.name;
    if (patches.get(m.qname()) != null) s += "_patch";
    return s;
  }                  
  
////////////////////////////////////////////////////////////////
// Find Native Methods
////////////////////////////////////////////////////////////////

  private void findNativeMethods()
  {
    HashMap map = new HashMap();
    IrKit[] kits = compiler.kits;
    for (int i=0; i<kits.length; ++i)
    {
      IrType[] types = kits[i].types;
      for (int j=0; j<types.length; ++j)
      {
        IrSlot[] slots= types[j].declared;
        for (int k=0; k<slots.length; ++k)
          if (slots[k].isMethod() && slots[k].isNative())
          {
            IrMethod m = (IrMethod)slots[k];
            Integer kitId = new Integer(m.nativeId.kitId);
            NativeKit kit = (NativeKit)map.get(kitId);
            if (kit == null) map.put(kitId, kit = new NativeKit(m));
            kit.acc.add(m);
          }
      }
    }
    this.nativeKits = (NativeKit[])map.values().toArray(new NativeKit[map.size()]);
  }

////////////////////////////////////////////////////////////////
// Order Native Methods
////////////////////////////////////////////////////////////////

  private void orderKits()
  {
    // first figure out the highest kit id;
    int maxKitId = 0;
    for (int i=0; i<nativeKits.length; ++i)
      maxKitId = Math.max(maxKitId, nativeKits[i].kitId);

    // allocate and map ordered array
    NativeKit[] ordered = new NativeKit[maxKitId+1];
    for (int i=0; i<nativeKits.length; ++i)
    {
      NativeKit k = nativeKits[i];
      ordered[k.kitId] = k;
    }

    // save back ordered array
    nativeKits = ordered;
  }

  private void orderMethods()
  {
    for (int i=0; i<nativeKits.length; ++i)
      orderMethods(nativeKits[i]);
  }

  private void orderMethods(NativeKit kit)
  {
    if (kit == null) return;

    // flatten into array
    kit.methods = (IrMethod[])kit.acc.toArray(new IrMethod[kit.acc.size()]);
    kit.acc = null;

    // first figure out the highest method id; and
    // check that all the kitIds are the same
    int kitId = kit.methods[0].nativeId.kitId;
    int maxMethodId = 0;
    for (int i=0; i<kit.methods.length; ++i)
    {
      IrMethod m = kit.methods[i];
      if (m.nativeId.kitId != kitId)
        err("Native kitIds must all be the same", m.nativeId.loc);
      maxMethodId = Math.max(maxMethodId, m.nativeId.methodId);
    }

    // allocate and map ordered array
    IrMethod[] ordered = new IrMethod[maxMethodId+1];
    for (int i=0; i<kit.methods.length; ++i)
    {
      IrMethod m = kit.methods[i];
      ordered[m.nativeId.methodId] = m;
    }

    // save back ordered array
    kit.methods = ordered;
  }

//////////////////////////////////////////////////////////////////////////
// NativeKit
//////////////////////////////////////////////////////////////////////////

  static class NativeKit
  {
    NativeKit(IrMethod m)
    {
      kitName = m.parent.kit.name;
      kitId = m.nativeId.kitId;
    }

    String kitName;
    int kitId;
    ArrayList acc = new ArrayList();
    IrMethod[] methods;
  }

//////////////////////////////////////////////////////////////////////////
// Printer
//////////////////////////////////////////////////////////////////////////

  public static class Printer extends PrintWriter
  {
    public Printer(PrintWriter out) { super(out); }
    public Printer w(Object s) { print(s); return this; }
    public Printer w(int i)    { print(i); return this; }
    public Printer indent()    { print(TextUtil.getSpaces(indent*2)); return this; }
    public Printer nl()        { println(); return this; }
    public int indent;
  }


////////////////////////////////////////////////////////////////
// Fields
////////////////////////////////////////////////////////////////

  NativeKit[] nativeKits;          
  HashMap patches = new HashMap();
}
