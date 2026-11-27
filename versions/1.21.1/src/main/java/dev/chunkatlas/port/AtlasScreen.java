package dev.chunkatlas.port;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class AtlasScreen extends Screen {
   private static final long DOUBLE_MS = 250L;
   private int mods;
   private boolean rendering;
   private long lastClick;
   private int lastButton = -1;

   protected AtlasScreen(Component title) {
      super(title);
   }

   protected final int mods() {
      return this.mods;
   }

   @Override
   public final void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
      Gfx gfx = Gfx.of(g);
      this.drawBackground(gfx, mouseX, mouseY, partial);
      gfx.layer();
      this.rendering = true;
      try {
         this.draw(gfx, mouseX, mouseY, partial);
      } finally {
         this.rendering = false;
      }

      gfx.finish();
   }

   protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
      g.g.pose().pushPose();
      g.g.pose().translate(0.0F, 0.0F, g.z());
      super.render(g.g, mouseX, mouseY, partial);
      g.g.pose().popPose();
   }

   @Override
   public final void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
      if (!this.rendering) {
         this.drawBackground(Gfx.of(g), mouseX, mouseY, partial);
      }
   }

   protected void drawBackground(Gfx g, int mouseX, int mouseY, float partial) {
      super.renderBackground(g.g, mouseX, mouseY, partial);
   }

   @Override
   public final boolean mouseClicked(double x, double y, int button) {
      long now = Util.getMillis();
      boolean doubleClick = button == this.lastButton && now - this.lastClick < DOUBLE_MS;
      this.lastClick = now;
      this.lastButton = button;
      int was = this.mods;
      this.mods = Keys.mods();
      try {
         return this.onClick(x, y, button, doubleClick);
      } finally {
         this.mods = was;
      }
   }

   protected boolean onClick(double x, double y, int button, boolean doubleClick) {
      return super.mouseClicked(x, y, button);
   }

   @Override
   public final boolean mouseReleased(double x, double y, int button) {
      int was = this.mods;
      this.mods = Keys.mods();
      try {
         return this.onRelease(x, y, button);
      } finally {
         this.mods = was;
      }
   }

   protected boolean onRelease(double x, double y, int button) {
      return super.mouseReleased(x, y, button);
   }

   @Override
   public final boolean mouseDragged(double x, double y, int button, double dx, double dy) {
      int was = this.mods;
      this.mods = Keys.mods();
      try {
         return this.onDrag(x, y, button, dx, dy);
      } finally {
         this.mods = was;
      }
   }

   protected boolean onDrag(double x, double y, int button, double dx, double dy) {
      return super.mouseDragged(x, y, button, dx, dy);
   }

   @Override
   public final boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      return this.onScroll(x, y, scrollX, scrollY);
   }

   protected boolean onScroll(double x, double y, double scrollX, double scrollY) {
      return super.mouseScrolled(x, y, scrollX, scrollY);
   }

   @Override
   public final boolean keyPressed(int key, int scan, int mods) {
      int was = this.mods;
      this.mods = mods;
      try {
         return this.onKey(key, scan, mods);
      } finally {
         this.mods = was;
      }
   }

   protected boolean onKey(int key, int scan, int mods) {
      return super.keyPressed(key, scan, mods);
   }

   @Override
   public final boolean keyReleased(int key, int scan, int mods) {
      int was = this.mods;
      this.mods = mods;
      try {
         return this.onKeyRelease(key, scan, mods);
      } finally {
         this.mods = was;
      }
   }

   protected boolean onKeyRelease(int key, int scan, int mods) {
      return super.keyReleased(key, scan, mods);
   }

   @Override
   public final boolean charTyped(char c, int mods) {
      return this.onChar(c, mods);
   }

   protected boolean onChar(int codepoint, int mods) {
      return super.charTyped((char)codepoint, mods);
   }
}
