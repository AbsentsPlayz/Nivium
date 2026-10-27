package io.github.nivium.cull;

/**
 * Walks the block grid along a straight line (Amanatides-Woo voxel traversal) and reports whether an opaque
 * block lies strictly between the two end points. Allocation-free; no Minecraft types, so it is unit-tested.
 *
 * <p>Conservative by design: the voxels containing the start (camera) and the end (sample point) are never
 * tested, so a camera inside a block or an entity touching a wall never counts as hidden because of that block.
 */
public final class VoxelRay {
	/** Answers whether the block at the given coordinates fully blocks sight (a full, opaque cube). */
	@FunctionalInterface
	public interface OpaqueQuery {
		boolean isOpaque(int x, int y, int z);
	}

	private VoxelRay() {
	}

	/**
	 * @param maxSteps safety cap on visited voxels; reaching it counts as <em>not</em> blocked (visible)
	 * @return true if an opaque voxel lies between start and end (exclusive of both end voxels)
	 */
	public static boolean blocked(double sx, double sy, double sz, double ex, double ey, double ez,
								  OpaqueQuery query, int maxSteps) {
		int x = floor(sx);
		int y = floor(sy);
		int z = floor(sz);
		int endX = floor(ex);
		int endY = floor(ey);
		int endZ = floor(ez);

		double dx = ex - sx;
		double dy = ey - sy;
		double dz = ez - sz;
		int stepX = dx > 0 ? 1 : dx < 0 ? -1 : 0;
		int stepY = dy > 0 ? 1 : dy < 0 ? -1 : 0;
		int stepZ = dz > 0 ? 1 : dz < 0 ? -1 : 0;
		double tDeltaX = stepX != 0 ? Math.abs(1.0 / dx) : Double.POSITIVE_INFINITY;
		double tDeltaY = stepY != 0 ? Math.abs(1.0 / dy) : Double.POSITIVE_INFINITY;
		double tDeltaZ = stepZ != 0 ? Math.abs(1.0 / dz) : Double.POSITIVE_INFINITY;
		double tMaxX = stepX > 0 ? (x + 1 - sx) / dx : stepX < 0 ? (sx - x) / -dx : Double.POSITIVE_INFINITY;
		double tMaxY = stepY > 0 ? (y + 1 - sy) / dy : stepY < 0 ? (sy - y) / -dy : Double.POSITIVE_INFINITY;
		double tMaxZ = stepZ > 0 ? (z + 1 - sz) / dz : stepZ < 0 ? (sz - z) / -dz : Double.POSITIVE_INFINITY;

		for (int steps = 0; steps < maxSteps; steps++) {
			if (x == endX && y == endY && z == endZ) {
				return false;
			}
			double t;
			if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
				x += stepX;
				t = tMaxX;
				tMaxX += tDeltaX;
			} else if (tMaxY <= tMaxZ) {
				y += stepY;
				t = tMaxY;
				tMaxY += tDeltaY;
			} else {
				z += stepZ;
				t = tMaxZ;
				tMaxZ += tDeltaZ;
			}
			if (t > 1.0 || (x == endX && y == endY && z == endZ)) {
				return false;
			}
			if (query.isOpaque(x, y, z)) {
				return true;
			}
		}
		return false;
	}

	private static int floor(double v) {
		int i = (int) v;
		return v < i ? i - 1 : i;
	}
}
