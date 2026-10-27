package io.github.nivium.client.cull;

/** Implemented on {@code Entity} by mixin: gives access to that entity's {@link CullData}. */
public interface CullHolder {
	CullData nivium$cullData();
}
