package com.example.evida

import java.security.MessageDigest

class HashManager {

    fun generateSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.fold("") { str, it -> str + "%02x".format(it) }
    }
}
