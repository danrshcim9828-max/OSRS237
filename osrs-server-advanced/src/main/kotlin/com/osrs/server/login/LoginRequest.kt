package com.osrs.server.login

data class LoginRequest(
    val username: String,
    val password: String,
    val xteaKeys: IntArray,
    val lowMemory: Boolean = false,
    val reconnect: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LoginRequest) return false
        return username == other.username
    }
    override fun hashCode(): Int = username.hashCode()
}
