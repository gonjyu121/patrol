package dev.gonjy.patrolspectator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CameraMessageRouterTest {
    @Test
    void onlyExactConfiguredJavaCameraNameIsAccepted() {
        assertTrue(CameraMessageRouter.isCameraName("OtouGame", "OtouGame"));
        assertTrue(CameraMessageRouter.isCameraName("OtouGame", "otougame"));
        assertFalse(CameraMessageRouter.isCameraName("OtouGame", ".OtouGame"));
        assertFalse(CameraMessageRouter.isCameraName("OtouGame", "OtherPlayer"));
    }
}
