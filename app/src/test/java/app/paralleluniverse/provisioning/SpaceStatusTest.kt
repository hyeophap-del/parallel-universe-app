package app.paralleluniverse.provisioning

import org.junit.Assert.assertEquals
import org.junit.Test

class SpaceStatusTest {

    @Test
    fun profileOwnerIsInsideProfile() {
        assertEquals(
            SpaceStatus.INSIDE_PROFILE,
            resolveSpaceStatus(isProfileOwner = true, hasOurProfile = false, provisioningAllowed = false, profileCount = 2),
        )
    }

    @Test
    fun existingOwnProfileIsReady() {
        assertEquals(
            SpaceStatus.READY,
            resolveSpaceStatus(isProfileOwner = false, hasOurProfile = true, provisioningAllowed = false, profileCount = 2),
        )
    }

    @Test
    fun noProfileAndAllowedCanCreate() {
        assertEquals(
            SpaceStatus.CAN_CREATE,
            resolveSpaceStatus(isProfileOwner = false, hasOurProfile = false, provisioningAllowed = true, profileCount = 1),
        )
    }

    @Test
    fun foreignProfileBlocksCreation() {
        assertEquals(
            SpaceStatus.OTHER_PROFILE_EXISTS,
            resolveSpaceStatus(isProfileOwner = false, hasOurProfile = false, provisioningAllowed = false, profileCount = 2),
        )
    }

    @Test
    fun noProfileAndNotAllowedIsUnsupported() {
        assertEquals(
            SpaceStatus.UNSUPPORTED,
            resolveSpaceStatus(isProfileOwner = false, hasOurProfile = false, provisioningAllowed = false, profileCount = 1),
        )
    }
}
