/**
 * Minimal builtin_init for Android builds.
 *
 * On Android, module plugin_init functions are disabled (behind #ifdef PLUGSSS)
 * and modules are registered via direct function calls instead of the plugin system.
 * This file provides the required builtin_init symbol as a no-op.
 */

extern void builtin_init(void);

void builtin_init(void)
{
}
