package com.hybridash.deathcompass;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Death spots you've already walked back to (or hid), so the arrow doesn't come back after relogging. */
public class Cleared {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("deathcompass-cleared.txt");
	private static final int MAX = 200;

	private final Set<String> keys = new LinkedHashSet<>();

	public static Cleared load() {
		Cleared c = new Cleared();
		try {
			if (Files.exists(FILE)) c.keys.addAll(Files.readAllLines(FILE));
		} catch (IOException e) {
			DeathCompassClient.LOGGER.warn("Couldn't read {}", FILE, e);
		}
		return c;
	}

	public boolean contains(String key) {
		return keys.contains(key);
	}

	public void add(String key) {
		if (!keys.add(key)) return;
		// Only keep the newest entries so the file never grows forever
		if (keys.size() > MAX) {
			List<String> list = new ArrayList<>(keys);
			keys.clear();
			keys.addAll(list.subList(list.size() - MAX, list.size()));
		}
		try {
			Files.createDirectories(FILE.getParent());
			Files.write(FILE, keys);
		} catch (IOException e) {
			DeathCompassClient.LOGGER.warn("Couldn't save {}", FILE, e);
		}
	}
}
