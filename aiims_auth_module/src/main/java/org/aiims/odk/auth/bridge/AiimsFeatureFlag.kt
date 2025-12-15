package org.aiims.odk.auth.bridge

import android.content.Context
import android.content.SharedPreferences
import org.aiims.odk.auth.utils.AiimsConstants

/**
 * Manages AIIMS authentication feature flags.
 *
 * This class controls whether AIIMS authentication is enabled and allows
 * runtime toggling of authentication features without code changes.
 */
object AiimsFeatureFlag {

    /**
     * Check if AIIMS authentication is enabled.
     *
     * @param context Application context
     * @return true if AIIMS authentication is enabled
     */
    fun isEnabled(context: Context): Boolean {
        return getPreferences(context)
            .getBoolean(AiimsConstants.KEY_AIIMS_AUTH_ENABLED, false)
    }

    /**
     * Enable or disable AIIMS authentication.
     *
     * @param context Application context
     * @param enabled true to enable authentication
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        getPreferences(context)
            .edit()
            .putBoolean(AiimsConstants.KEY_AIIMS_AUTH_ENABLED, enabled)
            .apply()
    }

    /**
     * Check if debug mode is enabled (shows additional info and logs).
     *
     * @param context Application context
     * @return true if debug mode is enabled
     */
    fun isDebugEnabled(context: Context): Boolean {
        return getPreferences(context)
            .getBoolean(AiimsConstants.KEY_DEBUG_MODE, false)
    }

    /**
     * Enable or disable debug mode.
     *
     * @param context Application context
     * @param enabled true to enable debug mode
     */
    fun setDebugEnabled(context: Context, enabled: Boolean) {
        getPreferences(context)
            .edit()
            .putBoolean(AiimsConstants.KEY_DEBUG_MODE, enabled)
            .apply()
    }

    /**
     * Check if this is the first launch with AIIMS authentication.
     *
     * @param context Application context
     * @return true if this is the first launch
     */
    fun isFirstLaunch(context: Context): Boolean {
        return getPreferences(context)
            .getBoolean(AiimsConstants.KEY_FIRST_LAUNCH, true)
    }

    /**
     * Mark first launch as completed.
     *
     * @param context Application context
     */
    fun setFirstLaunchComplete(context: Context) {
        getPreferences(context)
            .edit()
            .putBoolean(AiimsConstants.KEY_FIRST_LAUNCH, false)
            .apply()
    }

    /**
     * Get configuration from build-time Gradle properties.
     *
     * @param context Application context
     * @return Map of configuration values
     */
    fun getBuildConfig(context: Context): Map<String, Any> {
        return mapOf(
            "enabled" to isEnabled(context),
            "debug" to isDebugEnabled(context),
            "version" to BuildConfig.VERSION_NAME,
            "buildType" to BuildConfig.BUILD_TYPE,
            "timestamp" to System.currentTimeMillis()
        )
    }

    /**
     * Update feature flags from remote configuration.
     *
     * This method can be called to update feature flags from a remote
     * configuration server, enabling A/B testing and remote control.
     *
     * @param context Application context
     * @param remoteConfig Map of remote configuration values
     */
    fun updateFromRemoteConfig(context: Context, remoteConfig: Map<String, Any>) {
        val editor = getPreferences(context).edit()

        remoteConfig.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                else -> {
                    // Log warning for unsupported type
                    if (isDebugEnabled(context)) {
                        android.util.Log.w(
                            "AiimsFeatureFlag",
                            "Unsupported config type for key: $key"
                        )
                    }
                }
            }
        }

        editor.apply()
    }

    /**
     * Reset all feature flags to default values.
     *
     * @param context Application context
     */
    fun resetToDefaults(context: Context) {
        getPreferences(context)
            .edit()
            .clear()
            .apply()
    }

    /**
     * Get SharedPreferences instance for feature flags.
     */
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(
            "aiims_feature_flags",
            Context.MODE_PRIVATE
        )
    }
}