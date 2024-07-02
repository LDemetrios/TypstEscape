package org.ldemetrios.escape

import org.ldemetrios.typst4k.Typst
import org.ldemetrios.typst4k.TypstException
import org.ldemetrios.typst4k.orm.*
import org.ldemetrios.typst4k.rt.*
import java.io.File
import java.io.IOException
import org.ldemetrios.escape.OutputFormat.*

val lineSep = Regex("\r\n|[\n\r\u2028\u2029\u0085]")

class MalformedQuery(message: String) : IllegalArgumentException(message)

inline fun <reified T : TValue> TDictionary<*>.getAs(key: String): T {
    return when (val it = this[key]) {
        null -> throw MalformedQuery("<$key> is expected to be present in the dictionary")
        is T -> it
        else -> throw MalformedQuery("<$key> in the dictionary should be a " + T::class.simpleName)
    }
}

inline fun <T> T?.orElse(x: () -> T) = this ?: x()

fun process(typst: Typst, list: List<File>, validator: Validator, projectRoot: File, args: Args) {
    for (file in list) {
        println("File: ${file.relativeTo(projectRoot)}")
        try {
            val keys = typst
                .query<TMetadata<TArray<TStr>>>(
                    file.absolutePath,
                    "typst-escape-keys",
                    inputs = mapOf("typst-escape-working" to "true"),
                    root = args.root
                )
                .orElseThrow()
                .singleOrNull()
                .orElse { throw MalformedQuery("No value or multiple values by label <typst-escape-keys>") }
                .value.map { it.value }

            for (key in keys) {
                val value = typst
                    .query<TMetadata<TDictionary<TValue>>>(
                        file.absolutePath,
                        key,
                        inputs = mapOf("typst-escape-working" to "true"),
                        root = args.root
                    )
                    .orElseThrow()
                    .singleOrNull()
                    .orElse { throw MalformedQuery("No value or multiple values by label <$key>") }
                    .value

                val call = parseCall(file, projectRoot, value)
                val result = TArray(
                    evaluate(call, validator, args.queit).map {
                        format(it, args)
                    }
                )

                call.output.run {
                    if (!exists()) parentFile.mkdirs()
                    writeText(result.repr())
                }
            }
        } catch (e: TypstException) {
            println("\tCompilation error")
            (e.message ?: "").split(lineSep).forEach {
                println("\t$it")
            }
        } catch (e: IOException) {
            println("\tI/O error: ${e.message}")
        } catch (e: MalformedQuery) {
            println("\t\t" + e.message)
        }
    }
}

fun format(result: EvaluationResult, args: Args): TDictionary<TValue> {
    return when (result.error) {
        Error.TIMEOUT -> {
            TDictionary(
                "error" to TStr("timeout"),
                "output" to formatOutput(result)
            )
        }
        Error.FORBIDDEN -> {
            TDictionary(
                "error" to TStr("forbidden"),
            )
        }
        null -> {
            // Success
            TDictionary(
                "error" to TNone,
                "output" to formatOutput(result),
                "code" to result.exitCode.t
            )
        }
    }
}

fun formatOutput(result: EvaluationResult): TValue {
    val outF = result.command.outputSpec?.outputFormat
    val errF = result.command.errorSpec?.outputFormat

    if (outF == CONJOINED_LIST || errF == CONJOINED_LIST) {
        if (outF != CONJOINED_LIST || errF != CONJOINED_LIST) {
            throw MalformedQuery("`conjoined-list` be format of both output and error, if selected")
        }
        return result.output.map {
            TDictionary(
                "file" to (if (it.out) "out" else "err").t,
                "line" to it.line.t,
            )
        }.let(::TArray)
    }

    if (outF == CONJOINED_RAW || errF == CONJOINED_RAW) {
        if (outF != CONJOINED_RAW || errF != CONJOINED_RAW) {
            throw MalformedQuery("`conjoined-list` be format of both output and error, if selected")
        }
        return result.output.flatMap {
            listOf(
                TText(
                    body = TRaw(it.line.t),
                    fill = TRgb(("#" + (if (it.out) result.command.outputSpec else result.command.errorSpec)!!.color).t)
                ),
                TLinebreak()
            )
        }.let(::TArray).let(::TSequence)
    }
    val outputFormatted = formatSingle(result, result.command.outputSpec, true)
    val errorFormatted = formatSingle(result, result.command.errorSpec, false)
    return TDictionary(
        "stdout" to outputFormatted,
        "stderr" to errorFormatted
    )
}

