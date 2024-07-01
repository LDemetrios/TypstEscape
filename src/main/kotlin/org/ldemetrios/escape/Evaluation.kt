package org.ldemetrios.escape

import java.io.File
import kotlin.io.path.createTempDirectory

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
// ---- Output file

data class LineAccumulator(val back: (String) -> Unit) {
    private var acc = StringBuilder()
    private var wasCR = false
    fun accept(c: Char) {
        if (wasCR) {
            wasCR = false
            back(acc.toString())
            acc = StringBuilder()
            when (c) {
                '\n' -> Unit
                '\r' -> wasCR = true
                in "\u2028\u2029\u0085" -> back("")
                else -> acc.append(c)
            }
        } else {
            when (c) {
                in "\n\u2028\u2029\u0085" -> {
                    back(acc.toString())
                    acc = StringBuilder()
                }
                '\r' -> wasCR = true
                else -> acc.append(c)
            }
        }
    }

    fun flush() {
        if (acc.isNotEmpty()) back(acc.toString())
        wasCR = false
        acc = StringBuilder()
    }
}

fun deleteFolder(folder: File) {
    if (folder.exists()) {
        if (folder.isDirectory) {
            folder.listFiles()?.forEach { file ->
                deleteFolder(file)
            }
        }
        folder.delete()
    }
}

data class Line(val out: Boolean, val line: String)

enum class Error { TIMEOUT, FORBIDDEN, }

data class EvaluationResult(
    val output: List<Line>,
    val exitCode: Int,
    val error: Error?,
    val outputFile: File,
    val command: Command
)

fun evaluate(call: EscapingCall, validator: Validator, quiet: Boolean): List<EvaluationResult> {
    val file = File(call.requestingFile)
    var tmpDir: File? = null
    val result = mutableListOf<EvaluationResult>()
    try {
        tmpDir = createTempDirectory("escaping-").toFile()
        for ((key, value) in call.setup) {
            val target = File(tmpDir, key)
            target.parentFile.mkdirs()
            target.writeText(value)
        }
        for (command in call.commands) {
            result.add(handle(tmpDir, call.requestingFile, command, validator, call.output, quiet))
        }
    } finally {
        if (tmpDir != null) {
            deleteFolder(tmpDir)
        }
    }
    return result
}

fun handle(
    tmpDir: File,
    requestingFile: String,
    command: Command,
    validator: Validator,
    outputFile: File,
    quiet: Boolean
): EvaluationResult {
    println("\tCommand: ${command.command.joinToString(" ")}")

    if (!validator.validate(requestingFile, command.command)) {
        println("\tForbidden\n")
        return EvaluationResult(emptyList(), Int.MAX_VALUE, Error.FORBIDDEN, outputFile, command)
    }

    val outgoing = mutableListOf<Line>()
    val outHandler = LineAccumulator {
        println("\t    $it")
        outgoing.add(Line(true, it))
    }
    val errHandler = LineAccumulator {
        println("\t E: $it")
        outgoing.add(Line(false, it))
    }

    val process = ProcessBuilder(command.command).run {
        directory(File("$tmpDir/${command.workingDir}"))
        start()
    }
    try {
        process.outputWriter().run {
            write(command.input)
            close()
        }

        val start = System.currentTimeMillis()
        val out = process.inputStream.bufferedReader()
        val err = process.errorStream.bufferedReader()
        var outOpen = true
        var errOpen = true
        while (errOpen || outOpen) {
            if (terminated) throw InterruptedException()
            if (outOpen && out.ready()) {
                val c = out.read()
                if (c == -1) outOpen = false
                else outHandler.accept(c.toChar())
            }
            if (errOpen && err.ready()) {
                val c = err.read()
                if (c == -1) errOpen = false
                else errHandler.accept(c.toChar())
            }
            if (!process.isAlive && !out.ready() && !err.ready()) break
            if (System.currentTimeMillis() - start > command.timeout) {
                outHandler.flush()
                errHandler.flush()
                println("\tTime (${command.timeout} ms) ran out\n")
                return EvaluationResult(outgoing, Int.MAX_VALUE, Error.TIMEOUT, outputFile, command)
            }
        }
        outHandler.flush()
        errHandler.flush()
        val exitCode = process.exitValue()
        println("\tExited with code ${exitCode}\n")
        return EvaluationResult(outgoing, exitCode, null, outputFile, command)
    } finally {
        if (process.isAlive) process.destroy()
    }
}