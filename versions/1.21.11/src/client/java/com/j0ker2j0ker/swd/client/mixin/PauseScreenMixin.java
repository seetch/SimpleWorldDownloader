package com.j0ker2j0ker.swd.client.mixin;

import com.j0ker2j0ker.swd.client.screen.SwdConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen{

    protected PauseScreenMixin(Component title) {
        super(title);
    }
    @Unique
    private static final Identifier SETTINGS = Identifier.fromNamespaceAndPath("swd", "icon/settings");

    @Inject(at = @At("RETURN"), method = "createPauseMenu")
    public void addSettingsButton(CallbackInfo ci) {
        // only shows the button if the player is on a multiplayer server or in a flashback replay
        if (Minecraft.getInstance().getSingleplayerServer() != null && Minecraft.getInstance().isLocalServer() && !Minecraft.getInstance().getSingleplayerServer().getWorldData().getLevelName().equalsIgnoreCase("Replay"))
            return;

        refresh();
    }

    @Unique
    private void refresh() {
        SpriteIconButton settingsButton = this.addRenderableWidget(SpriteIconButton.builder(Component.nullToEmpty(""), (button) -> {
            Minecraft.getInstance().setScreen(new SwdConfigScreen(this));
            button.setFocused(false);
        }, true).width(20).sprite(SETTINGS, 16, 16).build());
        settingsButton.setPosition(4, height-24);
    }

}
