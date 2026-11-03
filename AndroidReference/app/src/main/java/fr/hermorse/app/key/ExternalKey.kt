package fr.hermorse.app.key

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Manipulateur externe branché via le boîtier USB HerMorse (vu par Android comme un clavier) :
 * Ctrl gauche = contact « point » (ou pioche), Ctrl droit = contact « trait ».
 * Les touches [ et ] sont aussi acceptées.
 */
object ExternalKey {
    var leftDown by mutableStateOf(false)
        private set
    var rightDown by mutableStateOf(false)
        private set
    var seen by mutableStateOf(false)
        private set

    /** Reçoit (contact gauche ?, appuyé ?) ; défini par l'écran de manipulation actif. */
    var handler: ((Boolean, Boolean) -> Unit)? = null

    /** Renvoie true si l'événement vient du manipulateur (et ne doit pas aller ailleurs). */
    fun onKeyEvent(e: KeyEvent): Boolean {
        val left = when (e.keyCode) {
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_LEFT_BRACKET -> true
            KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_RIGHT_BRACKET -> false
            else -> return false
        }
        if (e.action != KeyEvent.ACTION_DOWN && e.action != KeyEvent.ACTION_UP) return true
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount > 0) return true
        val down = e.action == KeyEvent.ACTION_DOWN
        seen = true
        if (left) leftDown = down else rightDown = down
        handler?.invoke(left, down)
        return true
    }
}
