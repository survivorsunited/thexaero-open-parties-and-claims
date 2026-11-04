/*
 * SurvivorsUnited administration extensions for Open Parties and Claims.
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package xaero.pac.common.server.parties.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.common.parties.party.IPartyPlayerInfo;
import xaero.pac.common.parties.party.ally.IPartyAlly;
import xaero.pac.common.parties.party.member.IPartyMember;
import xaero.pac.common.server.IServerData;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.parties.party.IPartyManager;
import xaero.pac.common.server.parties.party.IServerParty;

import java.util.Collection;
import java.util.UUID;

/**
 * OP/console commands deliberately use real UUIDs, never impersonation.
 * Each mutation uses the existing managed party APIs to preserve indexes,
 * dirty tracking and synchronisation.
 */
public final class SurvivorsUnitedPartyAdminCommand {
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String action : new String[]{"info", "add", "remove", "invite", "move"}) {
            var owner = Commands.argument("owner", GameProfileArgument.gameProfile());
            if (!action.equals("info")) {
                owner.then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> execute(ctx, action)));
            } else {
                owner.executes(ctx -> execute(ctx, action));
            }
            dispatcher.register(Commands.literal(PartyCommandRegister.COMMAND_PREFIX)
                    .requires(c -> ServerConfig.CONFIG.partiesEnabled.get())
                    .then(Commands.literal("admin")
                            .requires(c -> Commands.LEVEL_GAMEMASTERS.check(c.permissions()))
                            .then(Commands.literal(action).then(owner))));
        }
    }

    private NameAndId single(CommandContext<CommandSourceStack> context, String key)
            throws CommandSyntaxException {
        Collection<NameAndId> profiles = GameProfileArgument.getGameProfiles(context, key);
        if (profiles.size() != 1)
            throw new IllegalArgumentException("Expected exactly one profile for " + key);
        return profiles.iterator().next();
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }

    private int execute(CommandContext<CommandSourceStack> context, String action)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        NameAndId owner;
        NameAndId player = null;
        try {
            owner = single(context, "owner");
            if (!action.equals("info"))
                player = single(context, "player");
        } catch (IllegalArgumentException e) {
            return fail(source, e.getMessage());
        }

        IServerData<?, IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly>> data =
                ServerData.from(server);
        IPartyManager<IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly>> manager =
                data.getPartyManager();
        IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly> destination =
                manager.getPartyByOwner(owner.id());
        if (destination == null)
            return fail(source, "No party is owned by " + owner.name());

        if (action.equals("info")) {
            source.sendSuccess(() -> Component.literal("Party " + destination.getDefaultName()
                    + " (" + destination.getId() + "), members "
                    + destination.getMemberCount() + "/" + ServerConfig.CONFIG.maxPartyMembers.get()), false);
            return 1;
        }

        UUID targetId = player.id();
        IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly> current =
                manager.getPartyByMember(targetId);

        if (action.equals("remove")) {
            if (destination != current)
                return fail(source, player.name() + " is not a member of " + owner.name() + "'s party");
            if (destination.getOwner().getUUID().equals(targetId))
                return fail(source, "Cannot remove a party owner; transfer ownership first");
            if (destination.removeMember(targetId) == null)
                return fail(source, "Could not remove member");
        } else if (action.equals("invite")) {
            if (current != null)
                return fail(source, player.name() + " already belongs to a party");
            if (destination.getInviteCount() >= ServerConfig.CONFIG.maxPartyInvites.get())
                return fail(source, "Party invitation limit reached");
            if (destination.getMemberCount() >= ServerConfig.CONFIG.maxPartyMembers.get())
                return fail(source, "Party membership limit reached");
            if (destination.isInvited(targetId))
                return fail(source, "Player has already been invited");
            if (destination.invitePlayer(targetId, player.name()) == null)
                return fail(source, "Could not create invitation");
            ServerPlayer online = server.getPlayerList().getPlayer(targetId);
            if (online != null)
                online.sendSystemMessage(Component.literal("Invited to " + destination.getDefaultName()
                        + ". Accept with /" + PartyCommandRegister.COMMAND_PREFIX
                        + " accept " + destination.getId()));
        } else {
            if (current == destination)
                return fail(source, player.name() + " is already in this party");
            if (current != null && action.equals("add"))
                return fail(source, "Player belongs to another party. Use admin move explicitly");
            if (current == null && action.equals("move"))
                return fail(source, "Player has no existing party. Use admin add");
            if (current != null && current.getOwner().getUUID().equals(targetId))
                return fail(source, "Cannot move a party owner; transfer ownership first");
            if (destination.getMemberCount() >= ServerConfig.CONFIG.maxPartyMembers.get())
                return fail(source, "Destination party is full");
            if (current != null)
                current.removeMember(targetId);
            if (destination.addMember(targetId, null, player.name()) == null) {
                if (current != null)
                    current.addMember(targetId, null, player.name()); // best-effort rollback
                return fail(source, "Could not add member");
            }
        }

        if (!action.equals("invite")) {
            ServerPlayer online = server.getPlayerList().getPlayer(targetId);
            if (online != null) {
                data.getPlayerPermissionChangeHandler().sendCommandsAndUpdatePermissions(online, data, false);
                online.sendSystemMessage(Component.literal("Your party membership was updated by a server admin."));
            }
        }
        String audit = "[SurvivorsUnited party admin] " + source.getTextName() + " " + action
                + " owner=" + owner.name() + "(" + owner.id() + ")"
                + " player=" + player.name() + "(" + player.id() + ")";
        OpenPartiesAndClaims.LOGGER.info(audit);
        source.sendSuccess(() -> Component.literal("Success: " + audit), true);
        return 1;
    }
}
