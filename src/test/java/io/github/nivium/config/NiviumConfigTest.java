package io.github.nivium.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NiviumConfigTest {
	@Test
	void missingFileIsCreatedWithDefaults(@TempDir Path dir) {
		Path file = dir.resolve("nivium.json");
		NiviumConfig.LoadResult r = NiviumConfig.load(file);
		assertEquals("created", r.status());
		assertTrue(Files.exists(file));
		assertTrue(r.config().adaptiveSimulationDistance.enabled);
		assertFalse(r.config().adaptiveViewDistance.enabled);
		assertFalse(Files.exists(NiviumConfig.backupPath(file)), "nothing to back up");
	}

	@Test
	void completeFileIsLoadedWithoutRewriting(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("nivium.json");
		NiviumConfig.load(file);
		String before = Files.readString(file);
		NiviumConfig.LoadResult r = NiviumConfig.load(file);
		assertEquals("loaded", r.status());
		assertEquals(before, Files.readString(file));
		assertFalse(Files.exists(NiviumConfig.backupPath(file)));
	}

	@Test
	void partialFileKeepsUserValuesAndIsBackedUpBeforeUpgrade(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("nivium.json");
		String original = "{\"adaptiveSimulationDistance\":{\"enabled\":false,\"minDistance\":8}}";
		Files.writeString(file, original);
		NiviumConfig.LoadResult r = NiviumConfig.load(file);
		assertEquals("upgraded", r.status());
		assertFalse(r.config().adaptiveSimulationDistance.enabled, "user value kept");
		assertEquals(8, r.config().adaptiveSimulationDistance.minDistance, "user value kept");
		assertEquals(45, r.config().adaptiveSimulationDistance.overloadMspt, "missing key gets its default");
		assertNotNull(r.config().monitoring, "missing section filled");
		assertEquals(original, Files.readString(NiviumConfig.backupPath(file)), "original preserved");
		assertTrue(Files.readString(file).contains("spikeThresholdMs"), "new keys written");
	}

	@Test
	void unreadableFileIsNeverOverwritten(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("nivium.json");
		Files.writeString(file, "{ \"monitoring\": { oops");
		NiviumConfig.LoadResult r = NiviumConfig.load(file);
		assertEquals("unreadable", r.status());
		assertFalse(r.warnings().isEmpty());
		assertEquals("{ \"monitoring\": { oops", Files.readString(file));
	}

	@Test
	void invalidValuesAreClampedInMemoryOnly(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("nivium.json");
		NiviumConfig.load(file);
		String text = Files.readString(file).replace("\"spikeThresholdMs\": 100.0", "\"spikeThresholdMs\": 5.0");
		Files.writeString(file, text);
		NiviumConfig.LoadResult r = NiviumConfig.load(file);
		assertEquals(50, r.config().monitoring.spikeThresholdMs);
		assertFalse(r.warnings().isEmpty());
		assertEquals(text, Files.readString(file), "the admin's file is not rewritten to fix values");
	}
}
