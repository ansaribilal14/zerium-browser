package com.zerium.browser;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-JVM policy behind {@link IncognitoProfiles}: profile naming,
 * identity checks and the persisted CSV registry of profiles awaiting
 * deletion. No android.* imports, so the rules are unit-testable in the
 * local ecj/JUnit chain.
 */
public final class IncognitoProfilePolicy {

    /** Prefix for ephemeral profile names (never a Space profile name). */
    public static final String PREFIX = "incognito_";

    private IncognitoProfilePolicy() {}

    /** Engine profile name for an Incognito session token. */
    public static String sessionProfileName(long sessionToken) {
        return PREFIX + Long.toString(sessionToken, 36);
    }

    /** True for ephemeral Incognito profile names (never a Space profile). */
    public static boolean isIncognitoProfileName(String name) {
        return name != null && name.startsWith(PREFIX);
    }

    /** Adds a profile name to a persisted CSV registry (no duplicates). */
    public static String registerInCsv(String csv, String name) {
        if (name == null || name.isEmpty()) return csv == null ? "" : csv;
        List<String> keep = new ArrayList<>();
        boolean present = false;
        if (csv != null) for (String n : csv.split(",")) {
            String t = n.trim();
            if (t.isEmpty()) continue;
            if (t.equals(name)) present = true;
            keep.add(t);
        }
        if (!present) keep.add(name);
        return String.join(",", keep);
    }

    /** Removes a profile name from a persisted CSV registry. Pure string logic. */
    public static String unregisterFromCsv(String csv, String name) {
        if (csv == null || csv.isEmpty()) return "";
        List<String> keep = new ArrayList<>();
        for (String n : csv.split(",")) {
            String t = n.trim();
            if (!t.isEmpty() && !t.equals(name)) keep.add(t);
        }
        return String.join(",", keep);
    }
}
