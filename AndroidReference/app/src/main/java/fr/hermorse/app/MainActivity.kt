package fr.hermorse.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.hermorse.app.audio.ToneEngine
import fr.hermorse.app.data.Store
import fr.hermorse.app.key.ExternalKey
import fr.hermorse.app.ui.HerMorseApp

class MainActivity : ComponentActivity() {

    private lateinit var tone: ToneEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tone = ToneEngine(applicationContext)
        val store = Store(applicationContext)
        setContent {
            HerMorseApp(store, tone)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (ExternalKey.onKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        tone.start()
    }

    override fun onPause() {
        tone.stop()
        super.onPause()
    }
}
