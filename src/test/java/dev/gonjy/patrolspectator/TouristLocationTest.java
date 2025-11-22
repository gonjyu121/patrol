package dev.gonjy.patrolspectator;

import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TouristLocationTest {

    private ServerMock server;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testConstructorAndFields() {
        String id = "test_id";
        String name = "Test Loc";
        String worldName = "world";
        double x = 100.5;
        double y = 64.0;
        double z = -200.5;
        float yaw = 90.0f;
        float pitch = 15.0f;
        String description = "A test location";
        String worldType = "overworld";

        TouristLocation loc = new TouristLocation(id, name, worldName, x, y, z, yaw, pitch, description, worldType);

        assertEquals(id, loc.id);
        assertEquals(name, loc.name);
        assertEquals(worldName, loc.world);
        assertEquals(x, loc.x);
        assertEquals(y, loc.y);
        assertEquals(z, loc.z);
        assertEquals(yaw, loc.yaw);
        assertEquals(pitch, loc.pitch);
        assertEquals(description, loc.description);
        assertEquals(worldType, loc.worldType);
    }

    @Test
    void testToString() {
        TouristLocation loc = new TouristLocation("id1", "Name1", "world", 10, 64, 10, 0, 0, "Desc", "overworld");
        String str = loc.toString();
        assertTrue(str.contains("id1"));
        assertTrue(str.contains("Name1"));
        assertTrue(str.contains("world"));
    }
}
