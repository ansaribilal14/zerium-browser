package com.zerium.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pure policy of the ephemeral Incognito profile manager: naming, identity
 * checks and the persisted CSV registry that drives the startup orphan
 * sweep (process death / refused deletions).
 */
public class IncognitoProfilePolicyTest {

    @Test
    public void sessionNamesCarryPrefixAndToken() {
        String a = IncognitoProfilePolicy.sessionProfileName(12345L);
        String b = IncognitoProfilePolicy.sessionProfileName(67890L);
        assertTrue(a.startsWith("incognito_"));
        assertTrue(b.startsWith("incognito_"));
        assertFalse(a.equals(b));
        // Same token → same name (session profile is shared by all its tabs)
        assertEquals(a, IncognitoProfilePolicy.sessionProfileName(12345L));
    }

    @Test
    public void identityCheckDistinguishesIncognitoFromSpaceProfiles() {
        assertTrue(IncognitoProfilePolicy.isIncognitoProfileName("incognito_1a2b"));
        assertFalse(IncognitoProfilePolicy.isIncognitoProfileName("space_default"));
        assertFalse(IncognitoProfilePolicy.isIncognitoProfileName("space_abc123"));
        assertFalse(IncognitoProfilePolicy.isIncognitoProfileName(null));
        assertFalse(IncognitoProfilePolicy.isIncognitoProfileName(""));
    }

    @Test
    public void csvRegistryAddAndRemove() {
        String csv = IncognitoProfilePolicy.registerInCsv("", "incognito_a");
        assertEquals("incognito_a", csv);
        csv = IncognitoProfilePolicy.registerInCsv(csv, "incognito_b");
        assertEquals("incognito_a,incognito_b", csv);
        // Re-registering the same name does not duplicate it
        csv = IncognitoProfilePolicy.registerInCsv(csv, "incognito_a");
        assertEquals("incognito_a,incognito_b", csv);
        csv = IncognitoProfilePolicy.unregisterFromCsv(csv, "incognito_a");
        assertEquals("incognito_b", csv);
        csv = IncognitoProfilePolicy.unregisterFromCsv(csv, "incognito_b");
        assertEquals("", csv);
    }

    @Test
    public void csvRegistryToleratesNullAndGarbage() {
        assertEquals("incognito_x",
                IncognitoProfilePolicy.registerInCsv(null, "incognito_x"));
        assertEquals("",
                IncognitoProfilePolicy.unregisterFromCsv(null, "incognito_x"));
        assertEquals("",
                IncognitoProfilePolicy.unregisterFromCsv("", "incognito_x"));
        // Registry entries are trimmed; blanks are dropped
        String csv = IncognitoProfilePolicy.registerInCsv(" incognito_a ,, incognito_b ",
                "incognito_c");
        assertTrue(csv.contains("incognito_a"));
        assertTrue(csv.contains("incognito_b"));
        assertTrue(csv.contains("incognito_c"));
        assertFalse(csv.contains(" "));
        // Null/blank names never mutate the registry
        assertEquals(csv, IncognitoProfilePolicy.registerInCsv(csv, null));
        assertEquals(csv, IncognitoProfilePolicy.registerInCsv(csv, ""));
    }

    @Test
    public void orphanSweepScenario() {
        // Force-stop with a private session alive: profile stays registered.
        String registry = IncognitoProfilePolicy.registerInCsv("", "incognito_dead");
        // Next launch: retryPendingDeletions runs before any WebView exists;
        // names that succeed are dropped by release()/retry contract — here
        // we verify the registry round-trips through the retry helper's
        // input format (CSV of names, possibly empty).
        String[] names = registry.split(",");
        assertEquals(1, names.length);
        assertEquals("incognito_dead", names[0]);
    }
}
