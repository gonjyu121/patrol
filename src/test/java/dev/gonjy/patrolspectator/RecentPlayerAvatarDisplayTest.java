package dev.gonjy.patrolspectator;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecentPlayerAvatarDisplayTest {

    @Test
    void placesAvatarInFrontOfCameraAtEyeHeight() {
        World world = Mockito.mock(World.class);
        Location camera = new Location(world, 10.0, 64.0, 20.0, 0.0f, 0.0f);

        Location avatar = PatrolManager.recentPlayerAvatarLocation(camera);

        assertEquals(10.0, avatar.getX(), 0.001);
        assertEquals(65.62, avatar.getY(), 0.001);
        assertEquals(22.75, avatar.getZ(), 0.001);
    }
}
