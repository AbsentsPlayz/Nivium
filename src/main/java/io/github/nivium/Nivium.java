package io.github.nivium;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants. Nivium is a client-side mod for players: it makes the game smoother on <em>your</em> PC
 * and tells you whether lag comes from your PC, the server or your connection. It changes nothing on servers.
 */
public final class Nivium {
	public static final String MOD_ID = "nivium";
	public static final Logger LOGGER = LoggerFactory.getLogger("Nivium");

	private Nivium() {
	}
}
