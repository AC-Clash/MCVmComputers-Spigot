package com.acclash.vmcomputers.parts;

import com.acclash.vmcomputers.emu.GuestProfile;
import com.acclash.vmcomputers.emu.VmSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The graphics bay, which until now was a required part that did nothing.
 *
 * <p>The interesting case is the plain card. Every machine built before the tiers existed has one
 * fitted, so it has to keep meaning "let the profile decide" -- if it named an adapter instead,
 * upgrading would quietly re-specify every existing computer's graphics.
 */
class GraphicsBayTest {

    @Test
    void thePlainCardDefersToTheProfile() {
        assertNull(ComponentType.GPU.vga(),
                "the plain card must not override the profile, or every old machine changes");
    }

    @Test
    void theNamedCardsEachNameAnAdapter() {
        assertSame(VmSpec.Vga.CIRRUS, ComponentType.GPU_CIRRUS.vga());
        assertSame(VmSpec.Vga.STD, ComponentType.GPU_VGA.vga());
        assertSame(VmSpec.Vga.VMWARE, ComponentType.GPU_SVGA.vga());
        assertSame(VmSpec.Vga.VIRTIO, ComponentType.GPU_VIRTIO.vga());
    }

    @Test
    void everyGraphicsCardIsInTheGraphicsBay() {
        for (ComponentType type : ComponentType.all()) {
            if (type.vga() != null) {
                assertSame(ComponentSlot.GPU, type.slot(),
                        type.id() + " names an adapter so it belongs in the graphics bay");
            }
        }
    }

    /**
     * There is a card for every adapter a profile can ask for. Without this a profile could name
     * an adapter no purchasable part provides, and the shop could never satisfy it.
     */
    @Test
    void everyAdapterAProfileWantsCanBeBought() {
        for (GuestProfile profile : GuestProfile.values()) {
            if (profile.vga() == null) {
                continue;
            }
            ComponentType match = null;
            for (ComponentType type : ComponentType.all()) {
                if (type.vga() == profile.vga()) {
                    match = type;
                    break;
                }
            }
            assertNotNull(match, profile + " wants " + profile.vga() + " and no card provides it");
        }
    }
}
