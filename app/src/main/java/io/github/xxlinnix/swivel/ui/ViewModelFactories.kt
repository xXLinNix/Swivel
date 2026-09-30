package io.github.xxlinnix.swivel.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.xxlinnix.swivel.SwivelApp
import io.github.xxlinnix.swivel.data.ControllerRepository
import io.github.xxlinnix.swivel.ui.home.HomeViewModel
import io.github.xxlinnix.swivel.ui.pairing.PairingViewModel
import io.github.xxlinnix.swivel.ui.test.ControllerTestViewModel
import io.github.xxlinnix.swivel.ui.test.ModeATestViewModel

/** Hands each view model the repository from the app container. */
object ViewModelFactories {
    val home = viewModelFactory {
        initializer { HomeViewModel(repository()) }
    }

    val pairing = viewModelFactory {
        initializer { PairingViewModel(repository()) }
    }

    val test = viewModelFactory {
        initializer {
            val descriptor = checkNotNull(createSavedStateHandle().get<String>(TEST_DESCRIPTOR_ARG))
            ControllerTestViewModel(descriptor, repository())
        }
    }

    val modeATest = viewModelFactory {
        initializer {
            val address = checkNotNull(createSavedStateHandle().get<String>(MODE_A_ADDRESS_ARG))
            ModeATestViewModel(address, repository())
        }
    }

    private fun CreationExtras.repository(): ControllerRepository =
        (checkNotNull(this[APPLICATION_KEY]) as SwivelApp).container.repository
}
