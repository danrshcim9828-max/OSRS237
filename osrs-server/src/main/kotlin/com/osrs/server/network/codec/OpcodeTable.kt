package com.osrs.server.network.codec

enum class ServerProt(val opcode: Int, val size: Int) {
    MESSAGE_GAME(67, -1),
    REBUILD_NORMAL(73, -2),
    UPDATE_STAT(46, 6),
    UPDATE_RUNORG(87, 1),
    UPDATE_RUNWEIGHT(120, 2),
    VARP_SMALL(62, 3),
    VARP_LARGE(2, 6),
    LOGOUT_FULL(96, 0),
    SERVER_TICK_END(97, 0),
    ;

    companion object {
        private val byOpcode = values().associateBy { it.opcode }
        fun fromOpcode(opcode: Int): ServerProt? = byOpcode[opcode]
        fun validateNoDuplicates(): List<String> {
            val seen = mutableMapOf<Int, MutableList<String>>()
            for (prot in values()) {
                seen.getOrPut(prot.opcode) { mutableListOf() }.add(prot.name)
            }
            return seen.filter { it.value.size > 1 }
                .map { (opcode, names) -> "Opcode $opcode conflict: ${names.joinToString(", ")}" }
        }
    }
}

enum class ClientProt(val opcode: Int, val size: Int) {
    PING(5, 0),
    WINDOW_STATUS(22, 5),
    MOVE_GAMECLICK(39, 18),
    MOVE_MINIMAPCLICK(49, 18),
    CHAT_SEND_PUBLIC(78, -1),
    CHAT_COMMAND(71, -1),
    CLOSE_MODAL(21, 0),
    ;

    companion object {
        private val byOpcode = values().associateBy { it.opcode }
        fun fromOpcode(opcode: Int): ClientProt? = byOpcode[opcode]
        fun validateNoDuplicates(): List<String> {
            val seen = mutableMapOf<Int, MutableList<String>>()
            for (prot in values()) {
                seen.getOrPut(prot.opcode) { mutableListOf() }.add(prot.name)
            }
            return seen.filter { it.value.size > 1 }
                .map { (opcode, names) -> "Opcode $opcode conflict: ${names.joinToString(", ")}" }
        }
    }
}
