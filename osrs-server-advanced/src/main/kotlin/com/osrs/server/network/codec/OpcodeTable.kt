package com.osrs.server.network.codec

/**
 * RSProt Revision 237 Opcode Alignment
 *
 * These opcode tables are aligned with RSProt's ServerProt and ClientProt
 * enumerations for OSRS revision 237. RSProt uses a typed packet system —
 * each packet class corresponds to an opcode entry here.
 *
 * Server→Client (ServerProt): packets sent FROM the server TO the client.
 * Client→Server (ClientProt): packets sent FROM the client TO the server.
 *
 * Opcode values are the raw byte values sent over the wire.
 * Size = -1 means the packet is variable-byte (prefixed with 1-byte length).
 * Size = -2 means the packet is variable-short (prefixed with 2-byte length).
 *
 * These align with rsprot/protocol/game/outgoing and incoming packet definitions.
 */

enum class ServerProt(val opcode: Int, val size: Int) {

    // -------------------------------------------------------------------------
    // Player / Entity Updates
    // -------------------------------------------------------------------------
    PLAYER_INFO(79, -2),                     // Full player update block
    NPC_INFO_SMALL_VIEWPORT(61, -2),         // NPC info (small viewport)
    NPC_INFO_LARGE_VIEWPORT(32, -2),         // NPC info (large viewport)

    // -------------------------------------------------------------------------
    // Map / Scene
    // -------------------------------------------------------------------------
    REBUILD_NORMAL(73, -2),                  // Full map rebuild (normal)
    REBUILD_REGION(3, -2),                   // Dynamic/instanced map rebuild
    MAP_ANIM(55, 3),                         // Ground animation
    MAP_PROJANIM(56, 18),                    // Projectile animation on ground
    OBJ_ADD(26, 10),                         // Add ground item
    OBJ_DEL(76, 6),                          // Remove ground item
    OBJ_COUNT(15, 10),                       // Update ground item stack count
    LOC_ADD_CHANGE(114, 7),                  // Add/change a loc (object)
    LOC_DEL(12, 3),                          // Delete a loc
    LOC_ANIM(85, 5),                         // Animate a loc

    // -------------------------------------------------------------------------
    // Interface / Client Scripts
    // -------------------------------------------------------------------------
    IF_OPENTOP(30, 2),                       // Open top-level interface
    IF_OPENSUB(82, 8),                       // Open sub interface
    IF_CLOSESUB(92, 4),                      // Close sub interface
    IF_MOVESUB(52, 8),                       // Move sub interface
    IF_SETANGLE(8, 10),                      // Set 3D model angle in interface
    IF_SETANIM(38, 8),                       // Set animation on interface
    IF_SETCOLOUR(60, 6),                     // Set colour on interface
    IF_SETHIDE(70, 5),                       // Set hidden state
    IF_SETMODEL(4, 8),                       // Set model on interface
    IF_SETNPCHEAD(34, 8),                    // Set NPC head on interface
    IF_SETOBJECT(10, 8),                     // Set object on interface
    IF_SETPLAYERHEAD(45, 4),                 // Set player head on interface
    IF_SETPOSITION(24, 8),                   // Set position of interface widget
    IF_SETSCROLLPOS(75, 6),                  // Set scroll position
    IF_SETTEXT(48, -2),                      // Set text on widget
    CLIENTSCRIPT(49, -2),                    // Run a client script (CS2RUN)
    FRIENDLIST_LOADED(22, 0),                // Signal friends list loaded

    // -------------------------------------------------------------------------
    // Player State
    // -------------------------------------------------------------------------
    UPDATE_RUNWEIGHT(120, 2),                // Update run weight
    UPDATE_STAT(46, 6),                      // Stat update (level/xp)
    RESET_STAT(68, 4),                       // Reset all stats
    UPDATE_RUNORG(87, 1),                    // Update run energy
    UPDATE_REBOOT_TIMER(65, 2),              // Update reboot timer
    UPDATE_INV_FULL(41, -2),                 // Full inventory update
    UPDATE_INV_PARTIAL(23, -2),              // Partial inventory update
    UPDATE_INV_STOP_TRANSMIT(16, 4),         // Stop transmitting inventory

    // -------------------------------------------------------------------------
    // Player Variables (Varps / Varclients / Varbits)
    // -------------------------------------------------------------------------
    VARP_SMALL(62, 3),                       // Varp update (small, 1-byte value)
    VARP_LARGE(2, 6),                        // Varp update (large, 4-byte value)
    VARCLIENT_SMALL(80, 3),                  // Varclient (small)
    VARCLIENT_LARGE(71, -2),                 // Varclient (large, string/large)
    RESET_CLIENT_VARCACHE(40, 0),            // Reset varclient cache
    VARBIT_SMALL(53, 3),                     // Varbit (small)
    VARBIT_LARGE(83, 6),                     // Varbit (large)

