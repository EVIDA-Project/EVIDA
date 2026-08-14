package com.example.evida.data

import com.example.evida.data.local.EvidenceLog
import com.example.evida.data.local.EvidenceLogDao
import kotlinx.coroutines.flow.Flow

class LogRepository(private val evidenceLogDao: EvidenceLogDao) {

    fun getAllLogs(): Flow<List<EvidenceLog>> = evidenceLogDao.getAllLogs()

    suspend fun insertLog(log: EvidenceLog) {
        evidenceLogDao.insertLog(log)
    }
}
