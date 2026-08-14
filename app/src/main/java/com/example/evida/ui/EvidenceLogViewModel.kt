package com.example.evida.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.evida.data.LogRepository
import com.example.evida.data.local.EvidenceLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class EvidenceLogViewModel(logRepository: LogRepository) : ViewModel() {

    val evidenceLogs: StateFlow<List<EvidenceLog>> = logRepository.getAllLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )
}
