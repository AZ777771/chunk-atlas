package dev.chunkatlas.port;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.HashMap;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

public final class Gfx {
   private static final Map<String, Identifier> IDS = new HashMap<>();
   private static final Map<String, SpriteId> BLOCK_SPRITES = new HashMap<>();
   private static Gfx last;
   final GuiGraphicsExtractor g;

   private Gfx(GuiGraphicsExtractor g) {
      this.g = g;
   }

   public static Gfx of(GuiGraphicsExtractor g) {
      Gfx have = last;
      if (have == null || have.g != g) {
         have = new Gfx(g);
         last = have;
      }

      return have;
   }

   static Identifier id(String id) {
      Identifier have = IDS.get(id);
      if (have == null) {
         have = Identifier.parse(id);
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

   public void fill(int x0, int y0, int x1, int y1, int argb) {
      this.g.fill(x0, y0, x1, y1, argb);
   }

   public void gradient(int x0, int y0, int x1, int y1, int top, int bottom) {
      this.g.fillGradient(x0, y0, x1, y1, top, bottom);
   }

   public void outline(int x, int y, int w, int h, int argb) {
      this.g.outline(x, y, w, h, argb);
   }

   public void text(Font font, String s, int x, int y, int argb) {
      this.g.text(font, s, x, y, argb);
   }

   public void text(Font font, String s, int x, int y, int argb, boolean shadow) {
      this.g.text(font, s, x, y, argb, shadow);
   }

   public void text(Font font, Component s, int x, int y, int argb) {
      this.g.text(font, s, x, y, argb);
   }

   public void text(Font font, Component s, int x, int y, int argb, boolean shadow) {
      this.g.text(font, s, x, y, argb, shadow);
   }

   public void text(Font font, FormattedCharSequence s, int x, int y, int argb) {
      this.g.text(font, s, x, y, argb);
   }

   public void text(Font font, FormattedCharSequence s, int x, int y, int argb, boolean shadow) {
      this.g.text(font, s, x, y, argb, shadow);
   }

   public void centered(Font font, String s, int x, int y, int argb) {
      this.g.centeredText(font, s, x, y, argb);
   }

   public void centered(Font font, Component s, int x, int y, int argb) {
      this.g.centeredText(font, s, x, y, argb);
   }

   public void centered(Font font, FormattedCharSequence s, int x, int y, int argb) {
      this.g.centeredText(font, s, x, y, argb);
   }

   public void blit(Tex t, int x, int y, float u, float v, int w, int h, int texW, int texH) {
      this.g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x, y, u, v, w, h, texW, texH);
   }

   public void blit(Tex t, int x, int y, float u, float v, int w, int h, int texW, int texH, int argb) {
      this.g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x, y, u, v, w, h, texW, texH, argb);
   }

