package cc.skysparkle.matewave.settings

import android.content.Context
import android.content.Intent
import android.net.Uri

/** External links of the app, kept in one place. */
object AppLinks {
    const val REPOSITORY = "https://github.com/kalarina-bit/Matewave"
    const val REPOSITORY_LABEL = "github.com/kalarina-bit/Matewave"
    const val DONATE = "https://ko-fi.com/skysparkle"
    const val DONATE_LABEL = "ko-fi.com/skysparkle"
    const val PRIVACY_POLICY = "https://skysparkle.cc/privacy?lang=en"

    /** Opens a link in the browser; does nothing if no app can open it. */
    fun open(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
