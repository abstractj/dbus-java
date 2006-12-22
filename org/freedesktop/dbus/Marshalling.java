/*
   D-Bus Java Bindings
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
*/
package org.freedesktop.dbus;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.types.DBusListType;
import org.freedesktop.dbus.types.DBusMapType;
import org.freedesktop.dbus.types.DBusStructType;

/**
 * Contains static methods for marshalling values.
 */
public class Marshalling
{
   private static Map<Type, String[]> typeCache = new HashMap<Type, String[]>();
   /**
    * Will return the DBus type corresponding to the given Java type.
    * Note, container type should have their ParameterizedType not their
    * Class passed in here.
    * @param c The Java type.
    * @return The DBus type.
    * @throws DBusException If the given type cannot be converted to a DBus type.
    */
   public static String[] getDBusType(Type c) throws DBusException
   {
      String[] cached = typeCache.get(c);
      if (null != cached) return cached;
      cached = getDBusType(c, false);
      typeCache.put(c, cached);
      return cached;
   }
   /**
    * Will return the DBus type corresponding to the given Java type.
    * Note, container type should have their ParameterizedType not their
    * Class passed in here.
    * @param c The Java type.
    * @param basic If true enforces this to be a non-compound type. (compound types are Maps, Structs and Lists/arrays).
    * @return The DBus type.
    * @throws DBusException If the given type cannot be converted to a DBus type.
    */
   public static String[] getDBusType(Type c, boolean basic) throws DBusException
   {
      return recursiveGetDBusType(c, basic, 0);
   }
   private static StringBuffer[] out = new StringBuffer[10];
   public static String[] recursiveGetDBusType(Type c, boolean basic, int level) throws DBusException
   {
      if (out.length <= level) {
         StringBuffer[] newout = new StringBuffer[out.length];
         System.arraycopy(out, 0, newout, 0, out.length);
      }
      if (null == out[level]) out[level] = new StringBuffer();
      else out[level].delete(0, out.length);      

      if (basic && !(c instanceof Class))
         throw new DBusException(c+" is not a basic type");

      if (c instanceof TypeVariable) out[level].append('v');
      else if (c instanceof GenericArrayType) {
         out[level].append('a');
         String[] s = recursiveGetDBusType(((GenericArrayType) c).getGenericComponentType(), false, level+1);
         if (s.length != 1) throw new DBusException("Multi-valued array types not permitted");
         out[level].append(s[0]);
      }
      else if (c instanceof ParameterizedType) {
         ParameterizedType p = (ParameterizedType) c;
         if (p.getRawType().equals(Map.class)) {
            out[level].append("a{");
            Type[] t = p.getActualTypeArguments();
            try {
               String[] s = recursiveGetDBusType(t[0], true, level+1);
               if (s.length != 1) throw new DBusException("Multi-valued array types not permitted");
               out[level].append(s[0]);
               s = recursiveGetDBusType(t[1], false, level+1);
               if (s.length != 1) throw new DBusException("Multi-valued array types not permitted");
               out[level].append(s[0]);
            } catch (ArrayIndexOutOfBoundsException AIOOBe) {
               if (DBusConnection.EXCEPTION_DEBUG) AIOOBe.printStackTrace();
               throw new DBusException("Map must have 2 parameters");
            }
            out[level].append('}');
         }
         else if (List.class.isAssignableFrom((Class) p.getRawType())) {
            for (Type t: p.getActualTypeArguments()) {
               if (Type.class.equals(t)) 
                  out[level].append('g');
               else {
                  String[] s = recursiveGetDBusType(t, false, level+1);
                  if (s.length != 1) throw new DBusException("Multi-valued array types not permitted");
                  out[level].append('a');
                  out[level].append(s[0]);
               }
            }
         } 
         else if (p.getRawType().equals(Variant.class)) {
            out[level].append('v');
         }
         else if (DBusInterface.class.isAssignableFrom((Class) p.getRawType())) {
            out[level].append('o');
         }
         else
            throw new DBusException("Exporting non-exportable parameterized type "+c);
      }
      
      else if (c.equals(Byte.class)) out[level].append('y');
      else if (c.equals(Byte.TYPE)) out[level].append('y');
      else if (c.equals(Boolean.class)) out[level].append('b');
      else if (c.equals(Boolean.TYPE)) out[level].append('b');
      else if (c.equals(Short.class)) out[level].append('n');
      else if (c.equals(Short.TYPE)) out[level].append('n');
      else if (c.equals(UInt16.class)) out[level].append('q');
      else if (c.equals(Integer.class)) out[level].append('i');
      else if (c.equals(Integer.TYPE)) out[level].append('i');
      else if (c.equals(UInt32.class)) out[level].append('u');
      else if (c.equals(Long.class)) out[level].append('x');
      else if (c.equals(Long.TYPE)) out[level].append('x');
      else if (c.equals(UInt64.class)) out[level].append('t');
      else if (c.equals(Double.class)) out[level].append('d');
      else if (c.equals(Double.TYPE)) out[level].append('d');
      else if (c.equals(String.class)) out[level].append('s');
      else if (c.equals(Variant.class)) out[level].append('v');
      else if (c instanceof Class && 
            DBusInterface.class.isAssignableFrom((Class) c)) out[level].append('o');
      else if (c instanceof Class && 
            Path.class.equals((Class) c)) out[level].append('o');
      else if (c instanceof Class && ((Class) c).isArray()) {
         if (Type.class.equals(((Class) c).getComponentType()))
            out[level].append('g');
         else {
            out[level].append('a');
            String[] s = recursiveGetDBusType(((Class) c).getComponentType(), false, level+1);
            if (s.length != 1) throw new DBusException("Multi-valued array types not permitted");
            out[level].append(s[0]);
         }
      } else if (c instanceof Class && 
            Struct.class.isAssignableFrom((Class) c)) {
         out[level].append('(');
         Type[] ts = Container.getTypeCache(c);
         if (null == ts) {
            Field[] fs = ((Class) c).getDeclaredFields();
            ts = new Type[fs.length];
            for (Field f : fs) {
               Position p = f.getAnnotation(Position.class);
               if (null == p) continue;
               ts[p.value()] = f.getGenericType();
           }
            Container.putTypeCache(c, ts);
         }

         for (Type t: ts)
            if (t != null)
               for (String s: recursiveGetDBusType(t, false, level+1))
                  out[level].append(s);
         out[level].append(')');
      } else if ((c instanceof Class && 
            DBusSerializable.class.isAssignableFrom((Class) c)) ||
            (c instanceof ParameterizedType &&
             DBusSerializable.class.isAssignableFrom((Class) ((ParameterizedType) c).getRawType()))) {
         // it's a custom serializable type
         Type[] newtypes = null;
         if (c instanceof Class)  {
            for (Method m: ((Class) c).getDeclaredMethods()) 
               if (m.getName().equals("deserialize")) 
                  newtypes = m.getGenericParameterTypes();
         }
         else 
            for (Method m: ((Class) ((ParameterizedType) c).getRawType()).getDeclaredMethods()) 
               if (m.getName().equals("deserialize")) 
                  newtypes = m.getGenericParameterTypes();

         if (null == newtypes) throw new DBusException("Serializable classes must implement a deserialize method");

         String[] sigs = new String[newtypes.length];
         for (int j = 0; j < sigs.length; j++) {
            String[] ss = recursiveGetDBusType(newtypes[j], false, level+1);
            if (1 != ss.length) throw new DBusException("Serializable classes must serialize to native DBus types");
            sigs[j] = ss[0];
         }
         return sigs;
      } else {
         throw new DBusException("Exporting non-exportable type "+c);
      }

      return new String[] { out[level].toString() };
   }

