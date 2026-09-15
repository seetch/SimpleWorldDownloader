package com.j0ker2j0ker.swd.client.screen;

import com.j0ker2j0ker.swd.client.util.ChunkDownloadTracker;
import com.j0ker2j0ker.swd.client.util.SaveManager;
import com.j0ker2j0ker.swd.client.util.WorldSessionTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class ChunkMapScreen extends Screen {
    private static final int SAVED_COLOR = 0xFF43A047;
    private static final int QUEUED_COLOR = 0xFFF2B84B;
    private static final int LOADED_COLOR = 0xFF3F8FD2;
    private static final int MISSING_COLOR = 0xFF343B43;
    private static final int GRID_COLOR = 0xFF252C34;
    private static final int PANEL_COLOR = 0xE611151A;
    private static final int PLAYER_COLOR = 0xFFFFFFFF;
    private static final int MAX_VISIBLE_CHUNKS = 6000;
    private static final int SNAPSHOT_INTERVAL_TICKS = 10;

    private final Screen parent;
    private int centerChunkX;
    private int centerChunkZ;
    private int cellSize = 10;
    private int mapLeft;
    private int mapTop;
    private int mapRight;
    private int mapBottom;
    private int gridLeft;
    private int gridTop;
    private int snapshotColumns;
    private int snapshotRows;
    private int snapshotStartChunkX;
    private int snapshotStartChunkZ;
    private int snapshotTicks;
    private ChunkState[] snapshot = new ChunkState[0];
    private Component summary = Component.empty();
    private boolean snapshotDirty = true;
    private boolean centerInitialized;
    private boolean draggingMap;
    private double dragAccumulatorX;
    private double dragAccumulatorY;
    private Button downloadButton;
    private Button worldButton;

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

        int buttonWidth = Math.min(110, Math.max(70, (this.width - 50) / 3));
        int totalWidth = buttonWidth * 3 + 10;
        int buttonX = (this.width - totalWidth) / 2;
        this.downloadButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> toggleDownload())
                .pos(buttonX, this.height - 27).width(buttonWidth).build());
        this.addRenderableWidget(Button.builder(Component.translatable("swd.button.center_player"), button -> centerOnPlayer())
                .pos(buttonX + buttonWidth + 5, this.height - 27).width(buttonWidth).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .pos(buttonX + (buttonWidth + 5) * 2, this.height - 27).width(buttonWidth).build());

        this.worldButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> cycleWorldTarget())
                .pos(mapLeft, 24).width(150).build());
        this.addRenderableWidget(Button.builder(Component.translatable("swd.button.settings"), button ->
                        this.minecraft.setScreenAndShow(new SwdConfigScreen(this)))
                .pos(mapRight - 100, 24).width(100).build());

        updateDownloadButton();
        updateWorldButton();
        refreshSnapshot();
    }

    @Override
    public void tick() {
        super.tick();
        updateDownloadButton();
        updateWorldButton();
        snapshotTicks++;
        if ((snapshotDirty && snapshotTicks >= 2) || snapshotTicks >= SNAPSHOT_INTERVAL_TICKS) {
            refreshSnapshot();
        }
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.nextStratum();

        graphics.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        ClientLevel level = this.minecraft.level;
        if (level == null || this.minecraft.player == null) {
            graphics.centeredText(this.font, Component.translatable("swd.screen.chunk_map.no_world"),
                    this.width / 2, this.height / 2, 0xFFAAAAAA);
            return;
        }

        Component dimension = Component.literal(level.dimension().identifier().toString());
        Component activity = SaveManager.isSaving
                ? Component.translatable("swd.screen.chunk_map.active")
                : Component.translatable("swd.screen.chunk_map.inactive");
        graphics.text(this.font, Component.translatable("swd.screen.chunk_map.dimension", dimension),
                mapLeft, 50, 0xFFB9C2CC);
        graphics.text(this.font, activity, mapRight - this.font.width(activity), 50,
                SaveManager.isSaving ? SAVED_COLOR : 0xFF9AA3AC);

        graphics.fill(mapLeft - 2, mapTop - 2, mapRight + 2, mapBottom + 2, PANEL_COLOR);
        graphics.outline(mapLeft - 2, mapTop - 2, mapRight - mapLeft + 4, mapBottom - mapTop + 4, 0xFF59636E);

        renderChunkGrid(graphics);
        graphics.centeredText(this.font, summary, this.width / 2, mapBottom + 8, 0xFFB9C2CC);
        renderLegend(graphics);
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderChunkGrid(GuiGraphicsExtractor graphics) {
        int gridWidth = snapshotColumns * cellSize;
        int gridHeight = snapshotRows * cellSize;
        graphics.fill(gridLeft, gridTop, gridLeft + gridWidth, gridTop + gridHeight, MISSING_COLOR);

        for (int row = 0; row < snapshotRows; row++) {
            for (int column = 0; column < snapshotColumns; column++) {
                ChunkState state = snapshot[row * snapshotColumns + column];
                if (state == ChunkState.MISSING) continue;

                int x = gridLeft + column * cellSize;
                int y = gridTop + row * cellSize;
                graphics.fill(x, y, x + cellSize, y + cellSize, state.color);
            }
        }

        if (cellSize >= 6) {
            for (int column = 0; column <= snapshotColumns; column++) {
                int x = gridLeft + column * cellSize;
                graphics.verticalLine(x, gridTop, gridTop + gridHeight, GRID_COLOR);
            }
            for (int row = 0; row <= snapshotRows; row++) {
                int y = gridTop + row * cellSize;
                graphics.horizontalLine(gridLeft, gridLeft + gridWidth, y, GRID_COLOR);
            }
        }

        ChunkPos playerChunk = this.minecraft.player.chunkPosition();
        int playerColumn = playerChunk.x() - snapshotStartChunkX;
        int playerRow = playerChunk.z() - snapshotStartChunkZ;
        if (playerColumn >= 0 && playerColumn < snapshotColumns && playerRow >= 0 && playerRow < snapshotRows) {
            int x = gridLeft + playerColumn * cellSize;
            int y = gridTop + playerRow * cellSize;
            graphics.outline(x, y, cellSize, cellSize, PLAYER_COLOR);
            if (cellSize >= 7) {
                graphics.fill(x + cellSize / 2, y + cellSize / 2,
                        x + cellSize / 2 + 1, y + cellSize / 2 + 1, PLAYER_COLOR);
            }
        }
    }

    private void renderTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int gridWidth = snapshotColumns * cellSize;
        int gridHeight = snapshotRows * cellSize;
        if (mouseX < gridLeft || mouseX >= gridLeft + gridWidth || mouseY < gridTop || mouseY >= gridTop + gridHeight) {
            return;
        }

        int column = (mouseX - gridLeft) / cellSize;
        int row = (mouseY - gridTop) / cellSize;
        ChunkState state = snapshot[row * snapshotColumns + column];
        graphics.setComponentTooltipForNextFrame(this.font, List.of(
                Component.translatable("swd.screen.chunk_map.chunk",
                        snapshotStartChunkX + column, snapshotStartChunkZ + row),
                Component.translatable(state.translationKey)
        ), mouseX, mouseY);
    }

    private void renderLegend(GuiGraphicsExtractor graphics) {
        int x = mapLeft;
        int y = this.height - 43;
        x = renderLegendEntry(graphics, x, y, SAVED_COLOR, "swd.screen.chunk_map.state.saved");
        x = renderLegendEntry(graphics, x, y, QUEUED_COLOR, "swd.screen.chunk_map.state.queued");
        x = renderLegendEntry(graphics, x, y, LOADED_COLOR, "swd.screen.chunk_map.state.loaded");
        renderLegendEntry(graphics, x, y, MISSING_COLOR, "swd.screen.chunk_map.state.missing");
    }

    private int renderLegendEntry(GuiGraphicsExtractor graphics, int x, int y, int color, String translationKey) {
        Component label = Component.translatable(translationKey);
        graphics.fill(x, y + 1, x + 8, y + 9, color);
        graphics.text(this.font, label, x + 12, y, 0xFFD6DCE2);
        return x + 18 + this.font.width(label);
    }

    private void refreshSnapshot() {
        ClientLevel level = this.minecraft.level;
        if (level == null || this.minecraft.player == null) {
            snapshot = new ChunkState[0];
            snapshotColumns = 0;
            snapshotRows = 0;
            summary = Component.empty();
            snapshotDirty = false;
            snapshotTicks = 0;
            return;
        }

        int availableColumns = Math.max(1, (mapRight - mapLeft) / cellSize);
        int availableRows = Math.max(1, (mapBottom - mapTop) / cellSize);
        double scale = Math.min(1.0,
                Math.sqrt((double) MAX_VISIBLE_CHUNKS / ((double) availableColumns * availableRows)));
        snapshotColumns = Math.max(1, (int) Math.floor(availableColumns * scale));
        snapshotRows = Math.max(1, (int) Math.floor(availableRows * scale));
        snapshotStartChunkX = centerChunkX - snapshotColumns / 2;
        snapshotStartChunkZ = centerChunkZ - snapshotRows / 2;
        gridLeft = mapLeft + ((mapRight - mapLeft) - snapshotColumns * cellSize) / 2;
        gridTop = mapTop + ((mapBottom - mapTop) - snapshotRows * cellSize) / 2;

        int snapshotSize = snapshotColumns * snapshotRows;
        if (snapshot.length != snapshotSize) {
            snapshot = new ChunkState[snapshotSize];
        }

        int savedCount = 0;
        int queuedCount = 0;
        int loadedCount = 0;
        for (int row = 0; row < snapshotRows; row++) {
            for (int column = 0; column < snapshotColumns; column++) {
                int chunkX = snapshotStartChunkX + column;
                int chunkZ = snapshotStartChunkZ + row;
                ChunkState state = getState(level, chunkX, chunkZ);
                snapshot[row * snapshotColumns + column] = state;
                switch (state) {
                    case SAVED -> savedCount++;
                    case QUEUED -> queuedCount++;
                    case LOADED -> loadedCount++;
                    case MISSING -> { }
                }
            }
        }

        summary = Component.translatable("swd.screen.chunk_map.summary", savedCount, queuedCount, loadedCount);
        snapshotDirty = false;
        snapshotTicks = 0;
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
        this.mapTop = 68;
        this.mapRight = Math.max(mapLeft + 1, this.width - 18);
        this.mapBottom = Math.max(mapTop + 1, this.height - 58);
    }

    private void centerOnPlayer() {
        if (this.minecraft != null && this.minecraft.player != null) {
            ChunkPos playerChunk = this.minecraft.player.chunkPosition();
            this.centerChunkX = playerChunk.x();
            this.centerChunkZ = playerChunk.z();
            snapshotDirty = true;
        }
    }

    private void toggleDownload() {
        SaveManager.toggle();
        updateDownloadButton();
        snapshotDirty = true;
    }

    private void updateDownloadButton() {
        if (downloadButton == null) return;
        downloadButton.setMessage(Component.translatable(SaveManager.isSaving
                ? "swd.button.stop_download"
                : "swd.button.start_download"));
        downloadButton.active = this.minecraft != null && this.minecraft.level != null && this.minecraft.player != null;
    }

    private void cycleWorldTarget() {
        if (!WorldSessionTracker.selectNext()) return;
        if (SaveManager.isSaving) {
            SaveManager.stop();
        }
        ChunkDownloadTracker.reset();
        updateWorldButton();
        snapshotDirty = true;
    }

    private void updateWorldButton() {
        if (worldButton == null) return;
        String translationKey = WorldSessionTracker.isSelectedCurrent()
                ? "swd.button.world_target.current"
                : "swd.button.world_target";
        worldButton.setMessage(Component.translatable(translationKey, WorldSessionTracker.getSelectedNumber()));
        worldButton.active = WorldSessionTracker.getTargetCount() > 1;
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
            snapshotDirty = true;
        }
        while (Math.abs(dragAccumulatorY) >= cellSize) {
            int direction = dragAccumulatorY > 0 ? -1 : 1;
            centerChunkZ += direction;
            dragAccumulatorY += direction * cellSize;
            snapshotDirty = true;
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
            snapshotDirty = true;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isLeft()) {
            centerChunkX--;
            snapshotDirty = true;
            return true;
        }
        if (event.isRight()) {
            centerChunkX++;
            snapshotDirty = true;
            return true;
        }
        if (event.isUp()) {
            centerChunkZ--;
            snapshotDirty = true;
            return true;
        }
        if (event.isDown()) {
            centerChunkZ++;
            snapshotDirty = true;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreenAndShow(parent);
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
