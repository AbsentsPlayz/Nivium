package io.github.nivium.optimize;

import io.github.nivium.Nivium;
import io.github.nivium.compat.ModCompat;
import io.github.nivium.config.NiviumConfig;
import io.github.nivium.monitor.PerformanceSnapshot;
import io.github.nivium.monitor.TickMonitor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.jetbrains.annotations.Nullable;

import java.util.function.ToIntFunction;

/**
 * Applies {@link AdaptiveController} decisions to the server's live simulation and view distance.
 *
 * <p>Why this reduces work: chunks within simulation distance of a player tick entities, block entities,
 * random ticks and scheduled ticks; view distance controls how many chunks are kept loaded and sent. Stepping
 * them down under sustained overload shrinks that work, at a visible cost (distant farms pause, terrain
 * pops in closer), which is why changes are slow, bounded, logged and always undone.
 *
 * <p>Changes go through {@code PlayerList.setSimulationDistance/setViewDistance}: live, never written to
 * server.properties, so a restart always returns to the admin's values. Server thread only.
 */
public final class AdaptiveDistances implements TickMonitor.AdaptiveStates {
	private final Entry simulation;
	private final Entry view;

	public AdaptiveDistances(NiviumConfig config) {
		this.simulation = new Entry("simulation distance", PlayerList::getSimulationDistance, PlayerList::setSimulationDistance);
		this.view = new Entry("view distance", PlayerList::getViewDistance, PlayerList::setViewDistance);
		this.applyConfig(config, null);
	}

	/** Re-reads settings; restores any setting whose adaptation was just turned off. */
	public void applyConfig(NiviumConfig config, @Nullable MinecraftServer server) {
		this.simulation.configure(config.adaptiveSimulationDistance, server);
		this.view.configure(config.adaptiveViewDistance, server);
	}

	/** Called once per second on the server thread. */
	public void evaluate(MinecraftServer server, double nowSeconds, double meanMspt) {
		if (server.tickRateManager().isSprinting() || server.tickRateManager().isFrozen()) {
			return; // tick times are not comparable while /tick sprint or /tick freeze is active
		}
		this.simulation.evaluate(server, nowSeconds, meanMspt);
		this.view.evaluate(server, nowSeconds, meanMspt);
	}

	/** Puts every setting Nivium reduced back to the admin's value. */
	public void restoreAll(MinecraftServer server) {
		this.simulation.restore(server);
		this.view.restore(server);
	}

	@Override
	public PerformanceSnapshot.DistanceState simulation() {
		return this.simulation.state();
	}

	@Override
	public PerformanceSnapshot.DistanceState view() {
		return this.view.state();
	}

	/** Why adaptation is not running on this server, or null if it is allowed. */
	public static @Nullable String blockedReason(MinecraftServer server, NiviumConfig.Adaptive config) {
		if (!config.enabled) {
			return "disabled in config";
		}
		if (ModCompat.SERVERCORE) {
			return "ServerCore is installed and manages distances itself";
		}
		if (!server.isDedicatedServer() && !config.applyOnIntegratedServer) {
			return "singleplayer/LAN world (the host's video settings control this)";
		}
		return null;
	}

	private static final class Entry {
		private final String name;
		private final ToIntFunction<PlayerList> getter;
		private final ObjIntSetter setter;
		private final AdaptiveController controller;
		private NiviumConfig.Adaptive config;
		private boolean active;
		/** The server's value at the last evaluation, tracked even while adaptation is off so status can show it. */
		private int lastObserved = -1;

		Entry(String name, ToIntFunction<PlayerList> getter, ObjIntSetter setter) {
			this.name = name;
			this.getter = getter;
			this.setter = setter;
			this.controller = new AdaptiveController(name, new NiviumConfig.Adaptive().toSettings());
		}

		void configure(NiviumConfig.Adaptive config, @Nullable MinecraftServer server) {
			this.config = config;
			this.controller.updateSettings(config.toSettings());
			if (server != null && blockedReason(server, config) != null) {
				this.restore(server);
			}
		}

		void evaluate(MinecraftServer server, double now, double meanMspt) {
			PlayerList players = server.getPlayerList();
			int observed = this.getter.applyAsInt(players);
			this.lastObserved = observed;
			this.active = blockedReason(server, this.config) == null;
			if (!this.active) {
				return;
			}
			if (server.getPlayerCount() == 0 && !this.controller.isReduced()) {
				// Nobody to simulate around: just keep tracking the configured value.
				this.controller.evaluate(now, 0, observed);
				return;
			}
			AdaptiveController.Decision d = this.controller.evaluate(now, meanMspt, observed);
			if (d != null) {
				this.setter.set(players, d.to());
				Nivium.LOGGER.info("Applied: {} {} -> {} ({}). {}", this.name, d.from(), d.to(), d.reason(),
						d.to() < d.from() ? "Will be restored when load stays low." : "");
			}
		}

		void restore(MinecraftServer server) {
			AdaptiveController.Decision d = this.controller.restore();
			if (d != null) {
				this.setter.set(server.getPlayerList(), d.to());
				Nivium.LOGGER.info("Restored: {} {} -> {} ({})", this.name, d.from(), d.to(), d.reason());
			}
		}

		PerformanceSnapshot.DistanceState state() {
			if (this.controller.base() < 0) {
				return new PerformanceSnapshot.DistanceState(this.active, this.lastObserved, this.lastObserved);
			}
			return new PerformanceSnapshot.DistanceState(this.active, this.controller.current(), this.controller.base());
		}
	}

	@FunctionalInterface
	private interface ObjIntSetter {
		void set(PlayerList players, int value);
	}
}
