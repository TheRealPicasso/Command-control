package com.vincentporath.commandcontrol.util;

/**
 * Thread-local tracker for the current command being executed
 * This allows the permission mixin to know which command is being checked
 */
public class CommandExecutionTracker {
    
    private static final ThreadLocal<String> currentCommand = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> elevated = ThreadLocal.withInitial(() -> false);
    
    /**
     * Set the current command being executed
     */
    public static void setCurrentCommand(String command) {
        currentCommand.set(command);
    }
    
    /**
     * Get the current command being executed
     */
    public static String getCurrentCommand() {
        return currentCommand.get();
    }
    
    /**
     * Clear the current command (call after execution)
     */
    public static void clearCurrentCommand() {
        currentCommand.remove();
    }
    
    /**
     * Set whether we're currently in an elevated execution context
     * (to prevent infinite recursion)
     */
    public static void setElevated(boolean value) {
        elevated.set(value);
    }
    
    /**
     * Check if we're in an elevated execution context
     */
    public static boolean isElevated() {
        return elevated.get();
    }
}
