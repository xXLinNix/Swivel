package io.github.xxlinnix.swivel

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.xxlinnix.swivel.ui.SwivelNavHost
import io.github.xxlinnix.swivel.ui.theme.SwivelTheme

class MainActivity : ComponentActivity() {
    private val input get() = (application as SwivelApp).container.input

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SwivelTheme {
                SwivelNavHost()
            }
        }
    }

    /**
     * Controller buttons arrive here first. They are recorded for the test screen, and
     * swallowed while it is open so B does not act as Back.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val fromGamepad = input.onKeyEvent(event)
        if (fromGamepad && input.captureAll) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val fromGamepad = input.onMotionEvent(event)
        if (fromGamepad && input.captureAll) return true
        return super.dispatchGenericMotionEvent(event)
    }
}
