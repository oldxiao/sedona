//
// Copyright (c) 2008 Tridium, Inc.
// Licensed under the Academic Free License version 3.0
//
// History:
//   12 Jun 08  Brian Frank  Creation
//

package sedonac.test;

import java.io.*;
import java.net.*;
import java.util.*;
import sedona.*;
import sedona.Byte;
import sedona.Short;
import sedona.Long;
import sedona.Float;
import sedona.Double;
import sedona.offline.*;
import sedona.dasp.*;
import sedona.sox.*;
import sedona.util.*;
import sedona.xml.*;
import sedonac.Compiler;

/**
 * PstoreTest (over Sox)
 */
public class PstoreTest
  extends AbstractSoxTest  
  implements Constants
{

  public void test()          
    throws Exception
  {
    FileUtil.delete(testDir(), null);
    super.test();
  }

  public void doTest()
    throws Exception
  {                        
    client = new SoxClient(sock, InetAddress.getLocalHost(), 1876, "admin", "pw");
    client.connect(null);   
    System.out.println("Connected to pstore VM");    
    verifyStatus();   
    verifyReadWrite();   
    client.close();     
  }  

////////////////////////////////////////////////////////////////
// Status
////////////////////////////////////////////////////////////////

  public void verifyStatus()
    throws Exception
  {
    verifyEq(readByte(a, "status"), 0);
    verifyEq(readByte(b, "status"), 0);
    verifyEq(readByte(c, "status"), 0);
    
    verifyEq(readByte(badParent, "status"), 2);
    verifyEq(readByte(badOff1,   "status"), 4);
    verifyEq(readByte(badOff2,   "status"), 4);
    verifyEq(readByte(badSize1,  "status"), 5);
    verifyEq(readByte(badSize2,  "status"), 5);
    verifyEq(readByte(badDup1,   "status"), 6);
    verifyEq(readByte(badDup2,   "status"), 6);
    verifyEq(readByte(badDup3,   "status"), 6);
    verifyEq(readByte(badDup4,   "status"), 6);
  }

////////////////////////////////////////////////////////////////
// Read/Write
////////////////////////////////////////////////////////////////

  public void verifyReadWrite()
    throws Exception
  {
    verifyReadWrite(a);
    verifyReadWrite(b);
    verifyReadWrite(c);
  }                            
  
  public void verifyReadWrite(OfflineComponent c)
    throws Exception
  {                                              
    int n = c.name().charAt(0);
    
    invoke(c, "verifyRead", -1);  verifyEq(readInt(c, "result"), -1);
    invoke(c, "verifyRead", 100); verifyEq(readInt(c, "result"), -1);
    invoke(c, "verifyRead", 0);   verifyEq(readInt(c, "result"), 0);
    invoke(c, "verifyRead", 99);  verifyEq(readInt(c, "result"), 0);
    
    invoke(c, "verifyWrite", (-1  << 8) | 'x');  verifyEq(readInt(c, "result"), 0);
    invoke(c, "verifyWrite", (100 << 8) | 'x');  verifyEq(readInt(c, "result"), 0);
    invoke(c, "verifyWrite", (0 << 8) | 'a');    verifyEq(readInt(c, "result"), 1);
    invoke(c, "verifyWrite", (1 << 8) | 'b');    verifyEq(readInt(c, "result"), 1);
    invoke(c, "verifyWrite", (2 << 8) | 'c');    verifyEq(readInt(c, "result"), 1);
    invoke(c, "verifyWrite", (99 << 8) | n);     verifyEq(readInt(c, "result"), 1);
    
    invoke(c, "verifyRead", 0);  verifyEq(readInt(c, "result"), 'a');
    invoke(c, "verifyRead", 1);  verifyEq(readInt(c, "result"), 'b');
    invoke(c, "verifyRead", 2);  verifyEq(readInt(c, "result"), 'c');
    invoke(c, "verifyRead", 99); verifyEq(readInt(c, "result"), n);
  }
  
////////////////////////////////////////////////////////////////
// Setup
////////////////////////////////////////////////////////////////

  public OfflineApp buildApp()
    throws Exception
  {      
    this.schema = Schema.load(new KitPart[]
    {
      KitPart.forLocalKit("sys"),
      KitPart.forLocalKit("sox"),
      KitPart.forLocalKit("inet"),
      KitPart.forLocalKit("pstore"),
      KitPart.forLocalKit("pstoreFS"),
    });

    this.app = new OfflineApp(schema);
    this.app.setInt("meta", 0xf);
    this.plat   = app.add(app, new OfflineComponent(schema.type("sys::PlatformService"), "plat"));
    this.sox    = app.add(app, new OfflineComponent(schema.type("sox::SoxService"), "sox"));
    this.users  = app.add(app, new OfflineComponent(schema.type("sys::UserService"), "users"));    
    this.admin  = addAdmin(users, "admin", "pw");
    this.pstore = app.add(app, new OfflineComponent(schema.type("pstoreFS::FsPstoreService"), "pstore"));    
    
    this.a = addFile(pstore, "a",   0, 100);    
    this.b = addFile(pstore, "b", 100, 100);    
    this.c = addFile(pstore, "c", 200, 100);    

    badOff1   = addFile(pstore, "badOff1", -100, 10);    
    badOff2   = addFile(pstore, "badOff2", 800000, 10);    
    badSize1  = addFile(pstore, "badSz1",  300, 0);    
    badSize2  = addFile(pstore, "badSz2",  300, 800000);    
    badDup1   = addFile(pstore, "badDup1", 1000, 21);    
    badDup2   = addFile(pstore, "badDup2", 1020, 50);    
    badDup3   = addFile(pstore, "badDup3", 1020, 100);    
    badDup4   = addFile(pstore, "badDup4", 1040, 10);    
    badParent = addFile(app, "badPar",  300, 1);    
        
    app.assignIds();
    // app.dump();
    return app;                 
  }           
  
  OfflineComponent addFile(OfflineComponent parent, String name, int offset, int size)
  {                                         
    Type type = schema.type("pstore::TestPstoreFile");   
    OfflineComponent c = new OfflineComponent(type, name);
    c.setInt("resvOffset", offset);                       
    c.setInt("resvSize", size);                       
    app.add(parent, c);
    return c;
  }                        

//////////////////////////////////////////////////////////////////////////
// Database
//////////////////////////////////////////////////////////////////////////

  OfflineApp app;
    OfflineComponent plat;
    OfflineComponent sox;
    OfflineComponent users;
      OfflineComponent admin;
    OfflineComponent pstore;
      OfflineComponent a;
      OfflineComponent b;
      OfflineComponent c;
      OfflineComponent badOff1;
      OfflineComponent badOff2;
      OfflineComponent badSize1;
      OfflineComponent badSize2;
      OfflineComponent badDup1;
      OfflineComponent badDup2;
      OfflineComponent badDup3;
      OfflineComponent badDup4;
    OfflineComponent badParent;

//////////////////////////////////////////////////////////////////////////
// Fields
//////////////////////////////////////////////////////////////////////////

}
