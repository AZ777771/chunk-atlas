package dev.chunkatlas.minimap.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.gui.Glass;
import dev.chunkatlas.gui.Ui;
import dev.chunkatlas.minimap.Config;
import dev.chunkatlas.minimap.Minimap;
import dev.chunkatlas.minimap.hud.MinimapHud;
import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Keys;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class PositionScreen extends AtlasScreen {
   private static final int CARD_W = 190;

   private static final int GRAB = 5;
   private final Screen parent;
   private final List<Ui.Hit> hits = new ArrayList<>();
   private boolean dragging;
   private boolean resizing;
   private double grabX;
   private double grabY;

   private int fixedX;
   private int fixedY;
   private int growX;
   private int growY;

   public PositionScreen(Screen parent) {
      super(Component.translatable(Minimap.ID + ".position.title"));
      this.parent = parent;
   }

   @Override
   protected void init() {
      Minimap.HUD.hideForScreen(true);

      Minimap.HUD.enlarged(false);
   }

   @Override
   public void removed() {
      Minimap.HUD.hideForScreen(false);
      Config.get().save();
   }

   @Override
   public void onClose() {
      Game.open(this.minecraft, this.parent);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   private MinimapHud.Layout layout() {
      return Minimap.HUD.layout(this.width, this.height, Config.get());
   }

   @Override
   protected void drawBackground(Gfx g, int mouseX, int mouseY, float partial) {
      g.fill(0, 0, this.width, this.height, 0x40000000);
   }

   private int freeCentre(MinimapHud.Layout at) {
      int left = at.x();
      int right = this.width - at.x() - at.box();
      return left >= right ? left / 2 : at.x() + at.box() + right / 2;
   }

   private int[] handle(MinimapHud.Layout at) {
      Config c = Config.get();
      return new int[]{c.anchorRight ? at.x() : at.x() + at.box(), c.anchorBottom ? at.y() : at.y() + at.box()};
   }

   private boolean overHandle(MinimapHud.Layout at, double x, double y) {
      int[] h = this.handle(at);
      return Math.abs(x - h[0]) <= GRAB && Math.abs(y - h[1]) <= GRAB;
   }

   @Override
   protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
      super.draw(g, mouseX, mouseY, partial);
      this.hits.clear();
      Config c = Config.get();
      MinimapHud.Layout at = this.layout();
      if (this.minecraft.player != null && this.minecraft.level != null) {
         Minimap.HUD.draw(g, this.minecraft, c, at, partial);
      } else {
         g.fill(at.x(), at.y(), at.x() + at.box(), at.y() + at.box(), MinimapHud.PAPER);
      }

      boolean overMap = Glass.over(mouseX, mouseY, at.x(), at.y(), at.box(), at.height());
      int edge = this.dragging ? Glass.ACCENT : overMap ? 0xFFFFFFFF : 0xB0FFFFFF;
      g.outline(at.x() - 1, at.y() - 1, at.box() + 2, at.height() + 2, edge);
      this.drawHandle(g, at, this.resizing || this.overHandle(at, mouseX, mouseY));
      this.drawCard(g, at, mouseX, mouseY);
   }

   private void drawHandle(Gfx g, MinimapHud.Layout at, boolean hot) {
      Config c = Config.get();
      int[] h = this.handle(at);
      int x = h[0] - 4;
      int y = h[1] - 4;
      int colour = hot ? Glass.ACCENT : Ui.WHITE;
      g.fill(x + 1, y + 1, x + 9, y + 9, 0x73000000);
      g.fill(x, y, x + 8, y + 8, Glass.MENU);

      int sx = c.anchorRight ? x + 2 : x + 5;
      int sy = c.anchorBottom ? y + 2 : y + 5;
      g.fill(x + 2, sy, x + 6, sy + 1, colour);
      g.fill(sx, y + 2, sx + 1, y + 6, colour);
   }

   private void drawCard(Gfx g, MinimapHud.Layout at, int mouseX, int mouseY) {
      Config c = Config.get();
      String hint = Ui.t(Minimap.ID + ".position.hint");
      String where = (c.anchorRight ? "→ " : "← ") + c.offsetX + "  " + (c.anchorBottom ? "↓ " : "↑ ") + c.offsetY
         + "  ·  " + Ui.t(Minimap.ID + ".position.size", at.box());
      int w = Math.min(this.width - 8, Math.max(CARD_W, Math.max(this.font.width(hint), this.font.width(where)) + 24));
      int h = 12 + 10 + 8 + 10 + 4 + 10 + 10 + 20 + 10;
      int cx = Math.clamp(this.freeCentre(at), w / 2 + 4, this.width - w / 2 - 4);
      int x = cx - w / 2;
      int y = Math.clamp((this.height - h) / 2, 4, Math.max(4, this.height - h - 4));
      Glass.panel(g, x, y, w, h);
      g.fill(x, y, x + w, y + h, 0x66000000);
      int ty = y + 12;
      g.centered(this.font, Component.literal(this.title.getString()).withStyle(ChatFormatting.BOLD), cx, ty, Ui.WHITE);
      ty += 18;
      g.centered(this.font, this.font.plainSubstrByWidth(hint, w - 12), cx, ty, Glass.LABEL);
      ty += 14;
      g.centered(this.font, this.font.plainSubstrByWidth(where, w - 12), cx, ty, Glass.NOTE);
      ty += 20;
      String reset = Ui.t(Minimap.ID + ".position.reset");
      String done = Ui.t("gui.done");
      int bw = Math.max(70, Math.max(this.font.width(reset), this.font.width(done)) + 24);
      int right = x + w - 12;
      Glass.button(g, this.font, this.hits, right - bw, ty, bw, 20, done, Glass.ACCENT, mouseX, mouseY, this::onClose);
      Glass.button(g, this.font, this.hits, right - 2 * bw - 8, ty, bw, 20, reset, Ui.WHITE, mouseX, mouseY, this::reset);
   }

   @Override
   protected boolean onClick(double mouseX, double mouseY, int mouseButton, boolean doubleClick) {
      if (mouseButton != InputConstants.MOUSE_BUTTON_LEFT) {
         return super.onClick(mouseX, mouseY, mouseButton, doubleClick);
      }

      for (int n = this.hits.size() - 1; n >= 0; n--) {
         if (this.hits.get(n).has(mouseX, mouseY)) {
            this.hits.get(n).action().run();
            return true;
         }
      }

      MinimapHud.Layout at = this.layout();
      if (this.overHandle(at, mouseX, mouseY)) {
         Config c = Config.get();
         this.resizing = true;
         this.growX = c.anchorRight ? -1 : 1;
         this.growY = c.anchorBottom ? -1 : 1;
         this.fixedX = c.anchorRight ? at.x() + at.box() : at.x();
         this.fixedY = c.anchorBottom ? at.y() + at.box() : at.y();
         return true;
      }

      if (Glass.over((int)mouseX, (int)mouseY, at.x(), at.y(), at.box(), at.height())) {
         this.dragging = true;
         this.grabX = mouseX - at.x();
         this.grabY = mouseY - at.y();
         return true;
      }

      return super.onClick(mouseX, mouseY, mouseButton, doubleClick);
   }

   @Override
   protected boolean onDrag(double mouseX, double mouseY, int mouseButton, double dx, double dy) {
      if (this.resizing) {
         this.resize(mouseX, mouseY);
         return true;
      }

      if (this.dragging) {
         this.place((int)Math.round(mouseX - this.grabX), (int)Math.round(mouseY - this.grabY));
         return true;
      }

      return super.onDrag(mouseX, mouseY, mouseButton, dx, dy);
   }

   @Override
   protected boolean onRelease(double mouseX, double mouseY, int mouseButton) {
      if (this.dragging || this.resizing) {
         this.dragging = false;
         this.resizing = false;
         return true;
      }

      return super.onRelease(mouseX, mouseY, mouseButton);
   }

   @Override
   protected boolean onKey(int keyCode, int scanCode, int modifiers) {
      int step = Keys.shift(modifiers) ? 10 : 1;
      MinimapHud.Layout at = this.layout();
      switch (keyCode) {
         case InputConstants.KEY_LEFT -> this.place(at.x() - step, at.y());
         case InputConstants.KEY_RIGHT -> this.place(at.x() + step, at.y());
         case InputConstants.KEY_UP -> this.place(at.x(), at.y() - step);
         case InputConstants.KEY_DOWN -> this.place(at.x(), at.y() + step);
         default -> {
            if (Keys.confirm(keyCode)) {
               this.onClose();
               return true;
            }

            return super.onKey(keyCode, scanCode, modifiers);
         }
      }

      return true;
   }

   private void resize(double x, double y) {
      Config c = Config.get();
      MinimapHud.Layout at = this.layout();
      int wanted = (int)Math.round(Math.max(this.growX * (x - this.fixedX), this.growY * (y - this.fixedY)));
      int size = MinimapHud.customSize(wanted, this.width, this.height, at.infoHeight());
      c.size = Config.Size.CUSTOM;
      c.customSize = size;
      this.place(this.growX < 0 ? this.fixedX - size : this.fixedX, this.growY < 0 ? this.fixedY - size : this.fixedY);
   }

   private void place(int x, int y) {
      Config c = Config.get();
      MinimapHud.Layout at = this.layout();
      x = Math.clamp(x, 0, Math.max(0, this.width - at.box()));
      y = Math.clamp(y, 0, Math.max(0, this.height - at.height()));
      c.anchorRight = x + at.box() / 2 > this.width / 2;
      c.anchorBottom = y + at.height() / 2 > this.height / 2;
      c.offsetX = c.anchorRight ? this.width - x - at.box() : x;
      c.offsetY = c.anchorBottom ? this.height - y - at.height() : y;
   }

   private void reset() {
      Config c = Config.get();
      Config d = new Config();
      c.anchorRight = d.anchorRight;
      c.anchorBottom = d.anchorBottom;
      c.offsetX = d.offsetX;
      c.offsetY = d.offsetY;
   }

   public void tourResize(int size) {
      MinimapHud.Layout at = this.layout();
      Config c = Config.get();
      this.growX = c.anchorRight ? -1 : 1;
      this.growY = c.anchorBottom ? -1 : 1;
      this.fixedX = c.anchorRight ? at.x() + at.box() : at.x();
      this.fixedY = c.anchorBottom ? at.y() + at.box() : at.y();
      this.resize(this.fixedX + this.growX * size, this.fixedY + this.growY * size);
   }
}
