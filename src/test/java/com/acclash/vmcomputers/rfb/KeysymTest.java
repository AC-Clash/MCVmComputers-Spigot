package com.acclash.vmcomputers.rfb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The key table, shared by the typing command and by the chair's rebindable keys.
 *
 * <p>needsShift is the interesting one. A keysym names a character but a keyboard has keys, so
 * QEMU presses whichever key carries that character on the guest's layout -- and without a shift
 * state the guest hears the unshifted half of it. That shipped as a real bug: typing * put an 8
 * into the guest, and every capital letter arrived lower case.
 */
class KeysymTest {

    @Test
    void everySymbolThatSharesAKeyWithAnotherNeedsShift() {
        String shifted = "~!@#$%^&*()_+{}|:\"<>?";
        for (char c : shifted.toCharArray()) {
            assertTrue(RfbClient.Keysym.needsShift(c), "shift needed for " + c);
        }
    }

    @Test
    void theUnshiftedHalvesDoNot() {
        String plain = "`1234567890-=[]\\;',./";
        for (char c : plain.toCharArray()) {
            assertTrue(!RfbClient.Keysym.needsShift(c), "no shift for " + c);
        }
    }

    @Test
    void capitalsNeedShiftAndLowercaseDoesNot() {
        assertTrue(RfbClient.Keysym.needsShift('A'));
        assertTrue(RfbClient.Keysym.needsShift('Z'));
        assertTrue(!RfbClient.Keysym.needsShift('a'));
        assertTrue(!RfbClient.Keysym.needsShift('z'));
        assertTrue(!RfbClient.Keysym.needsShift(' '));
    }

    @Test
    void namesResolveBothWays() {
        String[] roundTrips = {"ENTER", "TAB", "ESCAPE", "SPACE", "UP", "DOWN", "LEFT", "RIGHT",
                               "HOME", "END", "PAGEUP", "PAGEDOWN", "SHIFT", "CTRL", "ALT", "F1", "F12"};
        for (String name : roundTrips) {
            Integer keysym = RfbClient.Keysym.byName(name);
            assertNotNull(keysym, name + " must resolve");
            assertEquals(name, RfbClient.Keysym.nameOf(keysym.intValue()),
                    name + " must survive a round trip");
        }
    }

    @Test
    void aliasesResolveToTheSameKey() {
        assertEquals(RfbClient.Keysym.byName("RETURN"), RfbClient.Keysym.byName("ENTER"));
        assertEquals(RfbClient.Keysym.byName("ESC"), RfbClient.Keysym.byName("ESCAPE"));
        assertEquals(RfbClient.Keysym.byName("CTRL"), RfbClient.Keysym.byName("CONTROL"));
    }

    @Test
    void singleCharactersAreTheirOwnKey() {
        assertEquals(Integer.valueOf('w'), RfbClient.Keysym.byName("w"));
        assertEquals(Integer.valueOf('7'), RfbClient.Keysym.byName("7"));
    }

    @Test
    void nonsenseIsRejectedRatherThanGuessed() {
        assertNull(RfbClient.Keysym.byName("NOPE"));
        assertNull(RfbClient.Keysym.byName(""));
        assertNull(RfbClient.Keysym.byName(null));
        assertNull(RfbClient.Keysym.byName("F13"), "only F1 to F12 exist");
    }

    @Test
    void everyAdvertisedNameActuallyWorks() {
        // The tab-completion list and the parser must not disagree; a player offered a name that
        // does not resolve is the exact failure the shared table was meant to remove.
        for (String name : RfbClient.Keysym.NAMES) {
            assertNotNull(RfbClient.Keysym.byName(name), name + " is offered but does not resolve");
        }
    }

    @Test
    void printableCharactersMapToThemselves() {
        assertEquals('a', RfbClient.Keysym.ofChar('a'));
        assertEquals('*', RfbClient.Keysym.ofChar('*'));
        assertEquals(RfbClient.Keysym.RETURN, RfbClient.Keysym.ofChar('\n'));
    }
}