    // -------------------------------------------------------------------------
    // Chat / Messages
    // -------------------------------------------------------------------------
    MESSAGE_GAME(67, -1),                    // Game message (chat)
    MESSAGE_PUBLIC(54, -1),                  // Public chat message
    MESSAGE_PRIVATE(43, -2),                 // Private message received
    MESSAGE_PRIVATE_ECHO(5, -2),             // Echo of sent private message
    MESSAGE_FRIEND_CHANNEL(20, -2),          // Friends chat channel message
    MESSAGE_CLAN_CHANNEL(44, -2),            // Clan channel message
    MESSAGE_CLAN_CHANNEL_SYSTEM(63, -1),     // Clan system message

    // -------------------------------------------------------------------------
    // Friends / Ignore List
    // -------------------------------------------------------------------------
    UPDATE_FRIENDLIST(1, -2),                // Update a friends list entry
    UPDATE_IGNORELIST(72, -2),               // Update ignore list entry
    UPDATE_FRIEND_CHAT_CHANNEL_FULL(7, -2),  // Full friends chat update
    UPDATE_FRIEND_CHAT_CHANNEL_PARTIAL(9, -2), // Partial friends chat update

    // -------------------------------------------------------------------------
    // Camera
    // -------------------------------------------------------------------------
    CAM_LOOKAT(81, 10),                      // Camera look at target
    CAM_MOVETO(91, 10),                      // Camera move to position
    CAM_RESET(78, 0),                        // Reset camera
    CAM_SHAKE(99, 4),                        // Screen shake effect
    CAM_SMOOTHRESET(25, 0),                  // Smooth camera reset

    // -------------------------------------------------------------------------
    // Audio
    // -------------------------------------------------------------------------
    MIDI_SONG(28, 2),                        // Play MIDI song
    MIDI_JINGLE(31, 4),                      // Play MIDI jingle (short)
    SYNTH_SOUND(58, 5),                      // Play sound effect

    // -------------------------------------------------------------------------
    // Misc / Utility
    // -------------------------------------------------------------------------
    RESET_ANIMS(17, 0),                      // Reset all animations
    HINT_ARROW(107, 9),                      // Show hint arrow
    CLEAR_HINT_ARROW(101, 0),               // Clear hint arrow  (some servers use 95)
    UPDATE_ZONE_PARTIAL_ENCLOSED(88, -2),    // Zone update (partial, header+data)
    UPDATE_ZONE_PARTIAL_FOLLOWS(36, 0),      // Zone update continuation header
    UPDATE_ZONE_FULL_FOLLOWS(90, 3),         // Zone full follows header
    LOGOUT_FULL(96, 0),                      // Full logout
    LOGOUT_TRANSFER(116, 2),                 // Transfer (hop world) logout
    SERVER_TICK_END(97, 0),                  // End of server tick marker
    SET_PLAYER_OP(100, -1),                  // Set right-click option on players
    PLAYER_SPOTANIM(14, 10),                 // Player spotanim (override)
    UPDATE_CLAN_SETTINGS_FULL(74, -2),       // Full clan settings update
    UPDATE_CLAN_CHANNEL_FULL(86, -2),        // Full clan channel update
    URL_OPEN(89, -1),                        // Open URL in browser
    WORLDLIST_FETCH_REPLY(69, -2),           // World list response
    ;

    companion object {
        private val byOpcode = entries.associateBy { it.opcode }

        fun fromOpcode(opcode: Int): ServerProt? = byOpcode[opcode]

        /**
         * Validates this opcode table for duplicate opcodes.
         * Call on startup to catch alignment issues early.
         */
        fun validateNoDuplicates(): List<String> {
            val seen = mutableMapOf<Int, MutableList<String>>()
            for (prot in entries) {
                seen.getOrPut(prot.opcode) { mutableListOf() }.add(prot.name)
            }
            return seen.filter { it.value.size > 1 }
                .map { (op, names) -> "Opcode $op conflict: ${names.joinToString(", ")}" }
        }
    }
}

enum class ClientProt(val opcode: Int, val size: Int) {

    // -------------------------------------------------------------------------
    // Movement
    // -------------------------------------------------------------------------
    MOVE_MINIMAPCLICK(49, 18),               // Click on minimap to move
    MOVE_GAMECLICK(39, 18),                  // Click on game world to move

    // -------------------------------------------------------------------------
    // Interface Interactions
    // -------------------------------------------------------------------------
    IF_BUTTON1(30, 8),                       // Interface button option 1
    IF_BUTTON2(52, 8),                       // Interface button option 2
    IF_BUTTON3(33, 8),                       // Interface button option 3
    IF_BUTTON4(80, 8),                       // Interface button option 4
    IF_BUTTON5(14, 8),                       // Interface button option 5
    IF_BUTTON6(72, 8),                       // Interface button option 6
    IF_BUTTON7(93, 8),                       // Interface button option 7
    IF_BUTTON8(34, 8),                       // Interface button option 8
    IF_BUTTON9(65, 8),                       // Interface button option 9
    IF_BUTTON10(4, 8),                       // Interface button option 10
    CLOSE_MODAL(21, 0),                      // Close modal interface

