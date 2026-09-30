package io.github.xxlinnix.swivel.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The Activity behind a Compose LocalContext, which may be wrapped. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
