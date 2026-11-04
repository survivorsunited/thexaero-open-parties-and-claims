/*
 * SurvivorsUnited membership validation (pure Java for regression tests).
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package xaero.pac.common.server.parties.command;

final class SurvivorsUnitedAdminPolicy {
    enum Action { ADD, REMOVE, MOVE, INVITE }

    private SurvivorsUnitedAdminPolicy() {}

    /**
     * Returns a user-facing rejection reason, or null when an action is allowed.
     * Owner parties must never be orphaned through an admin membership command.
     */
    static String validate(Action action, boolean inDestination, boolean inOther,
                           boolean targetIsOwner, boolean destinationFull,
                           boolean invitationFull, boolean alreadyInvited) {
        return switch (action) {
            case REMOVE -> {
                if (!inDestination) yield "Player is not a member of destination party";
                if (targetIsOwner) yield "Cannot remove a party owner";
                yield null;
            }
            case INVITE -> {
                if (inDestination || inOther) yield "Player already belongs to a party";
                if (invitationFull) yield "Party invitation limit reached";
                if (destinationFull) yield "Party membership limit reached";
                if (alreadyInvited) yield "Player has already been invited";
                yield null;
            }
            case ADD -> {
                if (inDestination) yield "Player is already in this party";
                if (inOther) yield "Player belongs to another party. Use admin move";
                if (destinationFull) yield "Destination party is full";
                yield null;
            }
            case MOVE -> {
                if (inDestination) yield "Player is already in this party";
                if (!inOther) yield "Player has no existing party. Use admin add";
                if (targetIsOwner) yield "Cannot move a party owner";
                if (destinationFull) yield "Destination party is full";
                yield null;
            }
        };
    }
}