   /**
    * Converts a dbus type string into Java Type objects, 
    * @param dbus The DBus type or types.
    * @param rv Vector to return the types in.
    * @param limit Maximum number of types to parse (-1 == nolimit).
    * @return number of characters parsed from the type string.
    */
   public static int getJavaType(String dbus, List<Type> rv, int limit) throws DBusException
   {
      if (null == dbus || "".equals(dbus) || 0 == limit) return 0;

      try {
         int i = 0;
         for (; i < dbus.length() && (-1 == limit || limit > rv.size()); i++) 
            switch(dbus.charAt(i)) {
               case '(':
                  int j = i+1;
                  for (int c = 1; c > 0; j++) {
                     if (')' == dbus.charAt(j)) c--;
                     else if ('(' == dbus.charAt(j)) c++;
                  }

                  Vector<Type> contained = new Vector<Type>();
                  int c = getJavaType(dbus.substring(i+1, j-1), contained, -1);
                  rv.add(new DBusStructType(contained.toArray(new Type[0])));
                  i = j;
                  break;                     
               case 'a':
                  if ('{' == dbus.charAt(i+1)) {
                     contained = new Vector<Type>();
                     c = getJavaType(dbus.substring(i+2), contained, 2);
                     rv.add(new DBusMapType(contained.get(0), contained.get(1)));
                     i += (c+2);
                  } else {
                     contained = new Vector<Type>();
                     c = getJavaType(dbus.substring(i+1), contained, 1);
                     rv.add(new DBusListType(contained.get(0)));
                     i += c;
                  }
                  break;
               case 'v':
                  rv.add(Variant.class);
                  break;
               case 'b':
                  rv.add(Boolean.class);
                  break;
               case 'n':
                  rv.add(Short.class);
                  break;
               case 'y':
                  rv.add(Byte.class);
                  break;
               case 'o':
                  rv.add(DBusInterface.class);
                  break;
               case 'q':
                  rv.add(UInt16.class);
                  break;
               case 'i':
                  rv.add(Integer.class);
                  break;
               case 'u':
                  rv.add(UInt32.class);
                  break;
               case 'x':
                  rv.add(Long.class);
                  break;
               case 't':
                  rv.add(UInt64.class);
                  break;
               case 'd':
                  rv.add(Double.class);
                  break;
               case 's':
                  rv.add(String.class);
                  break;
               case 'g':
                  rv.add(Type[].class);
                  break;
               default:
                  throw new DBusException("Failed to parse DBus type signature: "+dbus+" ("+dbus.charAt(i)+")");
            }
         return i;
      } catch (IndexOutOfBoundsException IOOBe) {
         if (DBusConnection.EXCEPTION_DEBUG) IOOBe.printStackTrace();
         throw new DBusException("Failed to parse DBus type signature: "+dbus);
      }
   }
   /*
    * Recursively converts types for serialization onto DBus.
    * @param parameters The parameters to convert.
    * @param types The (possibly generic) types of the parameters.
    * @return The converted parameters.
    * @throws DBusException Thrown if there is an error in converting the objects.
    *
   @SuppressWarnings("unchecked")
   public static Object[] convertParameters(Object[] parameters, Type[] types) throws DBusException
   {
      if (null == parameters) return null;
      for (int i = 0; i < parameters.length; i++) {
         if (null == parameters[i]) continue;

         if (types[i] instanceof Class &&
               DBusSerializable.class.isAssignableFrom((Class) types[i])) {
            for (Method m: ((Class) types[i]).getDeclaredMethods()) 
               if (m.getName().equals("deserialize")) {
                  Type[] newtypes = m.getGenericParameterTypes();
                  Type[] expand = new Type[types.length + newtypes.length - 1];
                  System.arraycopy(types, 0, expand, 0, i); 
                  System.arraycopy(newtypes, 0, expand, i, newtypes.length); 
                  System.arraycopy(types, i+1, expand, i+newtypes.length, types.length-i-1); 
                  types = expand;
               }
         } else 
            parameters[i] = convertParameter(parameters[i], types[i]);
        
      }
      return parameters;
   }
   @SuppressWarnings("unchecked")
   static Object convertParameter(Object parameter, Type type) throws DBusException
   {
      // its an unwrapped variant, wrap it
      if (type instanceof TypeVariable &&
            !(parameter instanceof Variant)) {
         parameter = new Variant<Object>(parameter);
      }

      // recurse on Variants
      else if (parameter instanceof Variant)
      {
         parameter = new Variant(convertParameter(((Variant) parameter).getValue(),
                                                   ((Variant) parameter).getType()),
                                 ((Variant) parameter).getType());
      }

      // wrap TypeSignatures
      else if (parameter instanceof Type[])
         parameter = new TypeSignature((Type[]) parameter);

      // its something parameterised
      else if (type instanceof ParameterizedType) {
         ParameterizedType p = (ParameterizedType) type;
         Class r = (Class) p.getRawType();

         // its a list, wrap it in our typed container class
         if (List.class.isAssignableFrom(r) && !(parameter instanceof ListContainer)) {
            parameter = new ListContainer((List<Object>) parameter, p);
         }
         // its a map, wrap it in our typed container class
         else if (Map.class.isAssignableFrom(r) && !(parameter instanceof MapContainer)) {
            parameter = new MapContainer((Map<Object,Object>) parameter, p);
         }
         // its a struct or tuple, recurse over it
         else if (Container.class.isAssignableFrom(r)) {
            Constructor con = r.getDeclaredConstructors()[0];
            Object[] newparams;
            if (Tuple.class.isAssignableFrom(r)) {
               Type[] ts = p.getActualTypeArguments();
               newparams = ((Container) parameter).getParameters(ts);
            } else
               newparams = ((Container) parameter).getParameters();
            try {
               parameter = con.newInstance(newparams);
            } catch (Exception e) {
               if (DBusConnection.EXCEPTION_DEBUG) e.printStackTrace();
               throw new DBusException("Failure in serializing parameters: "+e.getMessage());
            }
         }
      }

      else if (type instanceof GenericArrayType) {
         if (Array.getLength(parameter) > DBusConnection.MAX_ARRAY_LENGTH) throw new DBusException("Array exceeds maximum length of "+DBusConnection.MAX_ARRAY_LENGTH);
         Type t = ((GenericArrayType) type).getGenericComponentType();
         if (!(t instanceof Class) || !((Class) t).isPrimitive())
            parameter = new ListContainer((Object[]) parameter, t);
      } else if (type instanceof Class && ((Class) type).isArray()) {
         if (Array.getLength(parameter) > DBusConnection.MAX_ARRAY_LENGTH) throw new DBusException("Array exceeds maximum length of "+DBusConnection.MAX_ARRAY_LENGTH);
         if (!((Class) type).getComponentType().isPrimitive())
            parameter = new ListContainer((Object[]) parameter, ((Class) type).getComponentType());
      }
      return parameter;
   }*/
   @SuppressWarnings("unchecked")
   static Object deSerializeParameter(Object parameter, Type type) throws Exception
   {
      if (null == parameter) 
         return null;

      // it's a TypeSignature, turn it into a Type[]
      if (parameter instanceof TypeSignature) {
         Vector<Type> ts = new Vector<Type>();
         getJavaType(((TypeSignature) parameter).sig, ts, -1);
         parameter = ts.toArray(new Type[0]);
      }
      // its a wrapped variant, unwrap it
      if (type instanceof TypeVariable 
            && parameter instanceof Variant) {
         parameter = ((Variant)parameter).getValue();
      }

      // recurse on these
      if (parameter instanceof Variant) {
         parameter = new Variant(deSerializeParameter(((Variant) parameter).getValue(),
                                                      ((Variant) parameter).getType()),
                                 ((Variant) parameter).getType());
      }

      // its a wrapped map, unwrap it
      if (parameter instanceof MapContainer)
         parameter = ((MapContainer) parameter).getMap(type);

      // its a wrapped list, unwrap it
      if (parameter instanceof ListContainer) {
         parameter = ((ListContainer) parameter).getList(type);
      }

      // its an object path, get/create the proxy
      if (parameter instanceof ObjectPath) {
         if (type instanceof Class && Path.class.equals((Class) type))
            parameter = new Path(((ObjectPath) parameter).path);
         else
            parameter = ((ObjectPath) parameter).conn.getExportedObject(
                  ((ObjectPath) parameter).source,
                  ((ObjectPath) parameter).path);
      }
      
      // it should be a struct. create it
      if (parameter instanceof Object[] && 
            type instanceof Class &&
            Struct.class.isAssignableFrom((Class) type)) {
         Type[] ts = Container.getTypeCache(type);
         if (null == ts) {
            Field[] fs = ((Class) type).getDeclaredFields();
            ts = new Type[fs.length];
            for (Field f : fs) {
               Position p = f.getAnnotation(Position.class);
               if (null == p) continue;
               ts[p.value()] = f.getGenericType();
           }
            Container.putTypeCache(type, ts);
         }

         // recurse over struct contents
         parameter = deSerializeParameters((Object[]) parameter, ts);
         for (Constructor con: ((Class) type).getDeclaredConstructors()) {
            try {
               parameter = con.newInstance((Object[]) parameter);
               break;
            } catch (IllegalArgumentException IAe) {}
         }
      }

      // recurse over arrays
      if (parameter instanceof Object[]) {
         Type[] ts = new Type[((Object[]) parameter).length];
         Arrays.fill(ts, parameter.getClass().getComponentType());
         parameter = deSerializeParameters((Object[]) parameter,
               ts);
      }

      // make sure arrays are in the correct format
      if (parameter instanceof Object[] ||
            parameter instanceof List ||
            parameter.getClass().isArray()) {
         if (type instanceof ParameterizedType)
            parameter = ArrayFrob.convert(parameter,
                  (Class<? extends Object>) ((ParameterizedType) type).getRawType());
         else if (type instanceof GenericArrayType) {
            Type ct = ((GenericArrayType) type).getGenericComponentType();
            Class cc = null;
            if (ct instanceof Class)
               cc = (Class) ct;
            if (ct instanceof ParameterizedType)
               cc = (Class) ((ParameterizedType) ct).getRawType();
            Object o = Array.newInstance(cc, 0);
            parameter = ArrayFrob.convert(parameter,
                  o.getClass());
         } else if (type instanceof Class &&
               ((Class) type).isArray()) {
            Class cc = ((Class) type).getComponentType();
            Object o = Array.newInstance(cc, 0);
            parameter = ArrayFrob.convert(parameter,
                  o.getClass());
         }
      }
      return parameter;
   }
   static Object[] deSerializeParameters(Object[] parameters, Type[] types) throws Exception
   {
      if (null == parameters) return null;
      for (int i = 0; i < parameters.length; i++) {
      if (null == parameters[i]) continue;

      if (types[i] instanceof Class &&
            DBusSerializable.class.isAssignableFrom((Class) types[i])) {
         for (Method m: ((Class) types[i]).getDeclaredMethods()) 
            if (m.getName().equals("deserialize")) {
               Type[] newtypes = m.getGenericParameterTypes();
               try {
                  Object[] sub = new Object[newtypes.length];
                  System.arraycopy(parameters, i, sub, 0, newtypes.length); 
                  sub = deSerializeParameters(sub, newtypes);
                  DBusSerializable sz = (DBusSerializable) ((Class) types[i]).newInstance();
                  m.invoke(sz, sub);
                  Object[] compress = new Object[parameters.length - newtypes.length + 1];
                  System.arraycopy(parameters, 0, compress, 0, i);
                  compress[i] = sz;
                  System.arraycopy(parameters, i + newtypes.length, compress, i+1, parameters.length - i - newtypes.length);
                  parameters = compress;
               } catch (ArrayIndexOutOfBoundsException AIOOBe) {
                  if (DBusConnection.EXCEPTION_DEBUG) AIOOBe.printStackTrace();
                  throw new DBusException("Not enough elements to create custom object from serialized data ("+(parameters.length-i)+" < "+(newtypes.length)+")");
               }
            }
      } else
         parameters[i] = deSerializeParameter(parameters[i], types[i]);
      }
      return parameters;
   }
}

