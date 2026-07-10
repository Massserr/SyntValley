package dev.syntvalley.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProjectIdentityTest {
    @Test
    void exposesStableProjectIdentity() {
        assertEquals("syntvalley", ProjectIdentity.MOD_ID);
        assertEquals("SyntValley", ProjectIdentity.DISPLAY_NAME);
        assertEquals("1.21.1", ProjectIdentity.MINECRAFT_VERSION);
        assertEquals(21, ProjectIdentity.JAVA_VERSION);
        assertTrue(ProjectIdentity.class.getPackageName().startsWith("dev.syntvalley"));
    }
}
