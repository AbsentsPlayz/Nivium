package io.github.vortexoptimizer.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.vortexoptimizer.VortexOptimizer;
import io.github.vortexoptimizer.bench.BenchmarkSession;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.vortexoptimizer.client.bench.BenchmarkController;
import io.github.vortexoptimizer.client.gui.VortexScreen;
import io.github.vortexoptimizer.client.hud.FrameMeter;
import io.github.vortexoptimizer.client.hud.VortexHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class VortexOptimizerClient implements ClientModInitializer {
	/** ~5 s at 60 FPS: lets chunk meshing and shader/pipeline compilation settle before measuring. */
	private static final int DEFAULT_WARMUP_FRAMES = 300;
	private static final double DEFAULT_SECONDS = 60;

	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(VortexOptimizer.MOD_ID, "main"));
	private static KeyMapping openScreenKey;

	@Override
	public void onInitializeClient() {
		openScreenKey = KeyBindingHelper.registerKeyBinding(
				new KeyMapping("key.vortexoptimizer.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openScreenKey.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new VortexScreen(null));
				}
			}
		});

		FrameMeter.enabled = VortexConfig.get().fpsOverlay;
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(VortexOptimizer.MOD_ID, "hud"), VortexHud::render);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommands(dispatcher));
		// A capture that spans a world change mixes two workloads; drop it rather than report it.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(BenchmarkController::cancel));
		VortexOptimizer.LOGGER.info("Vortex Optimizer client initialized");
	}

	private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(literal("vortex").then(literal("bench")
				.then(literal("start")
						.executes(ctx -> start(ctx, "run", DEFAULT_SECONDS, DEFAULT_WARMUP_FRAMES))
						.then(argument("seconds", DoubleArgumentType.doubleArg(0, 3600))
								.executes(ctx -> start(ctx, "run", DoubleArgumentType.getDouble(ctx, "seconds"), DEFAULT_WARMUP_FRAMES))
								.then(argument("warmupFrames", IntegerArgumentType.integer(0, 100_000))
										.executes(ctx -> start(ctx, "run", DoubleArgumentType.getDouble(ctx, "seconds"),
												IntegerArgumentType.getInteger(ctx, "warmupFrames")))
										.then(argument("label", StringArgumentType.word())
												.executes(ctx -> start(ctx, StringArgumentType.getString(ctx, "label"),
														DoubleArgumentType.getDouble(ctx, "seconds"),
														IntegerArgumentType.getInteger(ctx, "warmupFrames")))))))
				.then(literal("stop").executes(ctx -> {
					if (!BenchmarkController.stop()) {
						ctx.getSource().sendError(Component.literal("No benchmark is running."));
						return 0;
					}
					return 1;
				}))
				.then(literal("status").executes(ctx -> {
					BenchmarkSession s = BenchmarkController.active();
					if (s == null) {
						ctx.getSource().sendFeedback(Component.literal("[Vortex] No benchmark running."));
					} else if (s.isWarmingUp()) {
						ctx.getSource().sendFeedback(Component.literal("[Vortex] '" + s.label() + "' warming up..."));
					} else {
						ctx.getSource().sendFeedback(Component.literal(String.format(Locale.ROOT,
								"[Vortex] '%s': %d frames, %.1fs recorded", s.label(), s.recordedFrames(),
								s.recordedSeconds(System.nanoTime()))));
					}
					return 1;
				}))));
	}

	private static int start(CommandContext<FabricClientCommandSource> ctx, String label, double seconds, int warmup) {
		if (!BenchmarkController.start(label, seconds, warmup)) {
			ctx.getSource().sendError(Component.literal("A benchmark is already running; use /vortex bench stop."));
			return 0;
		}
		String duration = seconds > 0 ? String.format(Locale.ROOT, "%.0fs", seconds) : "until /vortex bench stop";
		ctx.getSource().sendFeedback(Component.literal(String.format(Locale.ROOT,
				"[Vortex] Benchmark '%s' started: %d warm-up frames, then %s. Close chat and keep the camera path fixed.",
				label, warmup, duration)));
		return 1;
	}
}
