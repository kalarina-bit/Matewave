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
    }
}
