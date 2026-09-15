package com.j0ker2j0ker.swd.client.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SwdConfig {

    public enum NotificationMode {
        ACTIONBAR,
        BOSSBAR,
        OFF
    }

    public String saveWorldTo = "";
    public boolean autoDownload = false;
    public boolean resumeDownloads = true;
    public NotificationMode notificationMode = NotificationMode.ACTIONBAR;
    public boolean includeEntities = true;
    public boolean includePlayerNpcs = true;
    public boolean includePlayerData = true;
    public boolean includeResourcePacks = true;
    public Map<String, Integer> worldTargetSlots = new HashMap<>();

    private static final Gson GSON =
            new GsonBuilder().setPrettyPrinting().create();

    private static final Path PATH =
            FabricLoader.getInstance()
                    .getConfigDir()
                    .resolve("SimpleWorldDownloader.json");

    public static SwdConfig load() {
        try {
            if (Files.exists(PATH)) {
                return GSON.fromJson(Files.readString(PATH), SwdConfig.class);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new SwdConfig();
    }

    public void save() {
        try {
            Files.writeString(PATH, GSON.toJson(this));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
