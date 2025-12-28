package com.example.reglia;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Commands for Reglia Discord Bridge.
 */
public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /setwebhook <url>
        dispatcher.register(Commands.literal("setwebhook")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("url", StringArgumentType.greedyString())
                        .executes(ModCommands::setWebhook)));

        // /discord status|test|toggle
        dispatcher.register(Commands.literal("discord")
                .then(Commands.literal("status")
                        .executes(ModCommands::status))
                .then(Commands.literal("test")
                        .requires(source -> source.hasPermission(2))
                        .executes(ModCommands::testWebhook))
                .then(Commands.literal("toggle")
                        .requires(source -> source.hasPermission(2))
                        .executes(ModCommands::toggle)));
    }

    private static int setWebhook(CommandContext<CommandSourceStack> context) {
        String url = StringArgumentType.getString(context, "url");

        if (!url.startsWith("https://discord.com/api/webhooks/") &&
                !url.startsWith("https://discordapp.com/api/webhooks/")) {
            context.getSource().sendFailure(Component.literal("§cInvalid webhook URL!"));
            return 0;
        }

        Config.setWebhookUrl(url);
        context.getSource().sendSuccess(() -> Component.literal("§aWebhook set successfully!"), true);
        DiscordWebhook.sendMessage("✅ Reglia Discord Bridge connected!", "Server");

        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        if (Config.hasWebhook()) {
            String status = Config.bridgeEnabled ? "§aEnabled" : "§cDisabled";
            context.getSource().sendSuccess(() -> Component.literal(
                    "§6[Reglia] §fDiscord Bridge: " + status + "\n§7Webhook: §aConfigured"), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal(
                    "§6[Reglia] §cNo webhook configured\n§7Use: §e/setwebhook <url>"), false);
        }
        return 1;
    }

    private static int testWebhook(CommandContext<CommandSourceStack> context) {
        if (!Config.hasWebhook()) {
            context.getSource().sendFailure(Component.literal("§cNo webhook configured!"));
            return 0;
        }

        boolean success = DiscordWebhook.sendMessage("🧪 Test message from Reglia!", "Server");

        if (success) {
            context.getSource().sendSuccess(() -> Component.literal("§aTest message sent!"), false);
        } else {
            context.getSource().sendFailure(Component.literal("§cFailed to send."));
        }

        return success ? 1 : 0;
    }

    private static int toggle(CommandContext<CommandSourceStack> context) {
        Config.bridgeEnabled = !Config.bridgeEnabled;
        String status = Config.bridgeEnabled ? "§aenabled" : "§cdisabled";
        context.getSource().sendSuccess(() -> Component.literal("§6[Reglia] §fBridge " + status), true);
        return 1;
    }
}
