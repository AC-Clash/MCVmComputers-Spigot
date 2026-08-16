package com.acclash.vmcomputers.parts;

import com.acclash.vmcomputers.emu.GuestProfile;
import com.acclash.vmcomputers.emu.VmSpec;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The named cases, and the promise they make.
 *
 * <p>A case that says it is set up for Windows 98 has to actually produce a machine Windows 98 can
 * boot, and the model it names has to exist -- a missing model is a startup warning and a computer
 * that assembles into thin air, which is a long way from where the typo was.
 */
class CaseCatalogueTest {

    /** Model names declared by cases must be present in the hand-authored file. */
    @Test
    void everyCaseNamesAModelThatExists() throws Exception {
        Set<String> declared = modelNamesIn("/cases.json");
        // The plain case comes from the generated parts file, which this test does not read.
        declared.add("pc_case_sidepanel");

        for (ComponentType type : ComponentType.all()) {
            if (type.isCase()) {
                assertTrue(declared.contains(type.modelName()),
                        type.id() + " draws itself with '" + type.modelName()
                                + "', which no model file defines");
            }
        }
    }

    @Test
    void theNamedCasesCarryTheProfileTheyAdvertise() {
        assertSame(GuestProfile.DELL_DIMENSION_L500R,
                ComponentType.CASE_DELL_DIMENSION_L500R.profile());
        assertSame(GuestProfile.COMPAQ_PRESARIO,
                ComponentType.CASE_COMPAQ_PRESARIO.profile());
    }

    /** The plain case must stay generic, or every machine already built changes era on upgrade. */
    @Test
    void thePlainCaseStillDecidesNothing() {
        assertTrue(ComponentType.PC_CASE.isCase());
        assertNotNull(ComponentType.PC_CASE.modelName());
        org.junit.jupiter.api.Assertions.assertNull(ComponentType.PC_CASE.profile());
    }

    /** A side panel is not a case: putting one on the floor must not start a build. */
    @Test
    void onlyCasesArePlaceable() {
        assertFalse(ComponentType.CASE_SIDE_PANEL.isCase());
        assertFalse(ComponentType.GPU.isCase());
        assertTrue(ComponentType.CASE_DELL_DIMENSION_L500R.isCase());
        assertTrue(ComponentType.CASE_COMPAQ_PRESARIO.isCase());
    }

    /**
     * A case's profile has to suit the architecture the machine will be given, since assembling
     * one sets both from the same place.
     */
    @Test
    void aCasesProfileAndArchitectureAgree() {
        for (ComponentType type : ComponentType.all()) {
            GuestProfile profile = type.profile();
            if (profile == null) {
                continue;
            }
            assertNotNull(profile.architecture(),
                    type.id() + " must pin an architecture, since assembly takes it from here");
            assertSame(VmSpec.Architecture.X86_64, profile.architecture(),
                    type.id() + " is a PC, so it should be x86");
        }
    }

    private Set<String> modelNamesIn(String resource) throws Exception {
        Set<String> names = new HashSet<String>();
        InputStream in = CaseCatalogueTest.class.getResourceAsStream(resource);
        assertNotNull(in, resource + " is not on the classpath");
        StringBuilder sb = new StringBuilder();
        try (Reader reader = new InputStreamReader(in, "UTF-8")) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) > 0) {
                sb.append(buffer, 0, read);
            }
        }
        // Deliberately not a JSON parser: this test exists to catch a name that does not match,
        // and reaching for the plugin's parser here would need a running server.
        String json = sb.toString();
        int models = json.indexOf("\"models\"");
        assertTrue(models >= 0, resource + " has no models object");
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"([a-z0-9_]+)\"\\s*:\\s*\\[")
                .matcher(json.substring(models));
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }
}
