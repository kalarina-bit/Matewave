package cc.skysparkle.matewave

import android.app.Application
import cc.skysparkle.matewave.data.FriendsStore
import cc.skysparkle.matewave.data.GameSaveStore
import cc.skysparkle.matewave.data.ProfileStore
import cc.skysparkle.matewave.settings.Haptics
import cc.skysparkle.matewave.stats.Stats

/**
 * Sets up the stores once per process, before any screen or the background presence service
 * runs: the service can be restarted by the system without the activity.
 */
class MatewaveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ProfileStore.init(this)
        FriendsStore.init(this)
        Stats.init(this)
        GameSaveStore.init(this)
        Haptics.init(this)

        // Settings files load on a background thread from first use; start them now, so the
        // first screen finds them in memory instead of waiting for the disk.
        for (name in EARLY_PREFS) getSharedPreferences(name, MODE_PRIVATE)
        GameSaveStore.preload()
    }

    private companion object {
        /** Settings the home screen and its view models read while starting. */
        val EARLY_PREFS = arrayOf(
            "chess_profile_prefs", "chess_friends_prefs", "chess_stats_prefs", "chess_sound_prefs",
            "last_game_setup", "presence_settings", "opening_srs"
        )
    }
}
