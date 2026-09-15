package com.j0ker2j0ker.swd.client.util;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public final class ChunkDownloadTracker {
    private static final int REGION_HEADER_BYTES = 4096;
    private static final Map<String, Set<Long>> queuedChunks = new ConcurrentHashMap<>();
    private static final Map<String, Set<Long>> savedChunks = new ConcurrentHashMap<>();
    private static final Map<String, Set<Long>> loadedChunks = new ConcurrentHashMap<>();
    private static final AtomicLong generation = new AtomicLong();

    private ChunkDownloadTracker() {}

    public static void reset() {
        generation.incrementAndGet();
        queuedChunks.clear();
        savedChunks.clear();
        loadedChunks.clear();
    }

    public static void clearQueued() {
        queuedChunks.clear();
    }

    public static void loadExisting(Path worldFolder) {
        long expectedGeneration = generation.get();
        CompletableFuture.runAsync(() -> scanRegionFiles(worldFolder, expectedGeneration));
    }

    public static void markQueued(ChunkPos pos, ResourceKey<Level> dimension) {
        getChunks(queuedChunks, dimensionId(dimension)).add(ChunkPos.pack(pos.x(), pos.z()));
    }

    public static void markLoaded(ChunkPos pos, ResourceKey<Level> dimension) {
        getChunks(loadedChunks, dimensionId(dimension)).add(ChunkPos.pack(pos.x(), pos.z()));
    }

    public static void markSaved(ChunkPos pos, ResourceKey<Level> dimension) {
        String dimensionId = dimensionId(dimension);
        long packedPos = ChunkPos.pack(pos.x(), pos.z());
        removeChunk(queuedChunks, dimensionId, packedPos);
        getChunks(savedChunks, dimensionId).add(packedPos);
    }

    public static void markDequeued(ChunkPos pos, ResourceKey<Level> dimension) {
        removeChunk(queuedChunks, dimensionId(dimension), ChunkPos.pack(pos.x(), pos.z()));
    }

    public static boolean isQueued(int chunkX, int chunkZ, ResourceKey<Level> dimension) {
        return contains(queuedChunks, dimensionId(dimension), ChunkPos.pack(chunkX, chunkZ));
    }

    public static boolean isSaved(int chunkX, int chunkZ, ResourceKey<Level> dimension) {
        return contains(savedChunks, dimensionId(dimension), ChunkPos.pack(chunkX, chunkZ));
    }

    public static boolean isLoaded(int chunkX, int chunkZ, ResourceKey<Level> dimension) {
        return contains(loadedChunks, dimensionId(dimension), ChunkPos.pack(chunkX, chunkZ));
    }

    private static void scanRegionFiles(Path worldFolder, long expectedGeneration) {
        if (worldFolder == null || !Files.isDirectory(worldFolder)) return;

        try (Stream<Path> paths = Files.walk(worldFolder)) {
            paths.filter(Files::isDirectory)
                    .filter(path -> path.getFileName() != null && "region".equals(path.getFileName().toString()))
                    .forEach(regionDir -> scanRegionDirectory(worldFolder, regionDir, expectedGeneration));
        } catch (IOException ignored) {
        }
    }

    private static void scanRegionDirectory(Path worldFolder, Path regionDir, long expectedGeneration) {
        String dimensionId = resolveDimensionId(worldFolder, regionDir);
        if (dimensionId == null) return;

        try (Stream<Path> files = Files.list(regionDir)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.mca"))
                    .forEach(path -> scanRegionFile(path, dimensionId, expectedGeneration));
        } catch (IOException ignored) {
        }
    }

    private static void scanRegionFile(Path regionFile, String dimensionId, long expectedGeneration) {
        String[] nameParts = regionFile.getFileName().toString().split("\\.");
        int regionX;
        int regionZ;
        try {
            regionX = Integer.parseInt(nameParts[1]);
            regionZ = Integer.parseInt(nameParts[2]);
        } catch (NumberFormatException e) {
            return;
        }

        try (InputStream input = Files.newInputStream(regionFile)) {
            byte[] locations = input.readNBytes(REGION_HEADER_BYTES);
            if (locations.length < REGION_HEADER_BYTES || generation.get() != expectedGeneration) return;

            Set<Long> dimensionChunks = getChunks(savedChunks, dimensionId);
            for (int index = 0; index < 1024; index++) {
                if (generation.get() != expectedGeneration) return;
                int offset = index * 4;
                if (locations[offset] == 0 && locations[offset + 1] == 0
                        && locations[offset + 2] == 0 && locations[offset + 3] == 0) {
                    continue;
                }
                int chunkX = regionX * 32 + index % 32;
                int chunkZ = regionZ * 32 + index / 32;
                dimensionChunks.add(ChunkPos.pack(chunkX, chunkZ));
            }
        } catch (IOException ignored) {
        }
    }

    private static String resolveDimensionId(Path worldFolder, Path regionDir) {
        String relative = worldFolder.relativize(regionDir).toString().replace('\\', '/');
        if ("region".equals(relative)) return Level.OVERWORLD.identifier().toString();
        if ("DIM-1/region".equals(relative)) return Level.NETHER.identifier().toString();
        if ("DIM1/region".equals(relative)) return Level.END.identifier().toString();
        if (!relative.startsWith("dimensions/") || !relative.endsWith("/region")) return null;

        String dimensionPath = relative.substring("dimensions/".length(), relative.length() - "/region".length());
        int namespaceEnd = dimensionPath.indexOf('/');
        if (namespaceEnd <= 0 || namespaceEnd == dimensionPath.length() - 1) return null;
        return dimensionPath.substring(0, namespaceEnd) + ":" + dimensionPath.substring(namespaceEnd + 1);
    }

    private static String dimensionId(ResourceKey<Level> dimension) {
        return dimension != null ? dimension.identifier().toString() : Level.OVERWORLD.identifier().toString();
    }

    private static Set<Long> getChunks(Map<String, Set<Long>> chunks, String dimensionId) {
        return chunks.computeIfAbsent(dimensionId, ignored -> ConcurrentHashMap.newKeySet());
    }

    private static boolean contains(Map<String, Set<Long>> chunks, String dimensionId, long packedPos) {
        Set<Long> dimensionChunks = chunks.get(dimensionId);
        return dimensionChunks != null && dimensionChunks.contains(packedPos);
    }

    private static void removeChunk(Map<String, Set<Long>> chunks, String dimensionId, long packedPos) {
        Set<Long> dimensionChunks = chunks.get(dimensionId);
        if (dimensionChunks != null) dimensionChunks.remove(packedPos);
    }
}
