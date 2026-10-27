package io.github.nivium.optimize;

import org.jetbrains.annotations.Nullable;

/**
 * Decides when to step a distance setting (simulation or view distance) down under sustained overload and
 * back up after sustained recovery. Pure logic, no Minecraft types, driven once per second.
 *
 * <p>Safety rules:
 * <ul>
 *   <li>Input is the <em>mean</em> MSPT over a window (10 s), so isolated spikes cannot trigger a change.</li>
 *   <li>Overload must persist for {@code overloadSeconds} and recovery for {@code recoverSeconds}; the band
 *       between the two thresholds resets both timers (hysteresis, no oscillation).</li>
 *   <li>One step at a time, at most once per {@code cooldownSeconds}.</li>
 *   <li>Never below {@code min(minDistance, base)} and never above {@code base}, the value the admin configured.</li>
 *   <li>If something else changes the setting, that value becomes the new base: Nivium never fights it.</li>
 * </ul>
 */
public final class AdaptiveController {
	public record Settings(int minDistance, double overloadMspt, double recoverMspt,
						   double overloadSeconds, double recoverSeconds, double cooldownSeconds) {
	}

	/** A change to apply: {@code from} -> {@code to}, with a human-readable reason. */
	public record Decision(int from, int to, String reason) {
	}

	private final String name;
	private Settings settings;
	private int base = -1;
	private int current = -1;
	private double overloadSince = Double.NaN;
	private double recoverSince = Double.NaN;
	private double lastChange = Double.NEGATIVE_INFINITY;

	public AdaptiveController(String name, Settings settings) {
		this.name = name;
		this.settings = settings;
	}

	public void updateSettings(Settings settings) {
		this.settings = settings;
	}

	/** Starts tracking from the server's configured value. */
	public void reset(int configured) {
		this.base = configured;
		this.current = configured;
		this.overloadSince = Double.NaN;
		this.recoverSince = Double.NaN;
		this.lastChange = Double.NEGATIVE_INFINITY;
	}

	public int base() {
		return this.base;
	}

	public int current() {
		return this.current;
	}

	/** True while Nivium is holding the setting below the admin's value. */
	public boolean isReduced() {
		return this.current >= 0 && this.current < this.base;
	}

	public int floor() {
		return Math.min(this.settings.minDistance(), this.base);
	}

	/**
	 * @param nowSeconds monotonic time in seconds
	 * @param meanMspt   mean milliseconds per tick over the evaluation window
	 * @param observed   the setting's current value on the server
	 * @return the change to apply, or null to leave it alone
	 */
	public @Nullable Decision evaluate(double nowSeconds, double meanMspt, int observed) {
		if (this.base < 0) {
			this.reset(observed);
			return null;
		}
		if (observed != this.current) {
			// Changed by an admin or another mod: adopt it as the new baseline instead of fighting it.
			this.reset(observed);
			return null;
		}

		Settings s = this.settings;
		boolean cooledDown = nowSeconds - this.lastChange >= s.cooldownSeconds();

		if (meanMspt >= s.overloadMspt()) {
			this.recoverSince = Double.NaN;
			if (Double.isNaN(this.overloadSince)) {
				this.overloadSince = nowSeconds;
			}
			if (nowSeconds - this.overloadSince >= s.overloadSeconds() && cooledDown && this.current > this.floor()) {
				return this.change(nowSeconds, this.current - 1, String.format(java.util.Locale.ROOT,
						"mean tick time %.1f ms >= %.0f ms for %.0f s", meanMspt, s.overloadMspt(), nowSeconds - this.overloadSince));
			}
		} else if (meanMspt <= s.recoverMspt()) {
			this.overloadSince = Double.NaN;
			if (Double.isNaN(this.recoverSince)) {
				this.recoverSince = nowSeconds;
			}
			if (nowSeconds - this.recoverSince >= s.recoverSeconds() && cooledDown && this.current < this.base) {
				return this.change(nowSeconds, this.current + 1, String.format(java.util.Locale.ROOT,
						"mean tick time %.1f ms <= %.0f ms for %.0f s", meanMspt, s.recoverMspt(), nowSeconds - this.recoverSince));
			}
		} else {
			this.overloadSince = Double.NaN;
			this.recoverSince = Double.NaN;
		}
		return null;
	}

	/** The change that puts the admin's value back, or null if nothing is reduced. */
	public @Nullable Decision restore() {
		if (!this.isReduced()) {
			return null;
		}
		Decision d = new Decision(this.current, this.base, "restoring the configured " + this.name);
		this.current = this.base;
		this.overloadSince = Double.NaN;
		this.recoverSince = Double.NaN;
		return d;
	}

	private Decision change(double now, int to, String reason) {
		Decision d = new Decision(this.current, to, reason);
		this.current = to;
		this.lastChange = now;
		// Require a fresh full window before the next step in either direction.
		this.overloadSince = Double.NaN;
		this.recoverSince = Double.NaN;
		return d;
	}

	public String name() {
		return this.name;
	}
}
