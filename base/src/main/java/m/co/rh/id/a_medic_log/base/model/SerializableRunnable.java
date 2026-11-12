package m.co.rh.id.a_medic_log.base.model;

import java.io.Serializable;

/**
 * Serializable variant of {@link Runnable}: a-navigator serializes the whole
 * route stack on process death, so a callback traveling inside a pushed
 * route's pop callback must be serializable — a plain Runnable lambda never
 * is (its lambda class does not implement Serializable), and the snapshot
 * save silently fails while that route is on the stack.
 */
public interface SerializableRunnable extends Runnable, Serializable {
}
