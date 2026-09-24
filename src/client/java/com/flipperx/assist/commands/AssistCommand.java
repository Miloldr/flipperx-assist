package com.flipperx.assist.commands;

import com.flipperx.assist.AssistClient;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

public final class AssistCommand {
    private AssistCommand() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("assist")
                .then(ClientCommands.literal("hud").then(ClientCommands.literal("reset").executes(ctx -> {
                    AssistClient.get().resetHud();
                    return 1;
                })))
                .then(ClientCommands.literal("login").executes(ctx -> {
                    AssistClient.get().requestLink();
                    return 1;
                }))
                .then(ClientCommands.literal("logout").executes(ctx -> {
                    AssistClient.get().logout();
                    return 1;
                }))
                .then(ClientCommands.literal("start").executes(ctx -> {
                    AssistClient.get().start();
                    return 1;
                }))
                .then(ClientCommands.literal("stop").executes(ctx -> {
                    AssistClient.get().stop();
                    return 1;
                }))
                .then(ClientCommands.literal("goal")
                        .executes(ctx -> {
                            AssistClient.get().goal("");
                            return 1;
                        })
                        .then(ClientCommands.argument("text", StringArgumentType.greedyString()).executes(ctx -> {
                            AssistClient.get().goal(StringArgumentType.getString(ctx, "text"));
                            return 1;
                        })))
                .then(ClientCommands.literal("summary").executes(ctx -> {
                    AssistClient.get().showSummary();
                    return 1;
                }))
                .executes(ctx -> {
                    AssistClient.get().printHelp();
                    return 1;
                }));
    }
}
