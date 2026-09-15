package com.j0ker2j0ker.swd.client;

import com.j0ker2j0ker.swd.client.screen.ChunkMapScreen;
import com.j0ker2j0ker.swd.client.screen.SwdConfigScreen;
import com.j0ker2j0ker.swd.client.util.ChunkDownloadTracker;
import com.j0ker2j0ker.swd.client.util.SaveManager;
import com.j0ker2j0ker.swd.client.util.SwdConfig;
import com.j0ker2j0ker.swd.client.util.WorldSessionTracker;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class SwdClient implements ClientModInitializer {

    public static SwdConfig CONFIG;
    public static Path resourcepack_locations;

    public static final String MOD_ID = "swd";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static KeyMapping CHUNK_MAP_KEY;

    @Override
    public void onInitializeClient() {
        CONFIG = SwdConfig.load();
        CHUNK_MAP_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.swd.open_chunk_map",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_U,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "controls"))
        ));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            SaveManager.stop();
            ChunkDownloadTracker.reset();
            WorldSessionTracker.reset();
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            WorldSessionTracker.ensureConnection(handler);
            ChunkDownloadTracker.reset();
            if(SaveManager.isSaving) {
                SaveManager.stop();
                SaveManager.start();
            }else {
                if(CONFIG.autoDownload && !Minecraft.getInstance().isLocalServer()) {
                    SaveManager.start();
                }
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenEvents.remove(screen).register((closedScreen) -> {
                SaveManager.onScreenClosed(closedScreen);
            });
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            SaveManager.stop();
            CONFIG.save();

            if (SaveManager.saveThread != null && SaveManager.saveThread.isAlive()) {
                try {
                    SaveManager.saveThread.join(3000);
                } catch (InterruptedException ignored) {}
            }
        });

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            SaveManager.lastClicked = hitResult.getBlockPos();
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            SaveManager.lastClicked = entity;
            return InteractionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SaveManager.tick();
            while (CHUNK_MAP_KEY.consumeClick()) {
                if (client.level != null && client.player != null && client.screen == null) {
                    client.setScreen(new ChunkMapScreen(null));
                }
            }
        });

        registerCommands();
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                    literal("swd")
                            .then(literal("config")
                                    .executes(ctx -> {
                                        Minecraft.getInstance().execute(() ->
                                                Minecraft.getInstance().setScreen(
                                                        new SwdConfigScreen(Minecraft.getInstance().screen)
                                                )
                                        );
                                        return 1;
                                    }))
            );
        });
    }
}
