package io.github.nivium.client.cull;

import io.github.nivium.cull.VoxelRay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.phys.AABB;

/**
 * Entity occlusion culling: an entity that passed the frustum and Sodium's section checks is still skipped when
 * opaque blocks hide it completely from the camera. Runs on the render thread (which owns the client world), so
 * no snapshots or worker threads are needed.
 *
 * <p>Visibility is tested with rays from the camera to sample points on the entity's slightly enlarged bounding box
 * (center, 8 corners, 3 camera-facing face centers); any clear ray means visible. Only full opaque cubes
 * ({@code isSolidRender}) block. Results are reused for a few frames unless the camera or the entity moved, and a
 * "hidden" result is rechecked sooner than a "visible" one, so mistakes can only be brief pop-in, never a stale
 * invisible entity.
 *
 * <p>Never culled: players and mannequins (name tags show through walls), glowing or spectator-highlighted
 * entities, named entities, leashed entities, the camera's own vehicle, wall-attached entities (item frames,
 * paintings), display entities (server holograms can render through walls), multipart and huge entities,
 * anything within 3 blocks, and everything while spectating.
 */
public final class EntityOcclusionCuller {
	private static final double INFLATE = 0.2;
	private static final double NEAR_SQ = 3.0 * 3.0;
	private static final double MOVE_SQ = 0.25 * 0.25;
	private static final double MAX_SIZE = 8.0;
	private static final int MAX_STEPS = 512;
	private static final int RECHECK_HIDDEN_FRAMES = 6;
	private static final int RECHECK_VISIBLE_FRAMES = 10;

	private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
	private static ClientLevel level;
	private static final VoxelRay.OpaqueQuery OPAQUE = (x, y, z) -> level.getBlockState(POS.set(x, y, z)).isSolidRender();

	private static int frame;
	private static int contextFrame = -1;
	private static boolean disabledThisFrame;
	private static Entity cameraEntity;
	private static Entity cameraRoot;

	/** Statistics for benchmarks: entity-frames considered, of those skipped by the safety rules, ray tests, cache reuse, culled. */
	public static long considered;
	public static long exempt;
	public static long rayTests;
	public static long cacheHits;
	public static long culled;

	private EntityOcclusionCuller() {
	}

	/** Render thread, once per frame. */
	public static void onFrame() {
		frame++;
	}

	/** Render thread. True if the entity is certainly hidden behind opaque blocks. */
	public static boolean isHidden(Entity entity, double camX, double camY, double camZ) {
		Minecraft mc = Minecraft.getInstance();
		if (contextFrame != frame) {
			contextFrame = frame;
			level = mc.level;
			cameraEntity = mc.getCameraEntity();
			cameraRoot = cameraEntity != null ? cameraEntity.getRootVehicle() : null;
			disabledThisFrame = level == null || (mc.player != null && mc.player.isSpectator());
		}
		considered++;
		if (disabledThisFrame || isExcluded(entity, mc)) {
			exempt++;
			return false;
		}
		AABB box = entity.getBoundingBox();
		if (box.getXsize() > MAX_SIZE || box.getYsize() > MAX_SIZE || box.getZsize() > MAX_SIZE
				|| distanceSq(box, camX, camY, camZ) < NEAR_SQ) {
			exempt++;
			return false;
		}

		CullData d = ((CullHolder) entity).nivium$cullData();
		int age = frame - d.frame;
		if (age < (d.hidden ? RECHECK_HIDDEN_FRAMES : RECHECK_VISIBLE_FRAMES)
				&& sq(camX - d.camX) + sq(camY - d.camY) + sq(camZ - d.camZ) < MOVE_SQ
				&& sq(entity.getX() - d.entX) + sq(entity.getY() - d.entY) + sq(entity.getZ() - d.entZ) < MOVE_SQ) {
			cacheHits++;
			if (d.hidden) {
				culled++;
			}
			return d.hidden;
		}

		boolean hidden = computeHidden(box, camX, camY, camZ);
		d.set(frame, hidden, camX, camY, camZ, entity.getX(), entity.getY(), entity.getZ());
		if (hidden) {
			culled++;
		}
		return hidden;
	}

	private static boolean isExcluded(Entity e, Minecraft mc) {
		return e instanceof Avatar
				|| e == cameraEntity
				|| (cameraRoot != null && e.getRootVehicle() == cameraRoot)
				|| e.hasCustomName()
				|| e.isCurrentlyGlowing()
				|| mc.shouldEntityAppearGlowing(e)
				|| e instanceof BlockAttachedEntity
				|| e instanceof Display
				|| e instanceof EnderDragon
				|| (e instanceof Leashable leashable && leashable.isLeashed());
	}

	private static boolean computeHidden(AABB box, double cx, double cy, double cz) {
		double x0 = box.minX - INFLATE;
		double y0 = box.minY - INFLATE;
		double z0 = box.minZ - INFLATE;
		double x1 = box.maxX + INFLATE;
		double y1 = box.maxY + INFLATE;
		double z1 = box.maxZ + INFLATE;
		double mx = (x0 + x1) * 0.5;
		double my = (y0 + y1) * 0.5;
		double mz = (z0 + z1) * 0.5;

		if (clear(cx, cy, cz, mx, my, mz)) {
			return false;
		}
		// Faces turned towards the camera: their centers are the most likely visible points after the center.
		if ((cx < x0 || cx > x1) && clear(cx, cy, cz, cx < x0 ? x0 : x1, my, mz)) {
			return false;
		}
		if ((cy < y0 || cy > y1) && clear(cx, cy, cz, mx, cy < y0 ? y0 : y1, mz)) {
			return false;
		}
		if ((cz < z0 || cz > z1) && clear(cx, cy, cz, mx, my, cz < z0 ? z0 : z1)) {
			return false;
		}
		for (int i = 0; i < 8; i++) {
			if (clear(cx, cy, cz, (i & 1) == 0 ? x0 : x1, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? z0 : z1)) {
				return false;
			}
		}
		return true;
	}

	private static boolean clear(double sx, double sy, double sz, double ex, double ey, double ez) {
		rayTests++;
		return !VoxelRay.blocked(sx, sy, sz, ex, ey, ez, OPAQUE, MAX_STEPS);
	}

	private static double distanceSq(AABB box, double x, double y, double z) {
		double dx = Math.max(0, Math.max(box.minX - x, x - box.maxX));
		double dy = Math.max(0, Math.max(box.minY - y, y - box.maxY));
		double dz = Math.max(0, Math.max(box.minZ - z, z - box.maxZ));
		return dx * dx + dy * dy + dz * dz;
	}

	private static double sq(double v) {
		return v * v;
	}
}
