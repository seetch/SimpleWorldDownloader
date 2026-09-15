package com.j0ker2j0ker.swd.client.screen;

import com.j0ker2j0ker.swd.client.SwdClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;

import java.util.List;

public class SwdConfigScreen extends Screen {
    private final Screen parent;
    private EditBox nameField;
    private Checkbox autoDownloadCheckbox;
    private Checkbox entitiesCheckbox;
    private Checkbox playerNpcsCheckbox;

    // layout
    private int centerX;
    private int nameLabelX, nameLabelY;
    private int autoLabelX, autoLabelY;
    private int entitiesLabelX, entitiesLabelY;
    private int playerNpcsLabelX, playerNpcsLabelY;

    // description texts
    private static final List<Component> NAME_DESC = List.of(
            Component.literal("Set the name of the saved world."),
            Component.literal("If a world with this name already exists,"),
            Component.literal("the new chunks overwrite parts of that world.")
    );

    private static final List<Component> AUTO_DESC = List.of(
            Component.literal("Set whether worlds should be downloaded"),
            Component.literal("automatically on server joining.")
    );

    private static final List<Component> PLAYER_NPCS_DESC = List.of(
            Component.translatable("swd.tooltip.include_player_npcs.1"),
            Component.translatable("swd.tooltip.include_player_npcs.2")
    );

    private static final List<Component> ENTITIES_DESC = List.of(
            Component.translatable("swd.tooltip.include_entities.1"),
            Component.translatable("swd.tooltip.include_entities.2")
    );

    private static final List<ClientTooltipComponent> NAME_TOOLTIP = NAME_DESC.stream()
            .map(Component::getVisualOrderText)
            .map(ClientTooltipComponent::create)
            .toList();

    private static final List<ClientTooltipComponent> AUTO_TOOLTIP = AUTO_DESC.stream()
            .map(Component::getVisualOrderText)
            .map(ClientTooltipComponent::create)
            .toList();

    private static final List<ClientTooltipComponent> PLAYER_NPCS_TOOLTIP = PLAYER_NPCS_DESC.stream()
            .map(Component::getVisualOrderText)
            .map(ClientTooltipComponent::create)
            .toList();

    private static final List<ClientTooltipComponent> ENTITIES_TOOLTIP = ENTITIES_DESC.stream()
            .map(Component::getVisualOrderText)
            .map(ClientTooltipComponent::create)
            .toList();

    public SwdConfigScreen(Screen parent) {
        super(Component.literal("Simple World Downloader Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.centerX = this.width / 2;

        // label positions (left side)
        this.nameLabelX = centerX - 180;
        this.nameLabelY = 75;
        this.autoLabelX = centerX - 180;
        this.autoLabelY = 115;
        this.entitiesLabelX = centerX - 180;
        this.entitiesLabelY = 140;
        this.playerNpcsLabelX = centerX - 180;
        this.playerNpcsLabelY = 165;

        // move inputs right + smaller text box
        int nameFieldX = centerX - 20;   // was much more left before
        int nameFieldY = 70;
        int nameFieldW = 150;            // smaller
        int nameFieldH = 20;

        int resetX = nameFieldX + nameFieldW + 10;
        int resetW = 60;

        int autoCheckboxX = centerX - 20;
        int autoCheckboxY = 110;
        int entitiesCheckboxX = centerX - 20;
        int entitiesCheckboxY = 135;
        int playerNpcsCheckboxX = centerX - 20;
        int playerNpcsCheckboxY = 160;

        this.nameField = new EditBox(this.font, nameFieldX, nameFieldY, nameFieldW, nameFieldH,
                Component.literal("World name to save as"));
        this.nameField.setMaxLength(128);
        this.nameField.setValue(SwdClient.CONFIG.saveWorldTo != null ? SwdClient.CONFIG.saveWorldTo : "");
        this.addRenderableWidget(this.nameField);

        this.addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            this.nameField.setValue("");
            SwdClient.CONFIG.saveWorldTo = "";
        }).pos(resetX, nameFieldY).width(resetW).build());

        this.autoDownloadCheckbox = Checkbox.builder(Component.empty(), this.font)
                .pos(autoCheckboxX, autoCheckboxY)
                .selected(SwdClient.CONFIG.autoDownload)
                .build();
        this.addRenderableWidget(this.autoDownloadCheckbox);

        this.entitiesCheckbox = Checkbox.builder(Component.empty(), this.font)
                .pos(entitiesCheckboxX, entitiesCheckboxY)
                .selected(SwdClient.CONFIG.includeEntities)
                .build();
        this.addRenderableWidget(this.entitiesCheckbox);