   public void blit(Tex t, int x, int y, float u, float v, int w, int h, int regionW, int regionH, int texW, int texH) {
      this.g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x, y, u, v, w, h, regionW, regionH, texW, texH);
   }

   public void blit(Tex t, int x, int y, float u, float v, int w, int h, int regionW, int regionH, int texW, int texH, int argb) {
      this.g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x, y, u, v, w, h, regionW, regionH, texW, texH, argb);
   }

   public void blit(String texture, int x, int y, float u, float v, int w, int h, int texW, int texH) {
      this.g.blit(RenderPipelines.GUI_TEXTURED, id(texture), x, y, u, v, w, h, texW, texH);
   }

   public void blit(String texture, int x, int y, float u, float v, int w, int h, int texW, int texH, int argb) {
      this.g.blit(RenderPipelines.GUI_TEXTURED, id(texture), x, y, u, v, w, h, texW, texH, argb);
   }

   public void blit(String texture, int x, int y, float u, float v, int w, int h, int regionW, int regionH, int texW, int texH) {
      this.g.blit(RenderPipelines.GUI_TEXTURED, id(texture), x, y, u, v, w, h, regionW, regionH, texW, texH);
   }

   public void blit(String texture, int x, int y, float u, float v, int w, int h, int regionW, int regionH, int texW, int texH, int argb) {
      this.g.blit(RenderPipelines.GUI_TEXTURED, id(texture), x, y, u, v, w, h, regionW, regionH, texW, texH, argb);
   }

   public void quads(Tex t, float[] xyuv, int quads, int argb) {
      if (quads <= 0) {
         return;
      }

      float minX = Float.MAX_VALUE;
      float minY = Float.MAX_VALUE;
      float maxX = -Float.MAX_VALUE;
      float maxY = -Float.MAX_VALUE;
      for (int i = 0; i < quads * 16; i += 4) {
         minX = Math.min(minX, xyuv[i]);
         maxX = Math.max(maxX, xyuv[i]);
         minY = Math.min(minY, xyuv[i + 1]);
         maxY = Math.max(maxY, xyuv[i + 1]);
      }

      Matrix3x2f pose = new Matrix3x2f(this.g.pose());
      ScreenRectangle scissor = this.g.scissorStack.peek();
      int x0 = (int)Math.floor(minX);
      int y0 = (int)Math.floor(minY);
      ScreenRectangle bounds = new ScreenRectangle(x0, y0, (int)Math.ceil(maxX) - x0, (int)Math.ceil(maxY) - y0).transformMaxBounds(pose);
      if (scissor != null) {
         bounds = scissor.intersection(bounds);
         if (bounds == null) {
            return;
         }
      }

      TextureSetup setup = TextureSetup.singleTexture(t.view(), RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST));
      this.g.guiRenderState.addGuiElement(new Quads(RenderPipelines.GUI_TEXTURED, setup, pose, java.util.Arrays.copyOf(xyuv, quads * 16), quads, argb, scissor, bounds));
   }

   private record Quads(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, float[] xyuv, int quads, int argb, ScreenRectangle scissorArea,
      ScreenRectangle bounds) implements GuiElementRenderState {
      @Override
      public void buildVertices(VertexConsumer out) {
         float[] v = this.xyuv;
         for (int i = 0; i < this.quads * 16; i += 4) {
            out.addVertexWith2DPose(this.pose, v[i], v[i + 1]).setUv(v[i + 2], v[i + 3]).setColor(this.argb);
         }
      }
   }

   public void sprite(String sprite, int x, int y, int w, int h) {
      this.g.blitSprite(RenderPipelines.GUI_TEXTURED, id(sprite), x, y, w, h);
   }

   public void sprite(String sprite, int x, int y, int w, int h, int argb) {
      this.g.blitSprite(RenderPipelines.GUI_TEXTURED, id(sprite), x, y, w, h, argb);
   }

   public void blockSprite(String name, int x, int y, int w, int h) {
      SpriteId sprite = BLOCK_SPRITES.computeIfAbsent(name, Sheets.BLOCKS_MAPPER::defaultNamespaceApply);
      this.g.blitSprite(RenderPipelines.GUI_TEXTURED, this.g.getSprite(sprite), x, y, w, h);
   }

   public void item(ItemStack stack, int x, int y) {
      this.g.item(stack, x, y);
   }

   public void face(Skin skin, int x, int y, int size) {
      PlayerFaceExtractor.extractRenderState(this.g, skin.skin, x, y, size);
   }

   public void face(Skin skin, int x, int y, int size, int argb) {
      PlayerFaceExtractor.extractRenderState(this.g, skin.skin, x, y, size, argb);
   }

   public void widget(Renderable widget, int mouseX, int mouseY, float partial) {
      widget.extractRenderState(this.g, mouseX, mouseY, partial);
   }

   public void scissor(int x0, int y0, int x1, int y1) {
      this.g.enableScissor(x0, y0, x1, y1);
   }

   public void unscissor() {
      this.g.disableScissor();
   }

   public void push() {
      this.g.pose().pushMatrix();
   }

   public void pop() {
      this.g.pose().popMatrix();
   }

   public void translate(float x, float y) {
      this.g.pose().translate(x, y);
   }

   public void scale(float x, float y) {
      this.g.pose().scale(x, y);
   }

   public void rotate(float radians) {
      this.g.pose().rotate(radians);
   }

   public void identity() {
      this.g.pose().identity();
   }

   public void layer() {
      this.g.nextStratum();
   }

   public void tooltip(Font font, Component line, int mouseX, int mouseY) {
      this.g.setTooltipForNextFrame(font, line, mouseX, mouseY);
   }

   public void tooltip(Font font, List<Component> lines, int mouseX, int mouseY) {
      this.g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
   }

   public void finish() {
   }
}
