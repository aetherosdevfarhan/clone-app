package com.mycloner.app;

/** One clone "slot". Several entries can share the same pkg (Discord, Discord 2, ...). */
public class CloneEntry {
    public final String id;     // unique per slot, used for storage isolation + launch registry
    public final String pkg;
    public final int slot;      // 1, 2, 3...
    public final String label;  // display name, e.g. "Discord" or "Discord 2"

    public CloneEntry(String id, String pkg, int slot, String label) {
        this.id = id;
        this.pkg = pkg;
        this.slot = slot;
        this.label = label;
    }
}
