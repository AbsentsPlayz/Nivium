package io.github.nivium.client.cull;

/** Last occlusion result for one entity, and where camera and entity were when it was computed. Render thread only. */
public final class CullData {
	int frame = Integer.MIN_VALUE / 2;
	boolean hidden;
	double camX;
	double camY;
	double camZ;
	double entX;
	double entY;
	double entZ;

	void set(int frame, boolean hidden, double camX, double camY, double camZ, double entX, double entY, double entZ) {
		this.frame = frame;
		this.hidden = hidden;
		this.camX = camX;
		this.camY = camY;
		this.camZ = camZ;
		this.entX = entX;
		this.entY = entY;
		this.entZ = entZ;
	}
}
