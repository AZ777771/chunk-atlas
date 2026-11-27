package dev.chunkatlas.port;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

public final class Tex implements AutoCloseable {
   private static final AtomicInteger COUNT = new AtomicInteger();
   private final NativeImage image;
   private DynamicTexture texture;
   private ResourceLocation id;
   private boolean closed;

   private Tex(NativeImage image) {
      this.image = image;
   }

   public static Tex create(String label, int w, int h) {
      return new Tex(new NativeImage(w, h, true));
   }

   public static boolean exists(String texture) {
      ResourceLocation id = ResourceLocation.tryParse(texture);
      return id != null && Minecraft.getInstance().getResourceManager().getResource(id).isPresent();
   }

   public int width() {
      return this.image.getWidth();
   }

   public int height() {
      return this.image.getHeight();
   }

   public void set(int x, int y, int argb) {
      this.image.setPixelRGBA(x, y, argb & 0xFF00FF00 | (argb >> 16 & 0xFF) | (argb & 0xFF) << 16);
   }

   public void putAbgr(int[] abgr) {
      int w = this.image.getWidth();
      int n = Math.min(abgr.length, w * this.image.getHeight());
      for (int i = 0; i < n; i++) {
         this.image.setPixelRGBA(i % w, i / w, abgr[i]);
      }
   }

   public void upload() {
      if (this.closed) {
         return;
      }

      if (this.texture == null) {
         this.texture = new DynamicTexture(this.image);
         this.id = ResourceLocation.fromNamespaceAndPath("chunkatlas", "tex/" + COUNT.incrementAndGet());
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

      this.texture.bind();
      this.image.upload(0, x, y, x, y, w, h, false, false, false, false);
   }

   ResourceLocation id() {
      if (this.texture == null) {
         this.upload();
      }

      return this.id != null ? this.id : MissingTextureAtlasSprite.getLocation();
   }

   public void save(Path file) throws IOException {
      this.image.writeToFile(file);
   }

   @Override
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
