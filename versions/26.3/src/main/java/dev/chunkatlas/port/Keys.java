package dev.chunkatlas.port;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.resources.Identifier;

public final class Keys {
   public static final int LEFT_SUPER = InputConstants.KEY_LGUI;
   public static final int RIGHT_SUPER = InputConstants.KEY_RGUI;

   public static final int PAD_PLUS = InputConstants.KEY_ADD;
   public static final int PAD_MINUS = InputConstants.KEY_ADD - 1;
   private static KeyMapping.Category category;

   private Keys() {
   }

   public static boolean down(int key) {
      return InputConstants.isKeyDown(key);
   }

   public static boolean shift() {
      return down(InputConstants.KEY_LSHIFT) || down(InputConstants.KEY_RSHIFT);
   }

   public static boolean ctrl() {
      return down(InputConstants.KEY_LCONTROL) || down(InputConstants.KEY_RCONTROL);
   }

   public static boolean alt() {
      return down(InputConstants.KEY_LALT) || down(InputConstants.KEY_RALT);
   }

   public static boolean shift(int mods) {
      return (mods & InputConstants.MOD_SHIFT) != 0;
   }

   public static boolean ctrl(int mods) {
      return (mods & InputConstants.MOD_CONTROL) != 0;
   }

   public static boolean alt(int mods) {
      return (mods & InputConstants.MOD_ALT) != 0;
   }

   public static boolean shortcut(int mods) {
      return (mods & InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER) != 0;
   }

   public static boolean confirm(int key) {
      return key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER;
   }

   public static boolean escape(int key) {
      return key == InputConstants.KEY_ESCAPE;
   }

   public static InputConstants.Key key(int key) {
      return InputConstants.Type.KEYBOARD.getOrCreate(key);
   }

   public static String name(int key) {
      return key(key).getDisplayName().getString();
   }

   public static KeyMapping register(String name, int key) {
      if (category == null) {
         category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("chunkatlas", "main"));
      }

      return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, key, category));
   }
}
