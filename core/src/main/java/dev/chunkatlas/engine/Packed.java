package dev.chunkatlas.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

final class Packed {
   private static final byte[] LZ4_MAGIC = "LZ4Block".getBytes(StandardCharsets.US_ASCII);

   private Packed() {
   }

   static byte[] inflate(byte[] in, int off, int len, int cap) throws IOException {
      Inflater inf = new Inflater();

      try {
         inf.setInput(in, off, len);
         byte[] out = new byte[Math.min(cap, Math.max(4096, len * 6))];
         int n = 0;

         while (!inf.finished()) {
            if (n == out.length) {
               if (n >= cap) {
                  throw new IOException("unpacks to more than " + (cap >> 20) + " MB");
               }

               out = Arrays.copyOf(out, (int)Math.min(cap, out.length * 2L));
            }

            int got = inf.inflate(out, n, out.length - n);
            if (got == 0 && (inf.needsInput() || inf.needsDictionary())) {
               throw new IOException("packed data ends early");
            }

            n += got;
         }

         return n == out.length ? out : Arrays.copyOf(out, n);
      } catch (DataFormatException e) {
         throw new IOException("packed data is damaged");
      } finally {
         inf.end();
      }
   }

   static byte[] gunzip(InputStream raw, int cap) throws IOException {
      try (GZIPInputStream in = new GZIPInputStream(raw)) {
         ByteArrayOutputStream out = new ByteArrayOutputStream(8192);
         byte[] buf = new byte[8192];

         int n;
         while ((n = in.read(buf)) > 0) {
            if (out.size() + n > cap) {
               throw new IOException("unpacks to more than " + (cap >> 20) + " MB");
            }

            out.write(buf, 0, n);
         }

         return out.toByteArray();
      } catch (ZipException e) {
         throw new IOException("packed data is damaged");
      }
   }

   static byte[] lz4(byte[] in, int off, int len, int cap) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(4096, len * 3));
      int p = off;
      int end = off + len;

      while (p < end) {
         if (end - p < 21) {
            throw new IOException("packed data ends early");
         }

         for (int i = 0; i < 8; i++) {
            if (in[p + i] != LZ4_MAGIC[i]) {
               throw new IOException("packed data is damaged");
            }
         }

         int method = in[p + 8] & 240;
         int packed = le(in, p + 9);
         int plain = le(in, p + 13);
         p += 21;
         if (packed < 0 || plain < 0 || packed > end - p || (long)out.size() + plain > cap) {
            throw new IOException("packed data is damaged or too large");
         }

         if (plain == 0) {
            if (packed != 0) {
               throw new IOException("packed data is damaged");
            }
            break;
         }

         if (method == 16) {
            if (packed != plain) {
               throw new IOException("packed data is damaged");
            }

            out.write(in, p, plain);
         } else {
            if (method != 32) {
               throw new IOException("packed data is damaged");
            }

            byte[] block = new byte[plain];
            lz4Block(in, p, packed, block);
            out.write(block, 0, plain);
         }

         p += packed;
      }

      return out.toByteArray();
   }

   private static int le(byte[] b, int p) {
      return b[p] & 0xFF | (b[p + 1] & 0xFF) << 8 | (b[p + 2] & 0xFF) << 16 | (b[p + 3] & 0xFF) << 24;
   }

   private static void lz4Block(byte[] in, int off, int len, byte[] out) throws IOException {
      int p = off;
      int end = off + len;
      int o = 0;

      try {
         while (p < end) {
            int token = in[p++] & 255;
            int lit = token >>> 4;
            int b;
            if (lit == 15) {
               do {
                  b = in[p++] & 255;
                  lit += b;
               } while (b == 255);
            }

            if (lit <= end - p && lit <= out.length - o) {
               System.arraycopy(in, p, out, o, lit);
               p += lit;
               o += lit;
               if (p < end) {
                  b = in[p] & 255 | (in[p + 1] & 255) << 8;
                  p += 2;
                  int match = token & 15;
                  int bx;
                  if (match == 15) {
                     do {
                        bx = in[p++] & 255;
                        match += bx;
                     } while (bx == 255);
                  }

                  match += 4;
                  if (b != 0 && b <= o && match <= out.length - o) {
                     for (int i = 0; i < match; o++) {
                        out[o] = out[o - b];
                        i++;
                     }
                     continue;
                  }

                  throw new IOException("packed data is damaged");
               }
               break;
            }

            throw new IOException("packed data is damaged");
         }
      } catch (IndexOutOfBoundsException e) {
         throw new IOException("packed data is damaged");
      }

      if (o != out.length) {
         throw new IOException("packed data is damaged");
      }
   }
}
