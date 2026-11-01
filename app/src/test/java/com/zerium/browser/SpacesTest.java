package com.zerium.browser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Zerium Spaces persistence core: load/dump roundtrip, invariants. */
public class SpacesTest {

    @Test
    public void emptyStateYieldsDefaultSpace() {
        Spaces s = Spaces.load("");
        assertEquals(1, s.count());
        assertEquals(Spaces.DEFAULT_ID, s.activeId());
        assertNotNull(s.active());
        assertFalse(s.active().isolated);
    }

    @Test
    public void garbageInputStillYieldsDefaultSpace() {
        Spaces s = Spaces.load("not json at all");
        assertEquals(1, s.count());
        assertEquals(Spaces.DEFAULT_ID, s.activeId());
    }

    @Test
    public void createRenameRemoveRoundtrip() {
        Spaces s = Spaces.load("");
        Spaces.Space work = s.create("Work", true);
        assertNotNull(work);
        assertTrue(work.isolated);
        assertTrue(s.rename(work.id, "  Engineering  "));
        assertEquals("Engineering", s.get(work.id).name);
        assertEquals(2, s.count());

        String doc = s.dump();
        Spaces s2 = Spaces.load(doc);
        assertEquals(2, s2.count());
        assertEquals("Engineering", s2.get(work.id).name);
        assertTrue(s2.get(work.id).isolated);
        assertFalse(s2.get(work.id).name.equals("Work"));

        assertTrue(s2.remove(work.id));
        assertEquals(1, s2.count());
        assertNull(s2.get(work.id));
    }

    @Test
    public void activeIdSurvivesRoundtripAndFallsBack() {
        Spaces s = Spaces.load("");
        Spaces.Space work = s.create("Work", true);
        s.setActive(work.id);
        Spaces s2 = Spaces.load(s.dump());
        assertEquals(work.id, s2.activeId());
        assertEquals("Work", s2.active().name);

        // Removing the active space falls back to the default.
        assertTrue(s2.remove(work.id));
        assertEquals(Spaces.DEFAULT_ID, s2.activeId());
        assertEquals("Personal", s2.active().name);
    }

    @Test
    public void defaultSpaceIsProtected() {
        Spaces s = Spaces.load("");
        assertFalse(s.remove(Spaces.DEFAULT_ID));
        assertFalse(s.remove(null));
        assertEquals(1, s.count());
        // Default space survives unknown active ids.
        Spaces s2 = Spaces.load(s.dump().replace("default", "ghost"));
        assertEquals(Spaces.DEFAULT_ID, s2.activeId());
    }

    @Test
    public void emptyNameIsRejected() {
        Spaces s = Spaces.load("");
        assertNull(s.create("   ", false));
        Spaces.Space sp = s.create("X", false);
        assertNotNull(sp);
        assertFalse(s.rename(sp.id, ""));
        assertFalse(s.rename(sp.id, null));
        assertEquals("X", s.get(sp.id).name);
    }

    @Test
    public void legacyBareArrayStillLoads() {
        Spaces s = Spaces.load(
                "[{\"id\":\"s1\",\"name\":\"Legacy\",\"isolated\":false,\"created\":5}]");
        assertEquals(2, s.count());
        assertEquals("Legacy", s.get("s1").name);
        // Bare array has no active marker -> default stays active.
        assertEquals(Spaces.DEFAULT_ID, s.activeId());
    }
}