        this.playerNpcsCheckbox = Checkbox.builder(Component.empty(), this.font)
                .pos(playerNpcsCheckboxX, playerNpcsCheckboxY)
                .selected(SwdClient.CONFIG.includePlayerNpcs)
                .build();
        this.addRenderableWidget(this.playerNpcsCheckbox);

        Button chunkMapButton = this.addRenderableWidget(Button.builder(
                Component.translatable("swd.button.chunk_map"),
                b -> this.minecraft.setScreen(new ChunkMapScreen(this))
        ).pos(centerX - 155, 40).width(150).build());
        chunkMapButton.active = this.minecraft.level != null && this.minecraft.player != null;

        this.addRenderableWidget(Button.builder(Component.translatable("swd.button.keybinds"),
                b -> this.minecraft.setScreen(new KeyBindsScreen(this, this.minecraft.options)))
                .pos(centerX + 5, 40).width(150).build());

        this.addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            SwdClient.CONFIG.saveWorldTo = this.nameField.getValue().trim();
            SwdClient.CONFIG.autoDownload = this.autoDownloadCheckbox.selected();
            SwdClient.CONFIG.includeEntities = this.entitiesCheckbox.selected();
            SwdClient.CONFIG.includePlayerNpcs = this.playerNpcsCheckbox.selected();
            SwdClient.CONFIG.save();
            this.onClose();
        }).pos(centerX - 155, this.height - 50).width(150).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> this.onClose())
                .pos(centerX + 5, this.height - 50).width(150).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        graphics.drawString(this.font, Component.literal("Save world to:"), nameLabelX, nameLabelY, 0xFFFFFFFF);
        graphics.drawString(this.font, Component.literal("Automatically download:"), autoLabelX, autoLabelY, 0xFFFFFFFF);
        graphics.drawString(this.font, Component.translatable("swd.screen.config.label.include_entities"),
                entitiesLabelX, entitiesLabelY, 0xFFFFFFFF);
        graphics.drawString(this.font, Component.translatable("swd.screen.config.label.include_player_npcs"),
                playerNpcsLabelX, playerNpcsLabelY, 0xFFFFFFFF);

        // hover descriptions (over label text or over input widgets)
        boolean hoverNameLabel = isHovering(mouseX, mouseY, nameLabelX, nameLabelY, this.font.width("Save world to:"), 10);
        boolean hoverNameField = this.nameField != null && this.nameField.isMouseOver(mouseX, mouseY);

        boolean hoverAutoLabel = isHovering(mouseX, mouseY, autoLabelX, autoLabelY, this.font.width("Automatically download:"), 10);
        boolean hoverAutoCheckbox = this.autoDownloadCheckbox != null && this.autoDownloadCheckbox.isMouseOver(mouseX, mouseY);

        Component entitiesLabel = Component.translatable("swd.screen.config.label.include_entities");
        boolean hoverEntitiesLabel = isHovering(mouseX, mouseY, entitiesLabelX, entitiesLabelY,
                this.font.width(entitiesLabel), 10);
        boolean hoverEntitiesCheckbox = this.entitiesCheckbox != null
                && this.entitiesCheckbox.isMouseOver(mouseX, mouseY);

        Component playerNpcsLabel = Component.translatable("swd.screen.config.label.include_player_npcs");
        boolean hoverPlayerNpcsLabel = isHovering(mouseX, mouseY, playerNpcsLabelX, playerNpcsLabelY,
                this.font.width(playerNpcsLabel), 10);
        boolean hoverPlayerNpcsCheckbox = this.playerNpcsCheckbox != null
                && this.playerNpcsCheckbox.isMouseOver(mouseX, mouseY);

        if (hoverNameLabel || hoverNameField) {
            graphics.renderTooltip(this.font, NAME_TOOLTIP, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        } else if (hoverAutoLabel || hoverAutoCheckbox) {
            graphics.renderTooltip(this.font, AUTO_TOOLTIP, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        } else if (hoverEntitiesLabel || hoverEntitiesCheckbox) {
            graphics.renderTooltip(this.font, ENTITIES_TOOLTIP, mouseX, mouseY,
                    DefaultTooltipPositioner.INSTANCE, null);
        } else if (hoverPlayerNpcsLabel || hoverPlayerNpcsCheckbox) {
            graphics.renderTooltip(this.font, PLAYER_NPCS_TOOLTIP, mouseX, mouseY,
                    DefaultTooltipPositioner.INSTANCE, null);
        }
    }

    private boolean isHovering(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
