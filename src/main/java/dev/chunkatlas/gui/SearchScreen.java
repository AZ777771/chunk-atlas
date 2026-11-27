package dev.chunkatlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.minimap.waypoint.Waypoint;
import dev.chunkatlas.minimap.waypoint.Waypoints;
import dev.chunkatlas.port.AtlasScreen;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Gfx;
import dev.chunkatlas.port.Keys;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class SearchScreen extends AtlasScreen {
   private static final Pattern COORDS = Pattern.compile("^\\s*(-?\\d{1,8})[\\s,]+(?:-?\\d{1,4}[\\s,]+)?(-?\\d{1,8})\\s*$");
   private static final int W = 260;
   private static final int MAX = 8;
   private static final int ROW = 16;

   private record Hit(String label, String detail, int x, int z) {
   }

   private final Screen parent;
   private final String dimension;
   private final BiConsumer<Integer, Integer> go;
   private final List<Hit> hits = new ArrayList<>();
   private final List<Ui.Hit> clicks = new ArrayList<>();
   private EditBox box;

   SearchScreen(Screen parent, String dimension, BiConsumer<Integer, Integer> go) {
      super(Component.translatable("chunkatlas.search.title"));
      this.parent = parent;
      this.dimension = dimension;
      this.go = go;
   }

   private int left() {
      return (this.width - W) / 2;
   }

   private int top() {
      return Math.max(Glass.MARGIN + 20, this.height / 4 - 20);
   }

   @Override
   protected void init() {
      this.box = new EditBox(this.font, this.left() + 6, this.top() + 20, W - 12, 18, Component.translatable("chunkatlas.search.hint"));
      this.box.setHint(Component.translatable("chunkatlas.search.hint"));
      this.box.setBordered(true);
      this.box.setResponder(s -> this.refresh());
      this.addRenderableWidget(this.box);
      this.setInitialFocus(this.box);
      this.refresh();
   }

   private void refresh() {
      this.hits.clear();
      String text = this.box.getValue().trim();
      Matcher m = COORDS.matcher(text);
      if (m.matches()) {
         int x = Integer.parseInt(m.group(1));
         int z = Integer.parseInt(m.group(2));
         this.hits.add(new Hit(Component.translatable("chunkatlas.search.go_to", x, z).getString(), "", x, z));
      }

      String find = text.toLowerCase(Locale.ROOT);
      if (!find.isEmpty()) {
         for (Waypoint w : Waypoints.all()) {
            double[] p = w.placeIn(this.dimension);
            if (p != null && w.name.toLowerCase(Locale.ROOT).contains(find) && this.hits.size() < MAX) {
               this.hits.add(new Hit(w.name, w.x + " " + w.z, (int)Math.floor(p[0]), (int)Math.floor(p[2])));
            }
         }
      }
   }

   void tourType(String text) {
      this.box.setValue(text);
   }

   private void pick(int i) {
      if (i < this.hits.size()) {
         Hit h = this.hits.get(i);
         Game.open(Minecraft.getInstance(), this.parent);
         this.go.accept(h.x(), h.z());
      }
   }

   @Override
   protected boolean onKey(int keyCode, int scanCode, int modifiers) {
      if (Keys.confirm(keyCode)) {
         this.pick(0);
         return true;
      }

      return super.onKey(keyCode, scanCode, modifiers);
   }

   @Override
   protected boolean onClick(double mouseX, double mouseY, int mouseButton, boolean doubleClick) {
      if (mouseButton == InputConstants.MOUSE_BUTTON_LEFT) {
         for (Ui.Hit h : this.clicks) {
            if (h.has(mouseX, mouseY)) {
               h.action().run();
               return true;
            }
         }
      }

      return super.onClick(mouseX, mouseY, mouseButton, doubleClick);
   }

   @Override
   public void onClose() {
      Game.open(Minecraft.getInstance(), this.parent);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   protected void drawBackground(Gfx g, int mouseX, int mouseY, float partial) {
      if (!(this.parent instanceof MapScreen)) {
         super.drawBackground(g, mouseX, mouseY, partial);
      }
   }

   @Override
   protected void draw(Gfx g, int mouseX, int mouseY, float partial) {
      if (this.parent instanceof MapScreen map) {
         map.drawUnder(g, partial);
         g.layer();
      }

      this.clicks.clear();
      int x = this.left();
      int y = this.top();
      boolean none = this.hits.isEmpty() && !this.box.getValue().isBlank();
      int rows = none ? 1 : this.hits.size();
      Glass.panel(g, x, y, W, 44 + rows * ROW + (rows > 0 ? 4 : 0));
      g.text(this.font, this.title, x + 6, y + 6, Ui.WHITE);
      int ry = y + 44;
      if (none) {
         g.text(this.font, Component.translatable("chunkatlas.search.none"), x + 8, ry + 4, Glass.NOTE);
      }

      for (int i = 0; i < this.hits.size(); i++) {
         Hit h = this.hits.get(i);
         int n = i;
         int top = ry + i * ROW;
         if (Glass.over(mouseX, mouseY, x + 2, top, W - 4, ROW) || i == 0 && !Glass.over(mouseX, mouseY, x, ry, W, this.hits.size() * ROW)) {
            g.fill(x + 2, top, x + W - 2, top + ROW, Glass.TINT);
         }

         g.text(this.font, this.font.plainSubstrByWidth(h.label(), W - 80), x + 8, top + 4, Ui.WHITE);
         if (!h.detail().isEmpty()) {
            g.text(this.font, h.detail(), x + W - 8 - this.font.width(h.detail()), top + 4, Glass.QUIET);
         }

         this.clicks.add(new Ui.Hit(x + 2, top, x + W - 2, top + ROW, () -> this.pick(n)));
      }

      super.draw(g, mouseX, mouseY, partial);
   }
}
