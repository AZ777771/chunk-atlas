package dev.chunkatlas.port;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class Tex implements AutoCloseable {
   private static final AtomicInteger COUNT = new AtomicInteger();
   private final String label;
   private final NativeImage image;
   private DynamicTexture texture;
   private Identifier id;
   private boolean closed;

   private Tex(String label, NativeImage image) {
      this.label = label;
      this.image = image;
   }

   public static Tex create(String label, int w, int h) {
      return new Tex(label, new NativeImage(w, h, true));
   }

   public static boolean exists(String texture) {
      Identifier id = Identifier.tryParse(texture);
      return id != null && Minecraft.getInstance().getResourceManager().getResource(id).isPresent();
   }

   public int width() {
      return this.image.getWidth();
   }

   public int height() {
      return this.image.getHeight();
   }

   public void set(int x, int y, int argb) {
      this.image.setPixel(x, y, argb);
   }

   public void putAbgr(int[] abgr) {
      this.image.getPixelBytes().order(ByteOrder.nativeOrder()).asIntBuffer().put(abgr);
   }

   public void upload() {
      if (this.closed) {
         return;
      }

      if (this.texture == null) {
         this.texture = new DynamicTexture(() -> this.label, this.image);
         this.id = Identifier.fromNamespaceAndPath("chunkatlas", "tex/" + COUNT.incrementAndGet());
         Minecraft.getInstance().getTextureManager().register(this.id, this.texture);
      } else {
         this.texture.upload();
      }
   }

   public void upload(int x, int y, int w, int h) {
      if (this.closed || w <= 0 || h <= 0) {
         return;
      }

      if (this.texture == null) {
         this.upload();
         return;
      }

      int width = this.image.getWidth();
      int bytes = w * h * 4;
      if (this.staging == null || this.staging.capacity() < bytes) {
         this.staging = ByteBuffer.allocateDirect(Math.max(bytes, 64 * 64 * 4)).order(ByteOrder.nativeOrder());
      }

      ByteBuffer from = this.image.getPixelBytes();
      ByteBuffer to = this.staging;
      to.clear();
      for (int row = 0; row < h; row++) {
         int at = ((y + row) * width + x) * 4;
         to.put(to.position(), from, at, w * 4);
         to.position(to.position() + w * 4);
      }

      to.flip();
      RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.texture.getTexture(), to, 0, 0, x, y, w, h);
   }

   private ByteBuffer staging;

   GpuTextureView view() {
      if (this.texture == null) {
         this.upload();
      }

      return this.texture.getTextureView();
   }

   Identifier id() {
      if (this.texture == null) {
         this.upload();
      }

      return this.id != null ? this.id : net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation();
   }

   public void save(Path file) throws IOException {
      this.image.writeToFile(file);
   }

   public void close() {
      if (this.closed) {
         return;
      }

      this.closed = true;
      if (this.texture != null) {
         Minecraft.getInstance().getTextureManager().release(this.id);
      } else {
         this.image.close();
      }
   }

   public boolean closed() {
      return this.closed;
   }
}
