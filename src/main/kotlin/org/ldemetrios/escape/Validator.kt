package org.ldemetrios.escape

class Validator(val args: Args) {
    val allowed = args.allow.toMutableList()
    val toAsk = args.ask.toMutableList()
    val forbidden = args.forbid.toMutableList()

    fun validate(file: String, command: List<String>): Boolean {
        if (terminated) throw InterruptedException()
        val safety = when {
            allowed.contains(command[0]) -> Safety.ALLOW
            toAsk.contains(command[0]) -> Safety.ASK
            forbidden.contains(command[0]) -> Safety.FORBID
            else -> args.safety
        }
        if (safety == Safety.ALLOW) return true
        if (safety == Safety.FORBID) return false

        println("File $file asks to run command: ${command.joinToString(" ")}") // TODO accurately format

        while (true) {
            println("\tAllow to run? always/y/n/never/h (always/yes/no/never/help)")

            val response = readln()
            if (terminated) {
                throw InterruptedException()
            }
            when (response) {
                "always" -> {
                    allowed.addAll(command)
                    return true
                }

                "y" -> {
                    return true
                }

                "n" -> {
                    forbidden.addAll(command)
                    return false
                }

                "never" -> {
                    return false
                }

                "h" -> {
                    println("always --- Yes, allow `${command[0]}` to run from now on")
                    println("y(es) --- Allow, but this time only")
                    println("n(o)  --- Don't allow, but this time only")
                    println("never --- Never allow `${command[0]}`")
                }

                else -> {
                    println("Invalid response, try again or type h to get help")
                }
            }
        }
    }
}