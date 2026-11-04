/*
 * SurvivorsUnited scoped access administration.
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package xaero.pac.common.server.claims.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import xaero.pac.OpenPartiesAndClaims;
import xaero.pac.common.player.config.group.api.PlayerConfigGroupActionError;
import xaero.pac.common.server.IServerData;
import xaero.pac.common.server.ServerData;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.player.config.IPlayerConfig;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;
import xaero.pac.common.server.player.config.group.custom.ICustomPlayerConfigGroup;

import java.util.Collection;

/**
 * A controlled group wrapper for the owner's MAIN configuration only.
 * This preserves the previous full-access group as a nested group.
 * Subclaim configurations remain unchanged.
 */
public final class SurvivorsUnitedClaimAccessCommand {
    private static final String GROUP = "su-admin-access";

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String action : new String[]{"grant", "revoke"}) {
            dispatcher.register(Commands.literal(ClaimsCommandRegister.COMMAND_PREFIX)
                .requires(c -> ServerConfig.CONFIG.claimsEnabled.get())
                .then(Commands.literal("admin")
                    .requires(c -> Commands.LEVEL_GAMEMASTERS.check(c.permissions()))
                    .then(Commands.literal("access")
                        .then(Commands.literal(action)
                            .then(Commands.argument("owner", GameProfileArgument.gameProfile())
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                    .then(Commands.literal("full")
                                        .then(Commands.literal("main")
                                            .executes(ctx -> {
                                                Collection<NameAndId> owners =
                                                    GameProfileArgument.getGameProfiles(ctx, "owner");
                                                Collection<NameAndId> players =
                                                    GameProfileArgument.getGameProfiles(ctx, "player");
                                                if (owners.size() != 1 || players.size() != 1) {
                                                    ctx.getSource().sendFailure(Component.literal("Specify exactly one owner and player"));
                                                    return 0;
                                                }
                                                NameAndId owner = owners.iterator().next();
                                                NameAndId player = players.iterator().next();
                                                IServerData<?, ?> data = ServerData.from(ctx.getSource().getServer());
                                                IPlayerConfig config = data.getPlayerConfigManager().getLoadedConfig(owner.id());
                                                if (config == null) {
                                                    ctx.getSource().sendFailure(Component.literal("Owner config unavailable"));
                                                    return 0;
                                                }
                                                var groupManager = config.getPlayerGroups();
                                                ICustomPlayerConfigGroup group = groupManager.getCustom(GROUP);
                                                if (action.equals("grant")) {
                                                    if (group == null) {
                                                        var created = groupManager.addCustomLimitedInternal(GROUP);
                                                        if (created.right().isPresent()) {
                                                            ctx.getSource().sendFailure(Component.literal("Cannot create group: " + created.right().get()));
                                                            return 0;
                                                        }
                                                        group = created.left().orElseThrow();
                                                        String prior = config.getEffective(PlayerConfigOptions.FULL_ACCESS);
                                                        if (!GROUP.equals(prior)) {
                                                            var inherited = group.includeGroupLimited(prior);
                                                            if (inherited.isPresent()) {
                                                                groupManager.removeCustom(GROUP);
                                                                ctx.getSource().sendFailure(Component.literal("Cannot preserve existing group: " + inherited.get()));
                                                                return 0;
                                                            }
                                                        }
                                                        var set = config.tryToSet(PlayerConfigOptions.FULL_ACCESS, GROUP);
                                                        if (set != xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI.SetResult.SUCCESS) {
                                                            groupManager.removeCustom(GROUP);
                                                            ctx.getSource().sendFailure(Component.literal("Full access is forced or not writable: " + set));
                                                            return 0;
                                                        }
                                                    } else if (!GROUP.equals(config.getEffective(PlayerConfigOptions.FULL_ACCESS))) {
                                                        ctx.getSource().sendFailure(Component.literal("Existing access group is not active; refusing to override claim settings"));
                                                        return 0;
                                                    }
                                                    var result = group.includeMemberLimitedInternal(player.id(), player.name(), true);
                                                    if (result.right().isPresent()) {
                                                        ctx.getSource().sendFailure(Component.literal("Cannot grant access: " + result.right().get()));
                                                        return 0;
                                                    }
                                                } else {
                                                    if (group == null || !GROUP.equals(config.getEffective(PlayerConfigOptions.FULL_ACCESS))) {
                                                        ctx.getSource().sendFailure(Component.literal("No active SurvivorsUnited access group for owner"));
                                                        return 0;
                                                    }
                                                    var error = group.excludeMember(player.id(), player.name());
                                                    if (error.isPresent()) {
                                                        ctx.getSource().sendFailure(Component.literal("Cannot revoke access: " + error.get()));
                                                        return 0;
                                                    }
                                                }
                                                OpenPartiesAndClaims.LOGGER.info("[SurvivorsUnited claim access] {} {} full main owner={} player={}",
                                                    ctx.getSource().getTextName(), action, owner.name(), player.name());
                                                ctx.getSource().sendSuccess(() -> Component.literal(action + " full access for " + player.name()
                                                    + " on " + owner.name() + "'s MAIN claim config (subclaims unchanged)"), true);
                                                return 1;
                                            })))))))));
        }
    }
}
