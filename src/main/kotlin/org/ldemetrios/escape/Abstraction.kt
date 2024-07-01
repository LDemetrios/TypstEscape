package org.ldemetrios.escape

import java.io.File

// For each call to Escape we should be able to specify:
// Initial setup (Map < File path -> File content >
// List of commands
// For each command:
// ---- Working directory (. by default), with support to relative and absolute handling
// ---- The command itself
// ---- Whether to keep output
// ---- Whether to keep error
// ---- For each of (output, error), if it is kept, the representation way
// ---- ---- Raw
// ---- ---- List line-by-line
// ---- ---- Conjoined list line-by-line
// ---- ---- Conjoined colored raw (then specify color)
// ---- Timeout

data class EscapingCall(
    val requestingFile: String,
    val setup: Map<String, String>,
    val commands: List<Command>,
    val output: File
)

data class Command(
    val workingDir: String,
    val command: List<String>,
    val outputSpec: StreamSpec?,
    val errorSpec: StreamSpec?,
    val timeout: Long,
    val input: String,
)

data class StreamSpec(
    val outputFormat: OutputFormat,
    val color: String
)

enum class OutputFormat {
    RAW, LIST, CONJOINED_RAW, CONJOINED_LIST
}
