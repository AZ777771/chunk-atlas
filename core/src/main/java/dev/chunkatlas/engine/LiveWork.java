package dev.chunkatlas.engine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LiveWork {
   public interface World {
      boolean busy(String dimension, int cx, int cz);

      boolean ready(ServerEdit edit);

      boolean apply(ServerEdit edit, int spot);
   }

   public record Step(int made, List<Long> finished) {
   }

   private static final class Item {
      final ServerEdit edit;
      final BitSet done;

      Item(ServerEdit edit) {
         this.edit = edit;
         this.done = (BitSet)edit.done().clone();
      }

      int left() {
         return this.edit.spots().length - this.done.cardinality();
      }
   }

   private record Key(String dimension, long spot) {
   }

   private final List<Item> items = new ArrayList<>();

   private final Map<Key, ArrayDeque<Long>> waiting = new HashMap<>();
   private int item;
   private int spot;
   private int left;

   private long sinceMade;

   public synchronized void add(ServerEdit edit) {
      Item it = new Item(edit);
      this.items.add(it);
      long[] spots = edit.spots();
      for (int i = 0; i < spots.length; i++) {
         if (!it.done.get(i)) {
            this.waiting.computeIfAbsent(new Key(edit.dimension(), spots[i]), k -> new ArrayDeque<>()).addLast(edit.id());
            this.left++;
         }
      }

      this.sinceMade = 0;
   }

   public synchronized void remove(long id) {
      for (int k = 0; k < this.items.size(); k++) {
         Item it = this.items.get(k);
         if (it.edit.id() != id) {
            continue;
         }

         long[] spots = it.edit.spots();
         for (int i = 0; i < spots.length; i++) {
            if (!it.done.get(i)) {
               this.release(new Key(it.edit.dimension(), spots[i]), id);
               this.left--;
            }
         }

         this.drop(k);
         return;
      }
   }

   public synchronized void clear() {
      this.items.clear();
      this.waiting.clear();
      this.item = 0;
      this.spot = 0;
      this.left = 0;
      this.sinceMade = 0;
   }

   public synchronized int left() {
      return this.left;
   }

   public synchronized boolean isEmpty() {
      return this.items.isEmpty();
   }

   public synchronized boolean idle() {
      return this.sinceMade >= this.left;
   }

   public synchronized List<ServerEdit> edits() {
      List<ServerEdit> out = new ArrayList<>(this.items.size());
      for (Item it : this.items) {
         out.add(it.edit.withDone(it.done));
      }

      return out;
   }

   public synchronized Step tick(int most, int look, LiveWork.World world) {
      int made = 0;
      int looked = 0;
      List<Long> finished = new ArrayList<>();
      int budget = Math.min(look, this.left);
      while (made < most && looked < budget && !this.items.isEmpty()) {
         if (this.item >= this.items.size()) {
            this.item = 0;
            this.spot = 0;
         }

         Item it = this.items.get(this.item);
         long[] spots = it.edit.spots();
         this.spot = this.spot < spots.length ? it.done.nextClearBit(this.spot) : this.spot;
         if (this.spot >= spots.length) {
            this.item++;
            this.spot = 0;
            continue;
         }

         if (!world.ready(it.edit)) {
            looked += it.left();
            this.sinceMade += it.left();
            this.item++;
            this.spot = 0;
            continue;
         }

         looked++;
         this.sinceMade++;
         int i = this.spot++;
         Key key = new Key(it.edit.dimension(), spots[i]);
         ArrayDeque<Long> queue = this.waiting.get(key);
         if (queue != null && queue.peekFirst() != it.edit.id()) {
            continue;
         }

         if (world.busy(it.edit.dimension(), ServerEdit.spotX(spots[i]), ServerEdit.spotZ(spots[i])) || !world.apply(it.edit, i)) {
            continue;
         }

         it.done.set(i);
         this.release(key, it.edit.id());
         this.left--;
         this.sinceMade = 0;
         made++;
      }

      for (int k = this.items.size() - 1; k >= 0; k--) {
         if (this.items.get(k).left() == 0) {
            finished.add(0, this.items.get(k).edit.id());
            this.drop(k);
         }
      }

      return new Step(made, finished);
   }

   private void release(Key key, long id) {
      ArrayDeque<Long> queue = this.waiting.get(key);
      if (queue != null) {
         queue.removeFirstOccurrence(id);
         if (queue.isEmpty()) {
            this.waiting.remove(key);
         }
      }
   }

   private void drop(int k) {
      this.items.remove(k);
      if (this.item > k) {
         this.item--;
      } else if (this.item == k) {
         this.spot = 0;
      }
   }
}
