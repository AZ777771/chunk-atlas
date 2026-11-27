package dev.chunkatlas.port;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class Keys {
   public static final int LEFT_SUPER = InputConstants.KEY_LWIN;
   public static final int RIGHT_SUPER = InputConstants.KEY_RWIN;

   public static final int PAD_PLUS = InputConstants.KEY_ADD;
   public static final int PAD_MINUS = 333;

   private static final String CATEGORY = "key.category.chunkatlas.main";

   private Keys() {
   }

   public static boolean down(int key) {
      return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), key);
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

   static boolean superKey() {
      return down(LEFT_SUPER) || down(RIGHT_SUPER);
   }

   static int mods() {
      return (shift() ? GLFW.GLFW_MOD_SHIFT : 0) | (ctrl() ? GLFW.GLFW_MOD_CONTROL : 0) | (alt() ? GLFW.GLFW_MOD_ALT : 0)
         | (superKey() ? GLFW.GLFW_MOD_SUPER : 0);
   }

   public static boolean shift(int mods) {
      return (mods & GLFW.GLFW_MOD_SHIFT) != 0;
   }

   public static boolean ctrl(int mods) {
      return (mods & GLFW.GLFW_MOD_CONTROL) != 0;
   }

   public static boolean alt(int mods) {
      return (mods & GLFW.GLFW_MOD_ALT) != 0;
   }

   public static boolean shortcut(int mods) {
      return (mods & (Minecraft.ON_OSX ? GLFW.GLFW_MOD_SUPER : GLFW.GLFW_MOD_CONTROL)) != 0;
   }

   public static boolean confirm(int key) {
      return key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER;
   }

   public static boolean escape(int key) {
      return key == InputConstants.KEY_ESCAPE;
   }

   public static InputConstants.Key key(int key) {
      return InputConstants.Type.KEYSYM.getOrCreate(key);
   }

   public static String name(int key) {
      return key(key).getDisplayName().getString();
   }

   public static KeyMapping register(String name, int key) {
      return KeyBindingHelper.registerKeyBinding(new KeyMapping(name, key, CATEGORY));
   }
}
