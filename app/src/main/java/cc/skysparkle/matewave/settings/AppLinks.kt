package cc.skysparkle.matewave.settings

import android.content.Context
import android.content.Intent
import android.net.Uri

/** External links of the app, kept in one place. */
object AppLinks {
    const val REPOSITORY = "https://git.skysparkle.cc/kalarina/Matewave"
    const val REPOSITORY_LABEL = "git.skysparkle.cc/kalarina/Matewave"
    const val PRIVACY_POLICY = "https://skysparkle.cc/privacy?lang=en"

    /** Opens a link in the browser; does nothing if no app can open it. */
    fun open(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
