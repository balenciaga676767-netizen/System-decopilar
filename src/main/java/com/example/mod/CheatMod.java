package com.example.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class CheatMod implements ClientModInitializer {
    private static KeyBinding guiKey;

    @Override
    public void onInitializeClient() {
        // Бинд на открытие меню (Right Shift)
        guiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "Open Mod Menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "Cheat Client"
        ));

        // Игровой цикл
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            while (guiKey.wasPressed()) {
                client.setScreen(new ClickGuiScreen());
            }

            if (FeatureState.autoTotem) runAutoTotem(client);
            if (FeatureState.autoSwap) runAutoSwap(client);
            if (FeatureState.aimAssist) runAimAssist(client);
            if (FeatureState.triggerBot) runTriggerBot(client);
            if (FeatureState.killAura) runKillAura(client);
            if (FeatureState.autoSprint) runAutoSprint(client);
        });

        // Отрисовка HUD списка активных функций
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;

            int y = 10;
            drawContext.drawText(client.textRenderer, "§6=== Mod Active ===", 10, y, 0xFFFFFF, true);
            y += 12;

            for (ClickGuiScreen.FeatureItem item : ClickGuiScreen.items) {
                if (item.stateSupplier().get()) {
                    drawContext.drawText(client.textRenderer, "§a" + item.name(), 10, y, 0xFFFFFF, true);
                    y += 10;
                }
            }
        });
    }

    public static class FeatureState {
        public static boolean autoTotem = true;
        public static boolean autoSwap = false;
        public static boolean aimAssist = false;
        public static boolean triggerBot = false;
        public static boolean killAura = false;
        public static boolean autoSprint = true;
    }

    private void runAutoTotem(MinecraftClient client) {
        if (client.player.getOffHandStack().getItem() == Items.TOTEM_OF_UNDYING) return;
        for (int i = 9; i < 45; i++) {
            if (client.player.getInventory().getStack(i).getItem() == Items.TOTEM_OF_UNDYING) {
                client.interactionManager.clickSlot(client.player.playerScreenHandler.syncId, i, 40, SlotActionType.SWAP, client.player);
                break;
            }
        }
    }

    private void runAutoSwap(MinecraftClient client) {
        if (client.options.attackKey.isPressed()) {
            int bestSlot = -1;
            for (int i = 0; i < 9; i++) {
                var item = client.player.getInventory().getStack(i).getItem();
                if (item instanceof SwordItem || item instanceof AxeItem) {
                    bestSlot = i;
                    break;
                }
            }
            if (bestSlot != -1) client.player.getInventory().selectedSlot = bestSlot;
        }
    }

    private void runAimAssist(MinecraftClient client) {
        Entity target = findClosestPlayer(client, 4.5);
        if (target != null) {
            Vec3d tPos = target.getEyePos();
            Vec3d pPos = client.player.getEyePos();
            double dx = tPos.x - pPos.x, dy = tPos.y - pPos.y, dz = tPos.z - pPos.z;
            float tYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
            float tPitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
            client.player.setYaw(MathHelper.lerpAngleDegrees(0.15f, client.player.getYaw(), tYaw));
            client.player.setPitch(MathHelper.lerpAngleDegrees(0.15f, client.player.getPitch(), tPitch));
        }
    }

    private void runTriggerBot(MinecraftClient client) {
        if (client.crosshairTarget != null && client.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.ENTITY) {
            if (client.player.getAttackCooldownProgress(0.5f) >= 1.0f) {
                client.interactionManager.attackEntity(client.player, ((net.minecraft.util.hit.EntityHitResult) client.crosshairTarget).getEntity());
                client.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void runKillAura(MinecraftClient client) {
        if (client.player.getAttackCooldownProgress(0.5f) < 1.0f) return;
        Entity target = findClosestPlayer(client, 4.2);
        if (target != null) {
            client.interactionManager.attackEntity(client.player, target);
            client.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private void runAutoSprint(MinecraftClient client) {
        if (client.player.forwardSpeed > 0 && !client.player.horizontalCollision) {
            client.player.setSprinting(true);
        }
    }

    private Entity findClosestPlayer(MinecraftClient client, double range) {
        Entity closest = null;
        double closestDist = range;
        for (Entity e : client.world.getEntities()) {
            if (e instanceof PlayerEntity && e != client.player && e.isAlive()) {
                double dist = client.player.distanceTo(e);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = e;
                }
            }
        }
        return closest;
    }

    public static class ClickGuiScreen extends Screen {
        public static final List<FeatureItem> items = new ArrayList<>();

        static {
            items.add(new FeatureItem("AutoTotem", () -> FeatureState.autoTotem = !FeatureState.autoTotem, () -> FeatureState.autoTotem));
            items.add(new FeatureItem("AutoSwap", () -> FeatureState.autoSwap = !FeatureState.autoSwap, () -> FeatureState.autoSwap));
            items.add(new FeatureItem("AimAssist", () -> FeatureState.aimAssist = !FeatureState.aimAssist, () -> FeatureState.aimAssist));
            items.add(new FeatureItem("TriggerBot", () -> FeatureState.triggerBot = !FeatureState.triggerBot, () -> FeatureState.triggerBot));
            items.add(new FeatureItem("KillAura", () -> FeatureState.killAura = !FeatureState.killAura, () -> FeatureState.killAura));
            items.add(new FeatureItem("AutoSprint", () -> FeatureState.autoSprint = !FeatureState.autoSprint, () -> FeatureState.autoSprint));
        }

        public ClickGuiScreen() { super(Text.of("Cheat Menu")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);
            int y = 40;
            context.drawText(this.textRenderer, "=== CHEAT MENU (RSHIFT) ===", 30, 20, 0xFFFFFF, true);
            for (FeatureItem item : items) {
                boolean active = item.stateSupplier().get();
                context.drawText(this.textRenderer, item.name() + " [" + (active ? "ON" : "OFF") + "]", 30, y, active ? 0x00FF00 : 0xFF0000, true);
                y += 20;
            }
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                int y = 40;
                for (FeatureItem item : items) {
                    if (mouseX >= 30 && mouseX <= 200 && mouseY >= y && mouseY <= y + 15) {
                        item.action().run();
                        return true;
                    }
                    y += 20;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        public record FeatureItem(String name, Runnable action, java.util.function.Supplier<Boolean> stateSupplier) {}
    }
}
