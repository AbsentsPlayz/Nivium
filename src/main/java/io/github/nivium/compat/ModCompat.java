package io.github.nivium.compat;

import net.fabricmc.loader.api.FabricLoader;

/** Other server mods whose presence changes what Nivium should do. Checked once at startup. */
public final class ModCompat {
	/** General tick optimizations; Nivium does not duplicate them. */
	public static final boolean LITHIUM = loaded("lithium");
	/** Has its own dynamic view/simulation distance; Nivium must not adjust the same settings. */
	public static final boolean SERVERCORE = loaded("servercore");
	public static final boolean FERRITECORE = loaded("ferritecore");
	public static final boolean C2ME = loaded("c2me");

	private ModCompat() {
	}

	private static boolean loaded(String id) {
		return FabricLoader.getInstance().isModLoaded(id);
	}
}
