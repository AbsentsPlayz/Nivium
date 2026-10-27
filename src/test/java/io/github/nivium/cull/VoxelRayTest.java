package io.github.nivium.cull;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VoxelRayTest {
	private static VoxelRay.OpaqueQuery blocks(int[]... coords) {
		Set<Long> set = new HashSet<>();
		for (int[] c : coords) {
			set.add(key(c[0], c[1], c[2]));
		}
		return (x, y, z) -> set.contains(key(x, y, z));
	}

	private static long key(int x, int y, int z) {
		return ((long) x & 0x1FFFFF) | (((long) y & 0x1FFFFF) << 21) | (((long) z & 0x1FFFFF) << 42);
	}

	private static final VoxelRay.OpaqueQuery AIR = (x, y, z) -> false;

	@Test
	void openAirIsNeverBlocked() {
		assertFalse(VoxelRay.blocked(0.5, 64.5, 0.5, 30.5, 70.5, -12.5, AIR, 256));
	}

	@Test
	void wallBetweenBlocks() {
		VoxelRay.OpaqueQuery wall = blocks(new int[]{5, 64, 0});
		assertTrue(VoxelRay.blocked(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, wall, 256));
	}

	@Test
	void wallBehindTheTargetDoesNotBlock() {
		VoxelRay.OpaqueQuery wall = blocks(new int[]{12, 64, 0});
		assertFalse(VoxelRay.blocked(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, wall, 256));
	}

	@Test
	void startAndEndVoxelsAreIgnored() {
		VoxelRay.OpaqueQuery both = blocks(new int[]{0, 64, 0}, new int[]{10, 64, 0});
		assertFalse(VoxelRay.blocked(0.5, 64.5, 0.5, 10.5, 64.5, 0.5, both, 256),
				"camera inside a block or target touching one must not count as hidden");
	}

	@Test
	void diagonalRayVisitsEveryVoxelItCrosses() {
		// Ray from (0.5,0.5) to (3.5,2.5) in the xy plane crosses voxel (1,1); a wall there must block.
		assertTrue(VoxelRay.blocked(0.5, 0.5, 0.5, 3.5, 2.5, 0.5, blocks(new int[]{1, 1, 0}), 256));
		// A voxel the line does not touch must not block.
		assertFalse(VoxelRay.blocked(0.5, 0.5, 0.5, 3.5, 2.5, 0.5, blocks(new int[]{0, 2, 0}), 256));
	}

	@Test
	void negativeDirectionsAndCoordinates() {
		assertTrue(VoxelRay.blocked(-0.5, 10.5, -0.5, -10.5, 10.5, -10.5, blocks(new int[]{-5, 10, -5}), 256));
		assertFalse(VoxelRay.blocked(-0.5, 10.5, -0.5, -10.5, 10.5, -10.5, blocks(new int[]{-5, 11, -5}), 256));
	}

	@Test
	void verticalRay() {
		assertTrue(VoxelRay.blocked(0.5, 80.5, 0.5, 0.5, 60.5, 0.5, blocks(new int[]{0, 70, 0}), 256));
	}

	@Test
	void stepCapCountsAsVisible() {
		VoxelRay.OpaqueQuery far = blocks(new int[]{50, 64, 0});
		assertFalse(VoxelRay.blocked(0.5, 64.5, 0.5, 100.5, 64.5, 0.5, far, 10), "conservative when the budget runs out");
		assertTrue(VoxelRay.blocked(0.5, 64.5, 0.5, 100.5, 64.5, 0.5, far, 256));
	}

	@Test
	void sameVoxelIsNeverBlocked() {
		assertFalse(VoxelRay.blocked(0.2, 64.2, 0.2, 0.8, 64.9, 0.7, (x, y, z) -> true, 256));
	}
}
