package xaero.pac.common.server.parties.command;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import xaero.pac.common.server.parties.command.SurvivorsUnitedAdminPolicy.Action;

class SurvivorsUnitedAdminPolicyTest {
    private String validate(Action a, boolean same, boolean other, boolean owner,
                            boolean full, boolean invitationsFull, boolean invited) {
        return SurvivorsUnitedAdminPolicy.validate(a, same, other, owner, full, invitationsFull, invited);
    }

    @Test void addAllowsUnassignedPlayer() {
        assertNull(validate(Action.ADD, false, false, false, false, false, false));
    }
    @Test void addRejectsCrossPartyStealing() {
        assertNotNull(validate(Action.ADD, false, true, false, false, false, false));
    }
    @Test void addRejectsDuplicateMember() {
        assertNotNull(validate(Action.ADD, true, false, false, false, false, false));
    }
    @Test void addRejectsFullDestination() {
        assertNotNull(validate(Action.ADD, false, false, false, true, false, false));
    }
    @Test void moveRequiresExistingMembership() {
        assertNotNull(validate(Action.MOVE, false, false, false, false, false, false));
    }
    @Test void moveDoesNotOrphanOwnerParty() {
        assertNotNull(validate(Action.MOVE, false, true, true, false, false, false));
    }
    @Test void moveAllowsOrdinaryMemberToNewParty() {
        assertNull(validate(Action.MOVE, false, true, false, false, false, false));
    }
    @Test void moveRejectsFullDestination() {
        assertNotNull(validate(Action.MOVE, false, true, false, true, false, false));
    }
    @Test void removeRejectsOwner() {
        assertNotNull(validate(Action.REMOVE, true, false, true, false, false, false));
    }
    @Test void removeAllowsOrdinaryMember() {
        assertNull(validate(Action.REMOVE, true, false, false, false, false, false));
    }
    @Test void inviteRejectsCurrentMember() {
        assertNotNull(validate(Action.INVITE, false, true, false, false, false, false));
    }
    @Test void inviteRejectsExistingInvitation() {
        assertNotNull(validate(Action.INVITE, false, false, false, false, false, true));
    }
    @Test void inviteRejectsCapacityLimits() {
        assertNotNull(validate(Action.INVITE, false, false, false, false, true, false));
        assertNotNull(validate(Action.INVITE, false, false, false, true, false, false));
    }
    @Test void inviteAllowsValidPlayer() {
        assertNull(validate(Action.INVITE, false, false, false, false, false, false));
    }
}
