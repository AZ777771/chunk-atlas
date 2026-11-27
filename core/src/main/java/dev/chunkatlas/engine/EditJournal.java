package dev.chunkatlas.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public final class EditJournal<T> {
   private static final int MAGIC = 0x43414A4C;
   private static final int VERSION = 1;

   public interface Codec<T> {
      void write(DataOutputStream out, T item) throws IOException;

      T read(DataInputStream in) throws IOException;
   }

   public interface Store {
      byte[] load() throws IOException;

      void save(byte[] data) throws IOException;

      void delete() throws IOException;
   }

   public interface Maker<T> {
      boolean make(T item);
   }

   private final Store store;
   private final Codec<T> codec;
   private List<T> items = List.of();

   public EditJournal(Store store, Codec<T> codec) {
      this.store = store;
      this.codec = codec;
   }

   public static Store file(Path f) {
      return new Store() {
         @Override
         public byte[] load() throws IOException {
            return Files.isRegularFile(f) ? Files.readAllBytes(f) : null;
         }

         @Override
         public void save(byte[] data) throws IOException {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.write(tmp, data);
            try {
               Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
               Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
         }

         @Override
         public void delete() throws IOException {
            Files.deleteIfExists(f);
         }
      };
   }

   public synchronized void load() throws IOException {
      this.items = List.of();
      byte[] data = this.store.load();
      if (data == null) {
         return;
      }

      DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
      if (in.readInt() != MAGIC || in.readInt() != VERSION) {
         throw new IOException("not an edit list");
      }

      int n = in.readInt();
      if (n < 0) {
         throw new IOException("bad length");
      }

      List<T> out = new ArrayList<>();
      for (int i = 0; i < n; i++) {
         int len = in.readInt();
         if (len < 0 || len > in.available()) {
            throw new IOException("cut short");
         }

         byte[] item = new byte[len];
         in.readFully(item);
         out.add(this.codec.read(new DataInputStream(new ByteArrayInputStream(item))));
      }

      this.items = List.copyOf(out);
   }

   public synchronized List<T> items() {
      return this.items;
   }

   public synchronized int count(Predicate<T> which) {
      int n = 0;
      for (T t : this.items) {
         if (which.test(t)) {
            n++;
         }
      }

      return n;
   }

   public void add(T item) throws IOException {
      this.update(list -> {
         list.add(item);
         return list;
      });
   }

   public synchronized List<T> remove(Predicate<T> which) throws IOException {
      List<T> gone = new ArrayList<>();
      this.update(list -> {
         list.removeIf(t -> {
            if (which.test(t)) {
               gone.add(t);
               return true;
            }

            return false;
         });
         return list;
      });
      return gone;
   }

   public synchronized void update(UnaryOperator<List<T>> change) throws IOException {
      List<T> next = List.copyOf(change.apply(new ArrayList<>(this.items)));
      if (next.equals(this.items)) {
         return;
      }

      this.persist(next);
      this.items = next;
   }

   private void persist(List<T> list) throws IOException {
      if (list.isEmpty()) {
         this.store.delete();
         return;
      }

      ByteArrayOutputStream bytes = new ByteArrayOutputStream(4096);
      DataOutputStream out = new DataOutputStream(bytes);
      out.writeInt(MAGIC);
      out.writeInt(VERSION);
      out.writeInt(list.size());
      for (T t : list) {
         ByteArrayOutputStream one = new ByteArrayOutputStream(256);
         this.codec.write(new DataOutputStream(one), t);
         out.writeInt(one.size());
         one.writeTo(out);
      }

      out.flush();
      this.store.save(bytes.toByteArray());
   }

   public int drain(Predicate<T> which, Maker<T> maker) {
      int done = 0;
      for (T t : this.items()) {
         if (!which.test(t)) {
            continue;
         }

         if (!maker.make(t)) {
            return done;
         }

         try {
            this.update(list -> {
               list.remove(t);
               return list;
            });
         } catch (IOException e) {
            return done;
         }

         done++;
      }

      return done;
   }
}
