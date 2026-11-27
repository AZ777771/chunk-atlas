package dev.chunkatlas.port;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

public final class Gfx {
   private static final float LAYER = 100.0F;
   private static final float TEXT = 50.0F;
   private static final Map<String, ResourceLocation> IDS = new HashMap<>();
   private static Gfx last;
   final GuiGraphics g;

   private float z;
   private final List<Object[]> tooltips = new ArrayList<>();

   private Gfx(GuiGraphics g) {
      this.g = g;
   }

   public static Gfx of(GuiGraphics g) {
      Gfx have = last;
      if (have == null || have.g != g) {
         have = new Gfx(g);
         last = have;
      }

      return have;
   }

   static ResourceLocation id(String id) {
      ResourceLocation have = IDS.get(id);
      if (have == null) {
         have = ResourceLocation.parse(id);
         IDS.put(id, have);
      }

      return have;
   }

   public int width() {
      return this.g.guiWidth();
   }

   public int height() {
      return this.g.guiHeight();
   }

   private void lift(float dz) {
      this.g.pose().pushPose();
      this.g.pose().translate(0.0F, 0.0F, dz);
   }

   private void drop() {
      this.g.pose().popPose();
   }

   public void fill(int x0, int y0, int x1, int y1, int argb) {
      if (this.z == 0.0F) {
         this.g.fill(x0, y0, x1, y1, argb);
      } else {
         this.lift(this.z);
         this.g.fill(x0, y0, x1, y1, argb);
         this.drop();
      }
   }

   public void gradient(int x0, int y0, int x1, int y1, int top, int bottom) {
      this.lift(this.z);
      this.g.fillGradient(x0, y0, x1, y1, top, bottom);
      this.drop();
   }

   public void outline(int x, int y, int w, int h, int argb) {
      this.lift(this.z);
      this.g.renderOutline(x, y, w, h, argb);
      this.drop();
   }

   private static boolean seen(int argb) {
      return (argb & 0xFC000000) != 0;
   }

   public void text(Font font, String s, int x, int y, int argb) {
      this.text(font, s, x, y, argb, true);
   }

   public void text(Font font, String s, int x, int y, int argb, boolean shadow) {
      if (seen(argb)) {
         this.lift(this.z + TEXT);
         this.g.drawString(font, s, x, y, argb, shadow);
         this.drop();
      }
   }

   public void text(Font font, Component s, int x, int y, int argb) {
      this.text(font, s, x, y, argb, true);
   }

   public void text(Font font, Component s, int x, int y, int argb, boolean shadow) {
      if (seen(argb)) {
         this.lift(this.z + TEXT);
         this.g.drawString(font, s, x, y, argb, shadow);
         this.drop();
      }
   }

   public void text(Font font, FormattedCharSequence s, int x, int y, int argb) {
      this.text(font, s, x, y, argb, true);
   }

   public void text(Font font, FormattedCharSequence s, int x, int y, int argb, boolean shadow) {
      if (seen(argb)) {
         this.lift(this.z + TEXT);
         this.g.drawString(font, s, x, y, argb, shadow);
         this.drop();
      }
   }

   public void centered(Font font, String s, int x, int y, int argb) {
      this.text(font, s, x - font.width(s) / 2, y, argb, true);
   }

   public void centered(Font font, Component s, int x, int y, int argb) {
      this.text(font, s.getVisualOrderText(), x - font.width(s) / 2, y, argb, true);
   }

   public void centered(Font font, FormattedCharSequence s, int x, int y, int argb) {
      this.text(font, s, x - font.width(s) / 2, y, argb, true);
   }

