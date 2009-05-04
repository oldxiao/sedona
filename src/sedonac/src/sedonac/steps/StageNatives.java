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
import java.util.zip.*;
import sedona.Env;
import sedona.util.*;
import sedona.xml.*;
import sedonac.*;
import sedonac.Compiler;
import sedonac.ir.*;

/**
 * StageNatives
 */
public class StageNatives
  extends InitImageCompile
{

//////////////////////////////////////////////////////////////////////////
// Run
//////////////////////////////////////////////////////////////////////////

  public StageNatives(Compiler compiler)
  {
    super(compiler);
    this.xmlFile  = compiler.input;
    this.xmlDir   = xmlFile.getParentFile();
    this.xml      = compiler.xml;
    this.stageDir = compiler.outDir;
  }

  public void run()
  {
    try
    {
      log.message("  StageVM [" + stageDir + "]");
      makeStageDir();
      copySourceDirs();
    }
    catch (XException e)
    {
      throw err(e);
    }
    catch (CompilerException e)
    {
      throw e;
    }
    catch (Exception e)
    {
      throw err("Cannot stage VM", new Location(stageDir), e);
    }
  }

//////////////////////////////////////////////////////////////////////////
// Make Dir
//////////////////////////////////////////////////////////////////////////

  public void makeStageDir()
  {
    // nuke it first to get a clean slate
    try
    {
      FileUtil.delete(stageDir, log);
    }
    catch (IOException e)
    {
      err("Cannot delete stage dir", new Location(stageDir));
    }

    // create it nice and fresh
    try
    {
      FileUtil.mkdir(stageDir, log);
    }
    catch (IOException e)
    {
      err("Cannot make stage dir", new Location(stageDir));
    }
  }


//////////////////////////////////////////////////////////////////////////
// Copy Source Dirs
//////////////////////////////////////////////////////////////////////////

  public void copySourceDirs()
  { 
    String[] paths = compiler.platform.nativePaths;
    if (paths.length == 0) throw err("Must have at least one <nativeSource> element", new Location(xml));
    for (int i=0; i<paths.length; ++i)
      copySourceDir(paths[i]);
  }

  public void copySourceDir(String path)
  {                                                                    
    Location loc = new Location(xml);
    
    if (!path.startsWith("/"))
      throw err("Paths must start with / and be relative to sedona home: " + path, loc);
    File dir = new File(Env.home, path.substring(1));
    if (!dir.exists() || !dir.isDirectory())
      throw err("Source path not found '" + dir + "'", loc);

    File[] files = dir.listFiles();
    log.verbose("    Copy '" + dir + "' [" + files.length + " files]");
    for (int i=0; i<files.length; ++i)
    {
      File f = files[i];
      if (f.isDirectory()) continue;
      try
      {
        FileUtil.copyFile(f, new File(stageDir, f.getName()));
      }
      catch (IOException e)
      {
        throw err("Cannot copy file", new Location(f), e);
      }
    }
  }

//////////////////////////////////////////////////////////////////////////
// Fields
//////////////////////////////////////////////////////////////////////////

  File stageDir;
}
