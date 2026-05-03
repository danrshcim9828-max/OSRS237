package com.osrs.server.login

data class LoginRequest(
    val username: String,
    val password: String,
    val xteaKeys: IntArray,
    val lowMemory: Boolean = false,
    val reconnect: Boolean = false
)
