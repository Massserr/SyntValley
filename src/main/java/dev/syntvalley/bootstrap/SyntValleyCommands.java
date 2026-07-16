package dev.syntvalley.bootstrap;

import com.mojang.brigadier.context.CommandContext;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator-only debug surface. Slice 10 exposes the LLM boundary's health and a safe diagnostic
 * generation; the command never mutates gameplay state.
 */
public final class SyntValleyCommands {
    private SyntValleyCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("syntvalley")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("ai")
                        .then(Commands.literal("status")
                                .executes(context -> reply(context, SyntValleyServerRuntime::aiStatus)))
                        .then(Commands.literal("ping")
                                .executes(context -> reply(context, SyntValleyServerRuntime::aiPing)))));
    }

    private static int reply(
            CommandContext<CommandSourceStack> context, Function<SyntValleyServerRuntime, String> query) {
        SyntValleyServerRuntime runtime = ServerRuntimeManager.getOrCreate(context.getSource().getServer());
        String text = query.apply(runtime);
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
