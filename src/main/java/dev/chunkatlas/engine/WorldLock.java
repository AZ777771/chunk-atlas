package dev.chunkatlas.engine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.util.DirectoryLock;

public final class WorldLock {
   private WorldLock() {
   }

   public static <T> Optional<T> with(Path world, Function<BooleanSupplier, T> work) {
      DirectoryLock lock;
      try {
         lock = DirectoryLock.create(world);
      } catch (IOException | RuntimeException e) {
         return Optional.empty();
      }

      try {
         return Optional.ofNullable(work.apply(lock::isValid));
      } finally {
         try {
            lock.close();
         } catch (IOException | RuntimeException ignored) {
         }
      }
   }
}
