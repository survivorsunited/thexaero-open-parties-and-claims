/*
 * SurvivorsUnited party invitation discovery and decline commands.
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package xaero.pac.common.server.parties.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.parties.party.IPartyPlayerInfo;
import xaero.pac.common.parties.party.ally.IPartyAlly;
import xaero.pac.common.parties.party.member.IPartyMember;
import xaero.pac.common.server.IServerData;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.parties.party.IServerParty;

import java.util.UUID;
import java.util.stream.Stream;

public final class SurvivorsUnitedInvitationsCommand {
    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(PartyCommandRegister.COMMAND_PREFIX)
            .requires(c -> ServerConfig.CONFIG.partiesEnabled.get())
            .then(Commands.literal("invites").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                IServerData<?, IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly>> data =
                        ServerData.from(ctx.getSource().getServer());
                var invites = data.getPartyManager().getTypedAllStream()
                        .filter(p -> p.isInvited(player.getUUID())).toList();
                if (invites.isEmpty()) {
                    player.sendSystemMessage(Component.literal("You have no pending party invitations."));
                    return 1;
                }
                for (var party : invites) {
                    String id = party.getId().toString();
                    Component accept = Component.literal("[Accept]")
                        .withStyle(style -> style.withClickEvent(
                            new ClickEvent.RunCommand("/" + PartyCommandRegister.COMMAND_PREFIX + " accept " + id)));
                    player.sendSystemMessage(Component.literal("Invitation from "
                        + party.getOwner().getUsername() + " (" + party.getDefaultName() + ") ")
                        .append(accept));
                }
                return invites.size();
            }))
            .then(Commands.literal("decline")
                .then(Commands.argument("party", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        IServerData<?, IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly>> data =
                            ServerData.from(ctx.getSource().getServer());
                        Stream<String> valid = data.getPartyManager().getTypedAllStream()
                            .filter(p -> p.isInvited(player.getUUID()))
                            .flatMap(p -> Stream.of(p.getId().toString(), p.getOwner().getUsername()));
                        return SharedSuggestionProvider.suggest(valid, builder);
                    })
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        String supplied = StringArgumentType.getString(ctx, "party");
                        IServerData<?, IServerParty<IPartyMember, IPartyPlayerInfo, IPartyAlly>> data =
                            ServerData.from(ctx.getSource().getServer());
                        var matching = data.getPartyManager().getTypedAllStream()
                            .filter(p -> p.isInvited(player.getUUID()))
                            .filter(p -> p.getId().toString().equalsIgnoreCase(supplied)
                                || p.getOwner().getUsername().equalsIgnoreCase(supplied)
                                || p.getDefaultName().equalsIgnoreCase(supplied))
                            .limit(2).toList();
                        if (matching.size() != 1) {
                            ctx.getSource().sendFailure(Component.literal("No unique pending invitation matches '" + supplied + "'."));
                            return 0;
                        }
                        matching.getFirst().uninvitePlayer(player.getUUID());
                        ctx.getSource().sendSuccess(() -> Component.literal("Declined invitation from "
                            + matching.getFirst().getOwner().getUsername()), false);
                        return 1;
                    }))));
    }
}