    // -------------------------------------------------------------------------
    // Item Interactions (world items / ground items)
    // -------------------------------------------------------------------------
    OPOBJ1(54, 6),                           // Pick up / option 1 on ground item
    OPOBJ2(10, 6),
    OPOBJ3(20, 6),
    OPOBJ4(25, 6),
    OPOBJ5(29, 6),
    OPOBJT(38, 10),                          // Use item on ground item

    // -------------------------------------------------------------------------
    // NPC Interactions
    // -------------------------------------------------------------------------
    OPNPC1(84, 2),                           // NPC option 1 (attack / talk)
    OPNPC2(28, 2),
    OPNPC3(23, 2),
    OPNPC4(16, 2),
    OPNPC5(3, 2),
    OPNPCT(47, 6),                           // Use item on NPC

    // -------------------------------------------------------------------------
    // Player Interactions
    // -------------------------------------------------------------------------
    OPPLAYER1(77, 2),                        // Player option 1
    OPPLAYER2(24, 2),
    OPPLAYER3(40, 2),
    OPPLAYER4(79, 2),
    OPPLAYER5(87, 2),
    OPPLAYER6(68, 2),
    OPPLAYER7(17, 2),
    OPPLAYER8(32, 2),
    OPPLAYERT(7, 6),                         // Use item on player

    // -------------------------------------------------------------------------
    // Location / Object Interactions
    // -------------------------------------------------------------------------
    OPLOC1(41, 9),                           // Loc (object in world) option 1
    OPLOC2(43, 9),
    OPLOC3(11, 9),
    OPLOC4(59, 9),
    OPLOC5(1, 9),
    OPLOCT(55, 13),                          // Use item on loc

    // -------------------------------------------------------------------------
    // Inventory Item Interactions
    // -------------------------------------------------------------------------
    OPITEM1(90, 4),                          // Item in inventory, option 1
    OPITEM2(35, 4),
    OPITEM3(53, 4),
    OPITEM4(67, 4),
    OPITEM5(9, 4),
    OPITEMT(36, 8),                          // Use item on item
    OPHELDT(89, 8),                          // Use item on held item

    // -------------------------------------------------------------------------
    // Chat
    // -------------------------------------------------------------------------
    CHAT_SEND_PUBLIC(78, -1),                // Send public chat message
    CHAT_SEND_PRIVATE(57, -2),              // Send private message
    CHAT_COMMAND(71, -1),                    // Chat command (::command)
    CHAT_SET_FILTER(45, 3),                  // Set chat filter settings
    FRIEND_CHAT_JOIN(64, -1),               // Join friends chat
    FRIEND_CHAT_LEAVE(48, 0),               // Leave friends chat
    FRIEND_CHAT_KICK(75, -1),               // Kick from friends chat

    // -------------------------------------------------------------------------
    // Social
    // -------------------------------------------------------------------------
    FRIENDS_ADD(88, -1),                     // Add friend
    FRIENDS_DEL(51, -1),                     // Delete friend
    IGNORE_ADD(74, -1),                      // Add ignore
    IGNORE_DEL(19, -1),                      // Delete ignore

    // -------------------------------------------------------------------------
    // Client State
    // -------------------------------------------------------------------------
    WINDOW_STATUS(22, 5),                    // Window resize / focus state
    CAMERA_ROTATION(86, 2),                  // Camera angle update
    CLIENT_CHEAT(85, -1),                    // Cheat/developer command
    CLANS_SETRANK(42, -1),                   // Set clan member rank
    CLAN_SETTINGS_SETFORM(26, -2),           // Submit clan settings form
    COUNTDIALOG(82, 4),                      // Count dialog input
    RESUME_PAUSEBUTTON(44, 4),               // Dismiss NPC dialogue
    RESUME_P_NAMEDIALOG(96, -1),             // Name dialog resume
    RESUME_P_OBJDIALOG(60, 2),              // Object dialog resume
    RESUME_P_STRINGDIALOG(73, -1),           // String dialog resume
    RESUME_P_COUNTDIALOG(62, 4),             // Count dialog resume

    // -------------------------------------------------------------------------
    // Misc
    // -------------------------------------------------------------------------
    PING(5, 0),                              // Client keep-alive ping
    SEND_SNAPSHOT(50, -2),                   // Send client snapshot/bug report
    EVENT_MOUSE_CLICK(27, 6),                // Mouse click telemetry
    EVENT_MOUSE_MOVE(81, -1),               // Mouse move telemetry
    EVENT_KEYBOARD(15, -1),                  // Keyboard event telemetry
    EVENT_NATIVE_MOUSE_CLICK(13, 6),         // Native mouse click event
    ANTICHEAT_CLIENTSYNC(92, -1),           // Anti-cheat sync payload
    ;

    companion object {
        private val byOpcode = entries.associateBy { it.opcode }

        fun fromOpcode(opcode: Int): ClientProt? = byOpcode[opcode]

        fun validateNoDuplicates(): List<String> {
            val seen = mutableMapOf<Int, MutableList<String>>()
            for (prot in entries) {
                seen.getOrPut(prot.opcode) { mutableListOf() }.add(prot.name)
            }
            return seen.filter { it.value.size > 1 }
                .map { (op, names) -> "Opcode $op conflict: ${names.joinToString(", ")}" }
        }
    }
}
