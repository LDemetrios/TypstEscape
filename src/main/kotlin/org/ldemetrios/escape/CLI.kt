package org.ldemetrios.escape

import com.xenomachina.argparser.ArgParser
import com.xenomachina.argparser.default
import com.xenomachina.argparser.mainBody
import org.ldemetrios.typst4k.Typst
import sun.misc.Signal
import java.io.File

enum class Safety {
    ALLOW, ASK, FORBID
}

const val typstSide = """

#let try-catch(lbl, key, try, catch) = locate(loc => {
  let path-label = label(lbl)
  let first-time = query(locate(_ => { }).func(), loc).len() == key
  if first-time or query(path-label, loc).len() > 0 {
    [#try()#path-label]
  } else {
    catch()
  }
})

#let escape-keys = state("escape-keys", 0)

#let escape(
  setup: (:),
  ..commands,
  output-file: auto,
  handler: it => [#it],
  replacement: [`Missing information`],
) = {
  escape-keys.display(key => {
    let file = if output-file == auto {
      "test.typ" + str(key) + ".typesc"
    } else {
      output-file
    }
    [
      #metadata((
        setup: setup,
        commands: commands.pos(),
        output: file,
      )) #label("typst-key-" + str(key))
    ]
    if sys.inputs.at("typst-escape-working", default:"false") == "true" {
      []
    } else {
      handler(eval(read(file)))
    }
  })
  escape-keys.update(it => it + 1)
}

#let command(..entries, output: none, error: none) = (
  command: entries.pos(),
  output-spec: output,
  error-spec: error,
)

#let file-format(format, color: "000000") = (
  format: format,
  color: color,
)

#let finish-escape() = escape-keys.display(it => [
  #metadata(
    range(it).map(jt => "typst-key-" + str(jt)),
  ) <typst-escape-keys>
])

//// Example:
//
//#escape(
//  setup: (
//    "build.gradle.kts": build.text,
//    "settings.gradle.kts": settings.text,
//    "src/main/kotlin/Main.kt": code.text,
//  ),
//  command("gradle", "assemble"),
//  command(
//    ..("java", "-jar", "build/libs/untitled-1.0-SNAPSHOT.jar"),
//    output: file-format("conjoined-raw"),
//    error: file-format("conjoined-raw", color: "ff0000"),
//  ),
//  handler: it => it.at(1).output
//)
//
//#finish-escape()

"""

class Args(parser: ArgParser) {
    val initLib by parser.storing(
        "--init-lib",
        help = "File to print the typst side of the application (defaults to null --- don't print). Relative to root (if ROOT is not a directory, relative to its dir)."
    ).default(null)

    val root by parser.positional("ROOT", help = "The root directory or the file to work on.").default(null)

    val delay by parser.storing(
        "-d", "--delay",
        help = "The delay between iterations in milliseconds (defaults to 0)."
    ) { toLong() }.default(0L)

    val once by parser.flagging("--once", help = "Process only once.")

    val allow by parser.adding(
        "--allow",
        help = "Commands to trust unquestioningly (unrecommended for `rm`, `mv` and other dangerous commands )."
    )

    val forbid by parser.adding(
        "-f", "--forbid",
        help = "Commands that should not be allowed to run."
    )

    val ask by parser.adding(
        "-a", "--ask",
        help = "Commands to ask about each time."
    )

    val safety by parser.mapping(
        "--allow-all" to Safety.ALLOW,
        "--ask-each" to Safety.ASK,
        "--forbid-all" to Safety.FORBID,
        help = "Safety policy for all commands that are not mentioned in other arguments (--ask-each is recommended, defaults to --forbid-all)."
    ).default { Safety.FORBID }

    val executable by parser.storing("--exec", help = "The typst executable (defaults to one in \$PATH).")
        .default("typst")

    val queit by parser.flagging("-q", "--quiet", help = "Do not print the output of commands.")
}

lateinit var mainThread: Thread
var terminated = false

fun parentChain(file: File): List<String> {
    val result = mutableListOf<String>()
    var tmp: File? = file.absoluteFile
    while (tmp != null) {
        result.add(tmp.absolutePath)
        tmp = tmp.parentFile
    }
    return result
}

fun main(args: Array<String>) = mainBody {
    val x = Line(true, "1") // Somehow Line isn't loaded properly by JVM. This fixes it.
    mainThread = Thread.currentThread()
    ArgParser(args).parseInto(::Args).run {
        Signal.handle(Signal("INT")) {
            terminated = true
            mainThread.interrupt()
            System.`in`.close()
            println("Received SIGINT signal, shutting down...")
        }


        this.initLib.run { if (this == "null") null else this }?.let {
            File(root, it).writeText(typstSide)
            return@run
        }

        val validator = Validator(this)
        if(this.root == null) {
            return@run println("No files provided")
        }
        val list = this.root!!.let(::File).let {
            if (it.isFile) listOf(it) else it.walkTopDown().toList().filter { it.extension == "typ" }
        }
        val root = this.root!!.let(::File).let {
            if (it.isFile) it.parentFile else it
        }
        val typst = Typst(this.executable)


        if (this.once) {
            process(typst, list, validator, root, this)
        } else {
            var iter = 0
            while (!terminated) {
                iter++
                println("\n\n=== Iteration $iter\n\n")
                process(typst, list, validator, root, this)
            }
        }
    }
}