   private void tinted(int argb, Runnable draw) {
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      if (argb != -1) {
         this.g.setColor((argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F, (argb >>> 24) / 255.0F);
      }

      this.lift(this.z);
      draw.run();
      this.drop();
      if (argb != -1) {
         this.g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
      }

      RenderSystem.disableBlend();
   }

   public void quads(Tex t, float[] xyuv, int quads, int argb) {
      if (quads <= 0) {
         return;
      }

      this.g.flush();
      RenderSystem.setShaderTexture(0, t.id());
      RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      org.joml.Matrix4f pose = this.g.pose().last().pose();
      com.mojang.blaze3d.vertex.BufferBuilder b = com.mojang.blaze3d.vertex.Tesselator.getInstance()
         .begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
      for (int i = 0; i < quads * 16; i += 4) {
         b.addVertex(pose, xyuv[i], xyuv[i + 1], this.z).setUv(xyuv[i + 2], xyuv[i + 3]).setColor(argb);
      }

      com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(b.buildOrThrow());
      RenderSystem.disableBlend();
   }

   private void blitId(ResourceLocation id, int x, int y, float u, float v, int w, int h, int regionW, int regionH, int texW, int texH, int argb) {
      this.tinted(argb, () -> this.g.blit(id, x, y, w, h, u, v, regionW, regionH, texW, texH));
   }

   public void blit(Tex t, int x, int y, float u, float v, int w, int h, int texW, int texH) {
      this.blitId(t.id(), x, y, u, v, w, h, w, h, texW, texH, -1);
   }

   public void blit(Tex t, int x, int y, float u, float v, int w, int h, int texW, int texH, int argb) {
      this.blitId(t.id(), x, y, u, v, w, h, w, h, texW, texH, argb);
   }

   public void blit(Tex t, int x, int y, float u, float v, int w, int h, int regionW, int regionH, int texW, int texH) {
      this.blitId(t.id(), x, y, u, v, w, h, regionW, regionH, texW, texH, -1);
   }

   public void blit(Tex t, int x, int y, float u, float v, int w, int h, int regionW, int regionH, int texW, int texH, int argb) {
      this.blitId(t.id(), x, y, u, v, w, h, regionW, regionH, texW, texH, argb);
   }

   public void blit(String texture, int x, int y, float u, float v, int w, int h, int texW, int texH) {
      this.blitId(id(texture), x, y, u, v, w, h, w, h, texW, texH, -1);
   }

   public void blit(String texture, int x, int y, float u, float v, int w, int h, int texW, int texH, int argb) {
      this.blitId(id(texture), x, y, u, v, w, h, w, h, texW, texH, argb);
   }

   public void blit(String texture, int x, int y, float u, float v, int w, int h, int regionW, int regionH, int texW, int texH) {
      this.blitId(id(texture), x, y, u, v, w, h, regionW, regionH, texW, texH, -1);
   }

   public void blit(String texture, int x, int y, float u, float v, int w, int h, int regionW, int regionH, int texW, int texH, int argb) {
      this.blitId(id(texture), x, y, u, v, w, h, regionW, regionH, texW, texH, argb);
   }

   public void sprite(String sprite, int x, int y, int w, int h) {
      this.sprite(sprite, x, y, w, h, -1);
   }

   public void sprite(String sprite, int x, int y, int w, int h, int argb) {
      ResourceLocation id = id(sprite);
      this.tinted(argb, () -> this.g.blitSprite(id, x, y, w, h));
   }

   public void blockSprite(String name, int x, int y, int w, int h) {
      TextureAtlasSprite s = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.withDefaultNamespace("block/" + name));
      this.tinted(-1, () -> this.g.blit(x, y, 0, w, h, s));
   }

   public void item(ItemStack stack, int x, int y) {
      this.lift(this.z);
      this.g.renderItem(stack, x, y);
      this.drop();
   }

   public void face(Skin skin, int x, int y, int size) {
      this.tinted(-1, () -> PlayerFaceRenderer.draw(this.g, skin.skin, x, y, size));
   }

   public void face(Skin skin, int x, int y, int size, int argb) {
      this.tinted(argb, () -> PlayerFaceRenderer.draw(this.g, skin.skin, x, y, size));
   }

   public void widget(Renderable widget, int mouseX, int mouseY, float partial) {
      this.lift(this.z);
      widget.render(this.g, mouseX, mouseY, partial);
      this.drop();
   }

   public void scissor(int x0, int y0, int x1, int y1) {
      this.g.enableScissor(x0, y0, x1, y1);
   }

   public void unscissor() {
      this.g.disableScissor();
   }

   public void push() {
      this.g.pose().pushPose();
   }

   public void pop() {
      this.g.pose().popPose();
   }

   public void translate(float x, float y) {
      this.g.pose().translate(x, y, 0.0F);
   }

   public void scale(float x, float y) {
      this.g.pose().scale(x, y, 1.0F);
   }

   public void rotate(float radians) {
      this.g.pose().mulPose(Axis.ZP.rotation(radians));
   }

   public void identity() {
      this.g.pose().setIdentity();
   }

   public void layer() {
      this.g.flush();
      this.z += LAYER;
   }

   float z() {
      return this.z;
   }

   public void tooltip(Font font, Component line, int mouseX, int mouseY) {
      this.tooltips.add(new Object[]{font, List.of(line), mouseX, mouseY});
   }

   public void tooltip(Font font, List<Component> lines, int mouseX, int mouseY) {
      this.tooltips.add(new Object[]{font, List.copyOf(lines), mouseX, mouseY});
   }

   @SuppressWarnings("unchecked")
   public void finish() {
      if (!this.tooltips.isEmpty()) {
         this.g.flush();
         this.lift(this.z + LAYER);
         for (Object[] t : this.tooltips) {
            this.g.renderComponentTooltip((Font)t[0], (List<Component>)t[1], (Integer)t[2], (Integer)t[3]);
         }

         this.drop();
         this.tooltips.clear();
      }

      this.z = 0.0F;
   }
}
