package com.j0ker2j0ker.swd.client.screen;

import com.j0ker2j0ker.swd.client.util.ChunkDownloadTracker;
import com.j0ker2j0ker.swd.client.util.SaveManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;

public class ChunkMapScreen extends Screen {
    private static final int SAVED_COLOR = 0xFF43A047;
    private static final int QUEUED_COLOR = 0xFFF2B84B;
    private static final int LOADED_COLOR = 0xFF3F8FD2;
    private static final int MISSING_COLOR = 0xFF343B43;
    private static final int GRID_COLOR = 0xFF252C34;
    private static final int PANEL_COLOR = 0xE611151A;
    private static final int PLAYER_COLOR = 0xFFFFFFFF;

    private final Screen parent;
    private int centerChunkX;
    private int centerChunkZ;
    private int cellSize = 10;
    private int mapLeft;
    private int mapTop;
    private int mapRight;
    private int mapBottom;
    private boolean centerInitialized;
    private boolean draggingMap;
    private double dragAccumulatorX;
    private double dragAccumulatorY;

    public ChunkMapScreen(Screen parent) {
        super(Component.translatable("swd.screen.chunk_map.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        updateMapBounds();
        if (!centerInitialized) {
            centerOnPlayer();
            centerInitialized = true;
        }

        this.addRenderableWidget(Button.builder(Component.translatable("swd.button.center_player"), button -> centerOnPlayer())
                .pos(this.width / 2 - 105, this.height - 27).width(100).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .pos(this.width / 2 + 5, this.height - 27).width(100).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.nextStratum();

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        ClientLevel level = this.minecraft.level;
        if (level == null || this.minecraft.player == null) {
            graphics.drawCenteredString(this.font, Component.translatable("swd.screen.chunk_map.no_world"),
                    this.width / 2, this.height / 2, 0xFFAAAAAA);
            return;
        }

        Component dimension = Component.literal(level.dimension().identifier().toString());
        Component activity = SaveManager.isSaving
                ? Component.translatable("swd.screen.chunk_map.active")
                : Component.translatable("swd.screen.chunk_map.inactive");
        graphics.drawString(this.font, Component.translatable("swd.screen.chunk_map.dimension", dimension),
                mapLeft, 30, 0xFFB9C2CC);
        graphics.drawString(this.font, activity, mapRight - this.font.width(activity), 30,
                SaveManager.isSaving ? SAVED_COLOR : 0xFF9AA3AC);

        graphics.fill(mapLeft - 2, mapTop - 2, mapRight + 2, mapBottom + 2, PANEL_COLOR);
        graphics.renderOutline(mapLeft - 2, mapTop - 2, mapRight - mapLeft + 4, mapBottom - mapTop + 4, 0xFF59636E);

        int columns = Math.max(1, (mapRight - mapLeft) / cellSize);
        int rows = Math.max(1, (mapBottom - mapTop) / cellSize);
        int startChunkX = centerChunkX - columns / 2;
        int startChunkZ = centerChunkZ - rows / 2;
        int playerChunkX = this.minecraft.player.chunkPosition().x;
        int playerChunkZ = this.minecraft.player.chunkPosition().z;

        int savedCount = 0;
        int queuedCount = 0;
        int loadedCount = 0;
        int hoveredChunkX = Integer.MIN_VALUE;
        int hoveredChunkZ = Integer.MIN_VALUE;
        ChunkState hoveredState = null;

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int chunkX = startChunkX + column;
                int chunkZ = startChunkZ + row;
                ChunkState state = getState(level, chunkX, chunkZ);
                int x = mapLeft + column * cellSize;
                int y = mapTop + row * cellSize;

                graphics.fill(x, y, x + cellSize - 1, y + cellSize - 1, state.color);
                if (cellSize >= 8) {
                    graphics.renderOutline(x, y, cellSize - 1, cellSize - 1, GRID_COLOR);
                }
                if (chunkX == playerChunkX && chunkZ == playerChunkZ) {
                    graphics.renderOutline(x, y, cellSize - 1, cellSize - 1, PLAYER_COLOR);
                    if (cellSize >= 7) {
                        graphics.fill(x + cellSize / 2, y + cellSize / 2,
                                x + cellSize / 2 + 1, y + cellSize / 2 + 1, PLAYER_COLOR);
                    }
                }

                switch (state) {
                    case SAVED -> savedCount++;
                    case QUEUED -> queuedCount++;
                    case LOADED -> loadedCount++;
                    case MISSING -> {}
                }

                if (mouseX >= x && mouseX < x + cellSize && mouseY >= y && mouseY < y + cellSize) {
                    hoveredChunkX = chunkX;
                    hoveredChunkZ = chunkZ;
                    hoveredState = state;
                }
            }
        }

        Component summary = Component.translatable("swd.screen.chunk_map.summary", savedCount, queuedCount, loadedCount);
        graphics.drawCenteredString(this.font, summary, this.width / 2, mapBottom + 8, 0xFFB9C2CC);
        renderLegend(graphics);

        if (hoveredState != null) {
            graphics.setComponentTooltipForNextFrame(this.font, List.of(
                    Component.translatable("swd.screen.chunk_map.chunk", hoveredChunkX, hoveredChunkZ),
                    Component.translatable(hoveredState.translationKey)
            ), mouseX, mouseY);
        }
    }

    private void renderLegend(GuiGraphics graphics) {
        int x = mapLeft;
        int y = this.height - 43;
        x = renderLegendEntry(graphics, x, y, SAVED_COLOR, "swd.screen.chunk_map.state.saved");
        x = renderLegendEntry(graphics, x, y, QUEUED_COLOR, "swd.screen.chunk_map.state.queued");
        x = renderLegendEntry(graphics, x, y, LOADED_COLOR, "swd.screen.chunk_map.state.loaded");
        renderLegendEntry(graphics, x, y, MISSING_COLOR, "swd.screen.chunk_map.state.missing");
    }

    private int renderLegendEntry(GuiGraphics graphics, int x, int y, int color, String translationKey) {
        Component label = Component.translatable(translationKey);
        graphics.fill(x, y + 1, x + 8, y + 9, color);
        graphics.drawString(this.font, label, x + 12, y, 0xFFD6DCE2);
        return x + 18 + this.font.width(label);
    }

    private ChunkState getState(ClientLevel level, int chunkX, int chunkZ) {
        if (ChunkDownloadTracker.isQueued(chunkX, chunkZ, level.dimension())) {
            return ChunkState.QUEUED;
        }
        if (ChunkDownloadTracker.isSaved(chunkX, chunkZ, level.dimension())) {
            return ChunkState.SAVED;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (chunk != null && !chunk.isEmpty()) {
            return ChunkState.LOADED;
        }
        return ChunkState.MISSING;
    }

    private void updateMapBounds() {
        this.mapLeft = 18;
        this.mapTop = 48;
        this.mapRight = Math.max(mapLeft + 1, this.width - 18);
        this.mapBottom = Math.max(mapTop + 1, this.height - 58);
    }

    private void centerOnPlayer() {
        if (this.minecraft != null && this.minecraft.player != null) {
            ChunkPos playerChunk = this.minecraft.player.chunkPosition();
            this.centerChunkX = playerChunk.x;
            this.centerChunkZ = playerChunk.z;
        }
    }

    private boolean isOverMap(double mouseX, double mouseY) {
        return mouseX >= mapLeft && mouseX < mapRight && mouseY >= mapTop && mouseY < mapBottom;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && isOverMap(event.x(), event.y())) {
            draggingMap = true;
            dragAccumulatorX = 0;
            dragAccumulatorY = 0;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!draggingMap) {
            return super.mouseDragged(event, deltaX, deltaY);
        }

        dragAccumulatorX += deltaX;
        dragAccumulatorY += deltaY;
        while (Math.abs(dragAccumulatorX) >= cellSize) {
            int direction = dragAccumulatorX > 0 ? -1 : 1;
            centerChunkX += direction;
            dragAccumulatorX += direction * cellSize;
        }
        while (Math.abs(dragAccumulatorY) >= cellSize) {
            int direction = dragAccumulatorY > 0 ? -1 : 1;
            centerChunkZ += direction;
            dragAccumulatorY += direction * cellSize;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && draggingMap) {
            draggingMap = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isOverMap(mouseX, mouseY) && verticalAmount != 0) {
            cellSize = Math.clamp(cellSize + (verticalAmount > 0 ? 1 : -1), 4, 20);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isLeft()) {
            centerChunkX--;
            return true;
        }
        if (event.isRight()) {
            centerChunkX++;
            return true;
        }
        if (event.isUp()) {
            centerChunkZ--;
            return true;
        }
        if (event.isDown()) {
            centerChunkZ++;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum ChunkState {
        SAVED(SAVED_COLOR, "swd.screen.chunk_map.state.saved"),
        QUEUED(QUEUED_COLOR, "swd.screen.chunk_map.state.queued"),
        LOADED(LOADED_COLOR, "swd.screen.chunk_map.state.loaded"),
        MISSING(MISSING_COLOR, "swd.screen.chunk_map.state.missing");

        private final int color;
        private final String translationKey;

        ChunkState(int color, String translationKey) {
            this.color = color;
            this.translationKey = translationKey;
        }
    }
}

