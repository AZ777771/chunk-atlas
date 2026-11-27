package dev.chunkatlas.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Nbt {
   static final int MAX_DEPTH = 64;
   private static final int END = 0;
   private static final int BYTE = 1;
   private static final int SHORT = 2;
   private static final int INT = 3;
   private static final int LONG = 4;
   private static final int FLOAT = 5;
   private static final int DOUBLE = 6;
   private static final int BYTES = 7;
   private static final int STRING = 8;
   private static final int LIST = 9;
   private static final int COMPOUND = 10;
   private static final int INTS = 11;
   private static final int LONGS = 12;

   private Nbt() {
   }

   public static Map<String, Object> read(byte[] data, Set<String> skip, Set<String> countOnly) throws IOException {
      try {
         Nbt.In in = new Nbt.In(data, skip, countOnly);
         int type = in.u8();
         if (type != 10) {
            throw new IOException("not a save-data document");
         }

         in.skipBytes(in.u16());
         return in.compound(0);
      } catch (IndexOutOfBoundsException | NegativeArraySizeException e) {
         throw new IOException("save data ends early");
      }
   }

   public static Map<String, Object> read(byte[] data) throws IOException {
      return read(data, Set.of(), Set.of());
   }

   public static Map<String, Object> map(Object parent, String key) {
      Object v = parent instanceof Map<?, ?> m ? m.get(key) : null;
      return v instanceof Map ? (Map)v : null;
   }

   public static List<Object> list(Object parent, String key) {
      Object v = parent instanceof Map<?, ?> m ? m.get(key) : null;
      return v instanceof List ? (List)v : null;
   }

   public static String string(Object parent, String key) {
      return (parent instanceof Map<?, ?> m ? m.get(key) : null) instanceof String s ? s : null;
   }

   public static long number(Object parent, String key, long fallback) {
      return (parent instanceof Map<?, ?> m ? m.get(key) : null) instanceof Number n ? n.longValue() : fallback;
   }

   public static boolean has(Object parent, String key) {
      return parent instanceof Map<?, ?> m && m.containsKey(key);
   }

   public static int count(Object parent, String key) {
      Object v = parent instanceof Map<?, ?> m ? m.get(key) : null;
      if (v instanceof Nbt.Count c) {
         return c.size();
      } else {
         return v instanceof List<?> l ? l.size() : -1;
      }
   }

   public static byte[] write(Map<String, Object> root) throws IOException {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 * 1024);
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeByte(COMPOUND);
      out.writeUTF("");
      writeCompound(out, root, 0);
      out.flush();
      return bytes.toByteArray();
   }

   private static int kind(Object v) throws IOException {
      if (v instanceof Byte) {
         return BYTE;
      } else if (v instanceof Short) {
         return SHORT;
      } else if (v instanceof Integer) {
         return INT;
      } else if (v instanceof Long) {
         return LONG;
      } else if (v instanceof Float) {
         return FLOAT;
      } else if (v instanceof Double) {
         return DOUBLE;
      } else if (v instanceof byte[]) {
         return BYTES;
      } else if (v instanceof String) {
         return STRING;
      } else if (v instanceof List) {
         return LIST;
      } else if (v instanceof Map) {
         return COMPOUND;
      } else if (v instanceof int[]) {
         return INTS;
      } else if (v instanceof long[]) {
         return LONGS;
      } else {
         throw new IOException("save data holds a value that can't be written (" + (v == null ? "nothing" : v.getClass().getSimpleName()) + ")");
      }
   }

   private static void writeCompound(DataOutputStream out, Map<?, ?> map, int depth) throws IOException {
      if (depth > MAX_DEPTH) {
         throw new IOException("save data is nested too deeply");
      }

      for (Map.Entry<?, ?> e : map.entrySet()) {
         int type = kind(e.getValue());
         out.writeByte(type);
         out.writeUTF(String.valueOf(e.getKey()));
         writeValue(out, type, e.getValue(), depth + 1);
      }

      out.writeByte(END);
   }

   private static void writeValue(DataOutputStream out, int type, Object v, int depth) throws IOException {
      switch (type) {
         case BYTE -> out.writeByte((Byte)v);
         case SHORT -> out.writeShort((Short)v);
         case INT -> out.writeInt((Integer)v);
         case LONG -> out.writeLong((Long)v);
         case FLOAT -> out.writeFloat((Float)v);
         case DOUBLE -> out.writeDouble((Double)v);
         case BYTES -> {
            byte[] a = (byte[])v;
            out.writeInt(a.length);
            out.write(a);
         }
         case STRING -> out.writeUTF((String)v);
         case LIST -> {
            if (depth > MAX_DEPTH) {
               throw new IOException("save data is nested too deeply");
            }

            List<?> list = (List<?>)v;
            int elem = list instanceof Nbt.EmptyList empty && list.isEmpty() ? empty.kind : (list.isEmpty() ? END : kind(list.get(0)));
            for (Object item : list) {
               if (kind(item) != elem) {
                  throw new IOException("save data has a list of mixed kinds");
               }
            }

            out.writeByte(elem);
            out.writeInt(list.size());
            for (Object item : list) {
               writeValue(out, elem, item, depth + 1);
            }
         }
         case COMPOUND -> writeCompound(out, (Map<?, ?>)v, depth);
         case INTS -> {
            int[] a = (int[])v;
            out.writeInt(a.length);
            for (int x : a) {
               out.writeInt(x);
            }
         }
         case LONGS -> {
            long[] a = (long[])v;
            out.writeInt(a.length);
            for (long x : a) {
               out.writeLong(x);
            }
         }
         default -> throw new IOException("save data has an unknown kind of value (" + type + ")");
      }
   }

   static final class EmptyList extends ArrayList<Object> {
      private static final long serialVersionUID = 1L;
      final int kind;

      EmptyList(int kind) {
         super(0);
         this.kind = kind;
      }
   }

   public record Count(int size) {
   }

   private static final class In {
      private final byte[] b;
      private final Set<String> skip;
      private final Set<String> countOnly;
      private int p;

      In(byte[] b, Set<String> skip, Set<String> countOnly) {
         this.b = b;
         this.skip = skip;
         this.countOnly = countOnly;
      }

      int u8() {
         return this.b[this.p++] & 0xFF;
      }

      int u16() {
         int v = (this.b[this.p] & 255) << 8 | this.b[this.p + 1] & 255;
         this.p += 2;
         return v;
      }

      int i32() {
         int v = (this.b[this.p] & 255) << 24 | (this.b[this.p + 1] & 255) << 16 | (this.b[this.p + 2] & 255) << 8 | this.b[this.p + 3] & 255;
         this.p += 4;
         return v;
      }

      long i64() {
         return (long)this.i32() << 32 | this.i32() & 4294967295L;
      }

      void skipBytes(long n) throws IOException {
         if (n >= 0L && n <= this.b.length - this.p) {
            this.p += (int)n;
         } else {
            throw new IOException("save data ends early");
         }
      }

      int length(int unit) throws IOException {
         int n = this.i32();
         if (n >= 0 && (long)n * unit <= this.b.length - this.p) {
            return n;
         } else {
            throw new IOException("save data declares more than it holds");
         }
      }

      String utf() throws IOException {
         int n = this.u16();
         if (n > this.b.length - this.p) {
            throw new IOException("save data ends early");
         }

         int start = this.p;
         this.p += n;

         for (int i = start; i < start + n; i++) {
            if (this.b[i] <= 0) {
               return new DataInputStream(new ByteArrayInputStream(this.b, start - 2, n + 2)).readUTF();
            }
         }

         return new String(this.b, start, n, StandardCharsets.ISO_8859_1);
      }

      Map<String, Object> compound(int depth) throws IOException {
         if (depth > 64) {
            throw new IOException("save data is nested too deeply");
         }

         Map<String, Object> out = new LinkedHashMap<>();

         while (true) {
            int type = this.u8();
            if (type == 0) {
               return out;
            }

            String name = this.utf();
            if (this.skip.contains(name)) {
               this.skip(type, depth + 1);
            } else if (type == 9 && this.countOnly.contains(name)) {
               int elem = this.u8();
               int n = this.i32();
               if (n < 0) {
                  throw new IOException("save data declares more than it holds");
               }

               for (int i = 0; i < n; i++) {
                  this.skip(elem, depth + 1);
               }

               out.put(name, new Nbt.Count(n));
            } else {
               out.put(name, this.value(type, depth + 1));
            }
         }
      }

      Object value(int type, int depth) throws IOException {
         switch (type) {
            case 1:
               return (byte)this.u8();
            case 2:
               return (short)this.u16();
            case 3:
               return this.i32();
            case 4:
               return this.i64();
            case 5:
               return Float.intBitsToFloat(this.i32());
            case 6:
               return Double.longBitsToDouble(this.i64());
            case 7: {
               int n = this.length(1);
               byte[] a = new byte[n];
               System.arraycopy(this.b, this.p, a, 0, n);
               this.p += n;
               return a;
            }
            case 8:
               return this.utf();
            case 9:
               if (depth > 64) {
                  throw new IOException("save data is nested too deeply");
               } else {
                  int elem = this.u8();
                  int nx = this.i32();
                  if (nx >= 0 && (elem == 0 || nx <= this.b.length - this.p)) {
                     if (elem == 0) {
                        return new ArrayList(0);
                     }

                     if (nx == 0) {
                        return new Nbt.EmptyList(elem);
                     }

                     List<Object> out = new ArrayList<>(Math.min(nx, 4096));

                     for (int i = 0; i < nx; i++) {
                        out.add(this.value(elem, depth + 1));
                     }

                     return out;
                  }

                  throw new IOException("save data declares more than it holds");
               }
            case 10:
               return this.compound(depth);
            case 11: {
               int n = this.length(4);
               int[] a = new int[n];

               for (int i = 0; i < n; i++) {
                  a[i] = this.i32();
               }

               return a;
            }
            case 12: {
               int n = this.length(8);
               long[] a = new long[n];

               for (int i = 0; i < n; i++) {
                  a[i] = this.i64();
               }

               return a;
            }
            default:
               throw new IOException("save data has an unknown kind of value (" + type + ")");
         }
      }

      void skip(int type, int depth) throws IOException {
         if (depth > 64) {
            throw new IOException("save data is nested too deeply");
         }

         switch (type) {
            case 1:
               this.skipBytes(1L);
               break;
            case 2:
               this.skipBytes(2L);
               break;
            case 3:
            case 5:
               this.skipBytes(4L);
               break;
            case 4:
            case 6:
               this.skipBytes(8L);
               break;
            case 7:
               this.skipBytes(this.i32());
               break;
            case 8:
               this.skipBytes(this.u16());
               break;
            case 9:
               int elem = this.u8();
               int n = this.i32();
               if (n < 0 || elem != 0 && n > this.b.length - this.p) {
                  throw new IOException("save data declares more than it holds");
               }

               if (elem != 0) {
                  for (int i = 0; i < n; i++) {
                     this.skip(elem, depth + 1);
                  }
               }
               break;
            case 10:
               while (true) {
                  int t = this.u8();
                  if (t == 0) {
                     return;
                  }

                  this.skipBytes(this.u16());
                  this.skip(t, depth + 1);
               }
            case 11:
               this.skipBytes(4L * this.i32());
               break;
            case 12:
               this.skipBytes(8L * this.i32());
               break;
            default:
               throw new IOException("save data has an unknown kind of value (" + type + ")");
         }
      }
   }
}