fun formatSingle(result: EvaluationResult, spec: StreamSpec?, out: Boolean): TValue = when (spec?.outputFormat) {
    null -> TNone
    RAW -> TRaw(
        result.output.filter { it.out == out }.joinToString("\n") { it.line }.t,
    )
    LIST -> result.output.filter { it.out == out }.map { it.line.t }.let(::TArray)
    else -> throw AssertionError()
}

fun parseCall(requestingFile: File, projectRoot: File, value: TDictionary<TValue>): EscapingCall {
    val setup = value.getAs<TDictionary<*>>("setup").mapValues {
        it.value as? TStr ?: throw MalformedQuery("All the values in the `setup` dictionary should be `str`s")
    }

    val self = "/" + requestingFile.relativeTo(projectRoot).path

    val output = value.getAs<TStr>("output").value.replace("\$self", self)

    val commands = value.getAs<TArray<*>>("commands").map {
        it as? TDictionary<*>
            ?: throw MalformedQuery("All the values in the `commands` array should be dictionaries")
    }.mapIndexed { index, commandDict ->
        val workingDir = (commandDict["working-dir"] ?: "".t) as? TStr ?: throw MalformedQuery(
            "Working directory for $index-th command should be unspecified or be a `str`"
        )
        val command = commandDict
            .getAs<TArray<*>>("command")
            .map {
                it as? TStr
                    ?: throw MalformedQuery("All the values in the `command` array for $index-th command should be `str`s")
            }


        val outputSpec = streamSpec(commandDict, index, "output", "Output")

        val errorSpec = streamSpec(commandDict, index, "error", "Error")

        val timeout = (commandDict["timeout"] ?: Long.MAX_VALUE.t) as? TInt
            ?: throw MalformedQuery("Timeout for $index-th command should be unspecified or be an `int`")

        val input = (commandDict["input"] ?: "".t) as? TStr
            ?: throw MalformedQuery("Input for $index-th command should be unspecified or be a `str`")

        Command(
            workingDir.value,
            command.map { it.value },
            outputSpec,
            errorSpec,
            timeout.value,
            input.value
        )
    }

    return EscapingCall(
        requestingFile.relativeTo(projectRoot).invariantSeparatorsPath,
        setup.mapValues { it.value.value },
        commands,
        output.run {
            if (startsWith("/")) {
                projectRoot.absolutePath + this // .substring(1)
            } else {
                requestingFile.parentFile.absolutePath + "/" + this
            }
        }.let(::File)
    )
}

private fun streamSpec(
    commandDict: TDictionary<*>,
    index: Int,
    name: String,
    nameCap: String,
): StreamSpec? {
    val specDict =
        commandDict["$name-spec"] ?: throw MalformedQuery("$nameCap spec for $index-th command should be present")

    return if (specDict is TNone) {
        null
    } else {
        val specDict = specDict as? TDictionary<*>
            ?: throw MalformedQuery("$nameCap spec for $index-th command should be a dictionary or none")
        val format = specDict.getAs<TStr>("format").value.run {
            when (this) {
                "raw" -> RAW
                "list" -> LIST
                "conjoined-raw" -> CONJOINED_RAW
                "conjoined-list" -> CONJOINED_LIST
                else -> throw MalformedQuery("$nameCap format should be one of `raw`, `list`, `conjoined-raw`, `conjoined-list`")
            }
        }
        val color = specDict.getAs<TStr>("color").value
        StreamSpec(format, color)
    }
}
