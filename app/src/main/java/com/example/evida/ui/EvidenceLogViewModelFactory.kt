package com.example.evida.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.evida.data.LogRepository

class EvidenceLogViewModelFactory(private val logRepository: LogRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EvidenceLogViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EvidenceLogViewModel(logRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
