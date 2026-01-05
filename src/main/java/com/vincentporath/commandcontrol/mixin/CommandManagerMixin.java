package com.vincentporath.commandcontrol.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import com.vincentporath.commandcontrol.CommandControl;
import com.vincentporath.commandcontrol.config.CommandControlConfig;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.ArgumentTypes;
import net.minecraft.network.packet.s2c.play.CommandTreeS2CPacket;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.mojang.brigadier.ParseResults;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Server-side mixin to filter command suggestions and block unauthorized commands
 */
@Mixin(CommandManager.class)
public abstract class CommandManagerMixin {

    @Shadow @Final private CommandDispatcher<ServerCommandSource> dispatcher;

    /**
     * Filter command suggestions sent to players.
     * We use an elevated source so that vanilla's makeCommandTree includes full argument structure,
     * but filter based on our config.
     */
    @Inject(method = "sendCommandTree", at = @At("HEAD"), cancellable = true)
    private void commandcontrol$filterCommandTree(ServerPlayerEntity player, CallbackInfo ci) {
        // OP level 4 sees all commands
        if (player.hasPermissionLevel(4)) {
            return;
        }
        
        try {
            // Use elevated source so vanilla includes full argument structure
            ServerCommandSource elevatedSource = player.getCommandSource().withLevel(4);
            
            // Build command tree using vanilla method with elevated permissions
            Map<CommandNode<ServerCommandSource>, CommandNode<CommandSource>> visitedNodes = new IdentityHashMap<>();
            RootCommandNode<CommandSource> resultRoot = new RootCommandNode<>();
            visitedNodes.put(this.dispatcher.getRoot(), resultRoot);
            
            // Process each top-level command
            for (CommandNode<ServerCommandSource> child : this.dispatcher.getRoot().getChildren()) {
                String commandName = child.getName().toLowerCase();
                
                // Check if command is allowed for this player via our config
                if (CommandControlConfig.isCommandAllowed(player, commandName)) {
                    // Use elevated source for building tree so all arguments are included
                    buildFilteredTree(child, resultRoot, elevatedSource, visitedNodes);
                }
            }
            
            // Send filtered packet
            player.networkHandler.sendPacket(new CommandTreeS2CPacket(resultRoot));
            ci.cancel();
            
        } catch (Exception e) {
            CommandControl.LOGGER.error("[CommandControls] Error filtering command tree", e);
            // Fall back to vanilla behavior on error
        }
    }
    
    /**
     * Block execution of unauthorized commands
     * Note: This is a safety check - the command tree filtering should already hide unauthorized commands
     */
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true)
    private void commandcontrol$blockUnauthorizedCommand(ParseResults<ServerCommandSource> parseResults, String command, CallbackInfoReturnable<Integer> cir) {
        ServerCommandSource source = parseResults.getContext().getSource();
        
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            // Check the SOURCE's permission level (which may have been elevated by our mod)
            // If source has level 4, it means either they're OP or we granted permission
            if (source.hasPermissionLevel(4)) {
                return;
            }
            
            // Extract base command
            String baseCommand = command.split(" ")[0].toLowerCase();
            if (baseCommand.startsWith("/")) {
                baseCommand = baseCommand.substring(1);
            }
            
            // Check if command is allowed - if so, don't block
            // (This handles cases where the source level wasn't elevated for some reason)
            if (CommandControlConfig.isCommandAllowed(player, baseCommand)) {
                return;
            }
            
            player.sendMessage(Text.literal("§c[CommandControls] You do not have permission for this command."), false);
            cir.setReturnValue(0);
        }
    }
    
    /**
     * Recursively build filtered command tree
     * Uses elevated source so all children pass canUse() checks
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void buildFilteredTree(
            CommandNode<ServerCommandSource> node,
            CommandNode<CommandSource> parent,
            ServerCommandSource elevatedSource,
            Map<CommandNode<ServerCommandSource>, CommandNode<CommandSource>> visitedNodes
    ) {
        CommandNode<CommandSource> existingNode = visitedNodes.get(node);
        if (existingNode != null) {
            parent.addChild(existingNode);
            return;
        }
        
        // Check if node can be used with elevated permissions
        // This ensures complex permission structures are respected
        if (!node.canUse(elevatedSource)) {
            return;
        }
        
        CommandNode<CommandSource> newNode = createNodeCopy(node, visitedNodes);
        if (newNode == null) {
            return;
        }
        
        visitedNodes.put(node, newNode);
        parent.addChild(newNode);
        
        // Process ALL children with elevated permissions
        for (CommandNode<ServerCommandSource> child : node.getChildren()) {
            buildFilteredTree(child, newNode, elevatedSource, visitedNodes);
        }
        
        // Handle redirect (important for commands like /tp which use redirects)
        if (node.getRedirect() != null) {
            CommandNode<ServerCommandSource> redirect = node.getRedirect();
            CommandNode<CommandSource> redirectCopy = visitedNodes.get(redirect);
            
            if (redirectCopy == null) {
                // Redirect target not yet processed, process it now
                buildFilteredTree(redirect, parent, elevatedSource, visitedNodes);
                redirectCopy = visitedNodes.get(redirect);
            }
            
            // Note: We can't easily set redirect on the copy since Brigadier nodes are immutable
            // The redirect will be handled through the children we've already copied
        }
    }
    
    /**
     * Create a CommandSource copy of a ServerCommandSource node
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private CommandNode<CommandSource> createNodeCopy(
            CommandNode<ServerCommandSource> node,
            Map<CommandNode<ServerCommandSource>, CommandNode<CommandSource>> visitedNodes
    ) {
        // Handle redirect - find or create the redirect target
        CommandNode<CommandSource> redirectTarget = null;
        if (node.getRedirect() != null) {
            redirectTarget = visitedNodes.get(node.getRedirect());
        }
        
        if (node instanceof LiteralCommandNode literal) {
            LiteralCommandNode<CommandSource> newNode = new LiteralCommandNode<>(
                    literal.getLiteral(),
                    null,  // command
                    s -> true,  // always allow
                    redirectTarget,
                    null,  // redirect modifier - not needed for client tree
                    literal.isFork()
            );
            return newNode;
        } else if (node instanceof ArgumentCommandNode argument) {
            ArgumentType<?> type = argument.getType();
            
            // Check if argument type is serializable
            if (ArgumentTypes.getArgumentTypeProperties(type) == null) {
                return null;
            }
            
            return new ArgumentCommandNode<>(
                    argument.getName(),
                    type,
                    null,  // command
                    s -> true,  // always allow
                    redirectTarget,
                    null,  // redirect modifier - not needed for client tree
                    argument.isFork(),
                    argument.getCustomSuggestions()
            );
        }
        
        return null;
    }
}
