package org.freedesktop.dbus;

import java.util.Map;
import java.util.HashMap;

class StructStruct
{
   public static Map<StructStruct, String> fillPackages(Map<StructStruct, String> structs, String pack)
   {
      Map<StructStruct, String> newmap = new HashMap<StructStruct, String>();
      for (StructStruct ss: structs.keySet()) {
         String type = structs.get(ss);
         if (null == ss.pack) ss.pack = pack;
         newmap.put(ss, type);
      }
      return newmap;
   }
   public String name;
   public String pack;
   public StructStruct(String name)
   {
      this.name = name;
   }
   public StructStruct(String name, String pack)
   {
      this.name = name;
      this.pack = pack;
   }
   public int hashCode()
   {
      return name.hashCode();
   }
   public boolean equals(Object o)
   {
      if (!(o instanceof StructStruct)) return false;
      if (!name.equals(((StructStruct) o).name)) return false;
      return true;
   }
   public String toString()
   {
      return "<"+name+", "+pack+">";
   }
}