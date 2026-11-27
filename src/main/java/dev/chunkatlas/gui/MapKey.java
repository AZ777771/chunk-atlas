package dev.chunkatlas.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.chunkatlas.Settings;
import dev.chunkatlas.port.Game;
import dev.chunkatlas.port.Keys;
import net.minecraft.network.chat.Component;

enum MapKey {
   EDIT_MODE("edit_mode", true, InputConstants.KEY_E, false),
   GO_TO("go_to", true, InputConstants.KEY_G, false),
   HIDE_CONTROLS("hide_controls", true, InputConstants.KEY_F1, false),
   SEARCH("search", true, InputConstants.KEY_F, true),
   FIND("find", false, InputConstants.KEY_F, false),
   SELECT_ALL("select_all", false, InputConstants.KEY_A, true),
   INVERT("invert", false, InputConstants.KEY_I, true),
   COPY("copy", false, InputConstants.KEY_C, true),
   PASTE("paste", false, InputConstants.KEY_V, true),
   UNDO("undo", false, InputConstants.KEY_Z, true),
   DELETE("delete", false, InputConstants.KEY_DELETE, false),
   TOOL_HAND("tool_hand", false, InputConstants.KEY_H, false),
   TOOL_SELECT("tool_select", false, InputConstants.KEY_S, false),
   TOOL_BOX("tool_box", false, InputConstants.KEY_X, false),
   TOOL_BRUSH("tool_brush", false, InputConstants.KEY_B, false),
   TOOL_REGION("tool_region", false, InputConstants.KEY_R, false),
   BRUSH_SMALLER("brush_smaller", false, InputConstants.KEY_LBRACKET, false),
   BRUSH_BIGGER("brush_bigger", false, InputConstants.KEY_RBRACKET, false);

   final String id;

   final boolean map;
   private final int defaultKey;
   private final boolean defaultCtrl;

   MapKey(String id, boolean map, int key, boolean ctrl) {
      this.id = id;
      this.map = map;
      this.defaultKey = key;
      this.defaultCtrl = ctrl;
   }

   static final Combo NONE = new Combo(InputConstants.UNKNOWN.getValue(), false);

   record Combo(int key, boolean ctrl) {
      boolean isSet() {
         return this.key != InputConstants.UNKNOWN.getValue();
      }

      String save() {
         return (this.ctrl ? "ctrl+" : "") + this.key;
      }

      static Combo read(String text) {
         if (text == null) {
            return null;
         }

         boolean ctrl = text.startsWith("ctrl+");
         try {
            return new Combo(Integer.parseInt(ctrl ? text.substring(5) : text), ctrl);
         } catch (NumberFormatException e) {
            return null;
         }
      }
   }

   Combo combo() {
      Combo saved = Combo.read(Settings.key(this.id));
      return saved != null ? saved : this.defaultCombo();
   }

   Combo defaultCombo() {
      return new Combo(this.defaultKey, this.defaultCtrl);
   }

   boolean isDefault() {
      return this.combo().equals(this.defaultCombo());
   }

   boolean isSet() {
      return this.combo().isSet();
   }

   void set(Combo combo) {
      Settings.key(this.id, combo == null || combo.equals(this.defaultCombo()) ? null : combo.save());
   }

   boolean matches(int key, int mods) {
      Combo c = this.combo();
      if (!c.isSet()) {
         return false;
      }

      boolean ctrl = Keys.ctrl(mods) || Keys.shortcut(mods);
      if (ctrl != c.ctrl()) {
         return false;
      }

      return key == c.key() || this == DELETE && c.equals(this.defaultCombo()) && key == InputConstants.KEY_BACKSPACE;
   }

   Component label() {
      return Component.translatable("chunkatlas.key." + this.id);
   }

   String keyName() {
      return keyName(this.combo());
   }

   static String keyName(Combo c) {
      if (!c.isSet()) {
         return "";
      }

      String key = Keys.name(c.key());
      return c.ctrl() ? Ui.t(Game.mac() ? "chunkatlas.key.cmd" : "chunkatlas.key.ctrl", key) : key;
   }
}
