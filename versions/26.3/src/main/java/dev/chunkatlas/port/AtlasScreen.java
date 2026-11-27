package dev.chunkatlas.port;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

public abstract class AtlasScreen extends Screen {
   private int mods;

   protected AtlasScreen(Component title) {
      super(title);
   }

   protected final int mods() {
      return this.mods;
   }

   @Override
   public final void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
      Gfx gfx = Gfx.of(g);
      this.draw(gfx, mouseX, mouseY, partial);
      gfx.finish();
   }

   protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
      super.extractRenderState(g.g, mouseX, mouseY, partial);
   }

   @Override
   public final void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
      this.drawBackground(Gfx.of(g), mouseX, mouseY, partial);
   }

   protected void drawBackground(Gfx g, int mouseX, int mouseY, float partial) {
      super.extractBackground(g.g, mouseX, mouseY, partial);
   }

   @Override
   public final boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      int was = this.mods;
      this.mods = event.modifiers();
      try {
         return this.onClick(event.x(), event.y(), event.button(), doubleClick);
      } finally {
         this.mods = was;
      }
   }

   protected boolean onClick(double x, double y, int button, boolean doubleClick) {
      return super.mouseClicked(new MouseButtonEvent(x, y, new MouseButtonInfo(button, this.mods)), doubleClick);
   }

   @Override
   public final boolean mouseReleased(MouseButtonEvent event) {
      int was = this.mods;
      this.mods = event.modifiers();
      try {
         return this.onRelease(event.x(), event.y(), event.button());
      } finally {
         this.mods = was;
      }
   }

   protected boolean onRelease(double x, double y, int button) {
      return super.mouseReleased(new MouseButtonEvent(x, y, new MouseButtonInfo(button, this.mods)));
   }

   @Override
   public final boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      int was = this.mods;
      this.mods = event.modifiers();
      try {
         return this.onDrag(event.x(), event.y(), event.button(), dx, dy);
      } finally {
         this.mods = was;
      }
   }

   protected boolean onDrag(double x, double y, int button, double dx, double dy) {
      return super.mouseDragged(new MouseButtonEvent(x, y, new MouseButtonInfo(button, this.mods)), dx, dy);
   }

   @Override
   public final boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
      return this.onScroll(x, y, scrollX, scrollY);
   }

   protected boolean onScroll(double x, double y, double scrollX, double scrollY) {
      return super.mouseScrolled(x, y, scrollX, scrollY);
   }

   @Override
   public final boolean keyPressed(KeyEvent event) {
      int was = this.mods;
      this.mods = event.modifiers();
      try {
         return this.onKey(event.key(), event.keycode(), event.modifiers());
      } finally {
         this.mods = was;
      }
   }

   protected boolean onKey(int key, int scan, int mods) {
      return super.keyPressed(new KeyEvent(key, scan, mods));
   }

   @Override
   public final boolean keyReleased(KeyEvent event) {
      int was = this.mods;
      this.mods = event.modifiers();
      try {
         return this.onKeyRelease(event.key(), event.keycode(), event.modifiers());
      } finally {
         this.mods = was;
      }
   }

   protected boolean onKeyRelease(int key, int scan, int mods) {
      return super.keyReleased(new KeyEvent(key, scan, mods));
   }

   @Override
   public final boolean charTyped(CharacterEvent event) {
      return this.onChar(event.codepoint(), 0);
   }

   protected boolean onChar(int codepoint, int mods) {
      return super.charTyped(new CharacterEvent(codepoint));
   }
}
