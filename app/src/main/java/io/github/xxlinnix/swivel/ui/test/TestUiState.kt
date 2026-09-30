package io.github.xxlinnix.swivel.ui.test

import io.github.xxlinnix.swivel.core.model.BatteryReading
import io.github.xxlinnix.swivel.core.model.ControllerSnapshot

/** One row of the raw-axes table. */
data class AxisRow(val label: String, val value: Float, val detail: String)

/** What the test screen shows, whichever mode the controller is in. */
data class TestUiState(
    val loading: Boolean = true,
    val connected: Boolean = false,
    val title: String? = null,
    /** Shown instead of the controls while not connected. */
    val status: String? = null,
    val canReconnect: Boolean = false,
    val summary: String = "",
    val details: List<String> = emptyList(),
    val snapshot: ControllerSnapshot = ControllerSnapshot(),
    val axes: List<AxisRow> = emptyList(),
    val events: List<String> = emptyList(),
    val battery: BatteryReading = BatteryReading.NotReported,
    /** The last Mode A report in hex. */
    val rawReport: String? = null,
    val hint: String? = null,
)
