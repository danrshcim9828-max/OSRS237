# RSProx + RSProt 237 Opcode Alignment Reference
# ===================================================
# 
# This document maps our server's OpcodeTable.kt entries to RSProt's
# canonical ServerProt/ClientProt enum names (revision 237).
#
# When RSProx decodes a packet, it uses RSProt's definitions.
# Your server must use the SAME opcode numbers for the pipe to work.
#
# Source: https://github.com/blurite/rsprot (tag: rev237 / beta.4)
#
# =============================================
# HOW RSProt ENUMERATES OPCODES (rev 237)
# =============================================
#
# RSProt stores opcodes in:
#   rsprot/protocol/game/outgoing/GameServerProtRepository
#   rsprot/protocol/game/incoming/GameClientProtRepository
#
# These are initialized from the client's ob3 / protocol definitions.
# Our OpcodeTable.kt mirrors the numeric values in those repositories.
#
# =============================================
# SERVER → CLIENT ALIGNMENT (ServerProt)
# =============================================
#
# RSProt Name                  | Our Name                    | Opcode | Size
# -----------------------------|-----------------------------|---------|---------
# PLAYER_INFO                  | PLAYER_INFO                 |  79    | var-short
# NPC_INFO_SMALL_VIEWPORT      | NPC_INFO_SMALL_VIEWPORT     |  61    | var-short
# NPC_INFO_LARGE_VIEWPORT      | NPC_INFO_LARGE_VIEWPORT     |  32    | var-short
# REBUILD_NORMAL               | REBUILD_NORMAL              |  73    | var-short
# REBUILD_REGION               | REBUILD_REGION              |   3    | var-short
# IF_OPENTOP                   | IF_OPENTOP                  |  30    | 2
# IF_OPENSUB                   | IF_OPENSUB                  |  82    | 8
# IF_CLOSESUB                  | IF_CLOSESUB                 |  92    | 4
# IF_SETTEXT                   | IF_SETTEXT                  |  48    | var-short
# IF_SETHIDE                   | IF_SETHIDE                  |  46    | 5
# IF_SETOBJECT                 | IF_SETOBJECT                |  10    | 8
# IF_SETANIM                   | IF_SETANIM                  |  38    | 8
# IF_SETCOLOUR                 | IF_SETCOLOUR                |  60    | 6
# CLIENTSCRIPT                 | CLIENTSCRIPT                |   3    | var-short (note: same opcode as REBUILD_REGION — CHECK)
# MESSAGE_GAME                 | MESSAGE_GAME                |  67    | var-byte
# MESSAGE_PUBLIC               | MESSAGE_PUBLIC              |  54    | var-byte
# MESSAGE_PRIVATE              | MESSAGE_PRIVATE             |  43    | var-short
# VARP_SMALL                   | VARP_SMALL                  |  62    | 3
# VARP_LARGE                   | VARP_LARGE                  |   2    | 6
# VARBIT_SMALL                 | VARBIT_SMALL                |  53    | 3
# VARBIT_LARGE                 | VARBIT_LARGE                |  83    | 6
# UPDATE_STAT                  | UPDATE_STAT                 |  46    | 6
# UPDATE_RUNORG                | UPDATE_RUNORG               |  87    | 1
# UPDATE_RUNWEIGHT             | UPDATE_RUNWEIGHT            | 120    | 2
# UPDATE_INV_FULL              | UPDATE_INV_FULL             |  41    | var-short
# UPDATE_INV_PARTIAL           | UPDATE_INV_PARTIAL          |  23    | var-short
# LOGOUT_FULL                  | LOGOUT_FULL                 |  96    | 0
# LOGOUT_TRANSFER              | LOGOUT_TRANSFER             | 116    | 2
# SYNTH_SOUND                  | SYNTH_SOUND                 |  58    | 5
# MIDI_SONG                    | MIDI_SONG                   |  28    | 2
# HINT_ARROW                   | HINT_ARROW                  | 107    | 9
# CAM_MOVETO                   | CAM_MOVETO                  |  91    | 10
# CAM_LOOKAT                   | CAM_LOOKAT                  |  81    | 10
# CAM_RESET                    | CAM_RESET                   |  78    | 0
# OBJ_ADD                      | OBJ_ADD                     |  26    | 10
# OBJ_DEL                      | OBJ_DEL                     |  32    | 6  (NOTE: conflicts with NPC_INFO_LARGE — verify)
# LOC_ADD_CHANGE               | LOC_ADD_CHANGE              | 114    | 7
# LOC_DEL                      | LOC_DEL                     |  12    | 3
# MAP_ANIM                     | MAP_ANIM                    |  55    | 3
#
# =============================================
# CLIENT → SERVER ALIGNMENT (ClientProt)
# =============================================
#
# RSProt Name                  | Our Name                    | Opcode | Size
# -----------------------------|-----------------------------|---------|---------
# MOVE_MINIMAPCLICK            | MOVE_MINIMAPCLICK           |  49    | 18
# MOVE_GAMECLICK               | MOVE_GAMECLICK              |  39    | 18
# IF_BUTTON1                   | IF_BUTTON1                  |  30    | 8
# IF_BUTTON2                   | IF_BUTTON2                  |  52    | 8
# IF_BUTTON3                   | IF_BUTTON3                  |  33    | 8
# OPNPC1                       | OPNPC1                      |  84    | 2
# OPNPC2                       | OPNPC2                      |  28    | 2
# OPNPC3                       | OPNPC3                      |  23    | 2
# OPNPC4                       | OPNPC4                      |  16    | 2
# OPNPC5                       | OPNPC5                      |   3    | 2
# OPPLAYER1                    | OPPLAYER1                   |  77    | 2
# OPPLAYER2                    | OPPLAYER2                   |  24    | 2
# OPLOC1                       | OPLOC1                      |  41    | 9
# OPLOC2                       | OPLOC2                      |  43    | 9
# OPLOC3                       | OPLOC3                      |  11    | 9
# OPOBJ1                       | OPOBJ1                      |  54    | 6
# OPOBJ2                       | OPOBJ2                      |  10    | 6
# OPITEM1                      | OPITEM1                     |  90    | 4
# OPITEM2                      | OPITEM2                     |  35    | 4
# CHAT_SEND_PUBLIC             | CHAT_SEND_PUBLIC            |  78    | var-byte
# CHAT_SEND_PRIVATE            | CHAT_SEND_PRIVATE           |  57    | var-short
# PING                         | PING                        |   5    | 0
# WINDOW_STATUS                | WINDOW_STATUS               |  22    | 5
# CAMERA_ROTATION              | CAMERA_ROTATION             |  86    | 2
# FRIENDS_ADD                  | FRIENDS_ADD                 |  88    | var-byte
# FRIENDS_DEL                  | FRIENDS_DEL                 |  51    | var-byte
# CLOSE_MODAL                  | CLOSE_MODAL                 |  21    | 0
#
# =============================================
# KNOWN COLLISION RISKS IN REV 237
# =============================================
#
# These opcodes APPEAR to collide in our initial table and need verification
# against the live rsprot source:
#
#  ServerProt:
#    - CLIENTSCRIPT (3) vs REBUILD_REGION (3): 
#      In RSProt, CLIENTSCRIPT and REBUILD_REGION use the same wire opcode but
#      are distinguished by context. Verify in GameServerProtRepository.
#    - OBJ_DEL (32) vs NPC_INFO_LARGE_VIEWPORT (32):
#      In some revisions these share an opcode. Check RSProt rev237 tag.
#    - CAM_SMOOTHRESET (62) vs VARP_SMALL (62):
#      Likely a rev difference — verify which is 62 in 237.
#
# HOW TO FIX: Clone rsprot, checkout the rev237-compatible tag, and grep:
#   grep -r "CLIENTSCRIPT\|REBUILD_REGION" rsprot/protocol/
# Then update OpcodeTable.kt to match exactly.
#
# =============================================
# USING RSProx TO VERIFY OPCODES
# =============================================
#
# 1. Run RSProx against the live Jagex servers first (not your private server).
# 2. Perform known actions (open inventory, send chat, move) and note opcodes.
# 3. Compare captured opcodes to OpcodeTable.kt.
# 4. Any mismatch = fix OpcodeTable.kt.
#
# Command to run RSProx in dump mode:
#   java -jar rsprox.jar --config rsprox/rsprox.properties --dump-all
