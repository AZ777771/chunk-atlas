package dev.chunkatlas.engine;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RegionFacts {
   public static final byte ABSENT = 0;
   public static final byte FULL = 1;
   public static final byte PARTIAL = 2;
   public static final byte UNREADABLE = 3;
   public static final byte AWAY = 4;
   public final byte[] state = new byte[1024];
   public final int[] dataVersion = new int[1024];
   public final long[] inhabited = new long[1024];
   public final int[] savedAt = new int[1024];
   public final short[] sections = new short[1024];
   public final short[] blockEntities = new short[1024];
   public final short[] entities = new short[1024];

   public final short[] kb = new short[1024];
   private final byte[] status = new byte[1024];
   private final List<String> statuses = new ArrayList<>();

   public RegionFacts() {
      Arrays.fill(this.entities, (short)-1);
   }

   public RegionFacts.ChunkInfo chunk(int index) {
      return new RegionFacts.ChunkInfo(
         this.state[index],
         this.dataVersion[index],
         this.inhabited[index],
         this.savedAt[index] & 4294967295L,
         this.sections[index],
         this.blockEntities[index],
         this.entities[index],
         this.status(index)
      );
   }

   public String status(int index) {
      int s = this.status[index] & 255;
      return s < this.statuses.size() ? this.statuses.get(s) : "";
   }

   public int present() {
      int n = 0;

      for (byte b : this.state) {
         if (b != 0) {
            n++;
         }
      }

      return n;
   }

   void set(int index, ChunkDecoder.Facts f) {
      this.state[index] = (byte)(f.fullyGenerated() ? 1 : 2);
      this.dataVersion[index] = f.dataVersion();
      this.inhabited[index] = f.inhabitedTicks();
      this.sections[index] = (short)Math.min(f.sections(), 32767);
      this.blockEntities[index] = (short)Math.min(f.blockEntities(), 32767);
      if (f.entities() >= 0) {
         this.entities[index] = (short)Math.min(f.entities(), 32767);
      }

      int s = this.statuses.indexOf(f.status());
      if (s < 0 && this.statuses.size() < 255) {
         this.statuses.add(f.status());
         s = this.statuses.size() - 1;
      }

      this.status[index] = (byte)Math.max(s, 0);
   }

   void write(DataOutputStream out) throws IOException {
      out.writeByte(this.statuses.size());

      for (String s : this.statuses) {
         out.writeUTF(s);
      }

      for (int i = 0; i < 1024; i++) {
         out.writeByte(this.state[i]);
         out.writeByte(this.status[i]);
         out.writeInt(this.dataVersion[i]);
         out.writeLong(this.inhabited[i]);
         out.writeInt(this.savedAt[i]);
         out.writeShort(this.sections[i]);
         out.writeShort(this.blockEntities[i]);
         out.writeShort(this.entities[i]);
         out.writeShort(this.kb[i]);
      }
   }

   static RegionFacts read(DataInputStream in) throws IOException {
      RegionFacts f = new RegionFacts();
      int n = in.readUnsignedByte();

      for (int i = 0; i < n; i++) {
         f.statuses.add(in.readUTF());
      }

      boolean sized = in.available() >= 1024 * 26;
      for (int i = 0; i < 1024; i++) {
         f.state[i] = in.readByte();
         f.status[i] = in.readByte();
         f.dataVersion[i] = in.readInt();
         f.inhabited[i] = in.readLong();
         f.savedAt[i] = in.readInt();
         f.sections[i] = in.readShort();
         f.blockEntities[i] = in.readShort();
         f.entities[i] = in.readShort();
         f.kb[i] = sized ? in.readShort() : 0;
      }

      return f;
   }

   public record ChunkInfo(byte state, int dataVersion, long inhabitedTicks, long savedAtSeconds, int sections, int blockEntities, int entities, String status) {
   }
}
