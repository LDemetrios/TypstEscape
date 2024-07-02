
# Typst Escape

This is an application for executing console commands from inside a Typst document.
For what? I find this useful for, for example, documenting libraries 
written in other languages.
In particular, I plan to one day add a Typst Escape manual,
written using it! Oh, if only Typst could compile to Markdown...

You can see an example of using TypstEscape for explaining how the code works here:
https://github.com/LDemetrios/Programnomicon/tree/main/reinterpret_cast%20and%20java
.

!!! This app is incredibly dangerous to use.
Make sure you've read the `Safety` section.
The author is not responsible for losing files due to careless use of the application!!!

## How does this work?
You insert into the document in a special way configured metadata,
marked with unique keys. 
You also insert metadata with a list of these keys, marked `<typst-escape-keys>`.

Then you launch the application, passing it the directory or file as parameter,
it requests this meta-information from the document, executes the commands, 
and then puts the output into files with the desired names.

However, there are also several Typst functions that make your work easier.
The application will write them to you in the required file if you run it with `--init-lib`.

## Typst side

### Simple example

```typ
#escape(
  current: "test.typ"
  setup: (
    "a.txt": "There's a text",
    "b.txt": ```
    To be, or not to be, that is the question:
    Whether 'tis nobler in the mind to suffer
    The slings and arrows of outrageous fortune,
    Or to take arms against a sea of troubles
    ```.text,
  ),
  command("ls", "-Ali", output: file-format("raw"), error:file-format("raw")),
  handler: it => [#it.at(0).output.output],
)

#finish-escape()
```

The `current` argument is a simple name of the file where the `escape` call is happening. 

The `setup` dictionary describes the state of the working directory before the work starts.
In this case, these are two text files in the root of the directory.

Then there is a list of commands. Each team consists of:

- An executor and a set of arguments. In this case, `ls` with the argument `-Ali`
- Indications in what format to return `stdout` commands
- Indications in what format to return `stderr` commands.

The last two could be:

- `none` (do not return)
- `file-format("raw")` &mdash; block of code
- `file-format("list")` &mdash; list of strings
- `file-format("conjoined-raw", color:"000000")` &mdash;
  the `stdout` and `stderr` lines , composed in the output order (optionally colored, hex rgb color without hash) as code line
- `file-format("conjoined-list")`&mdash;
  the `stdout` and `stderr` lines, composed in the output order and marked which stream they are from.

If `"conjoined-raw"` or `"conjoined-list"` is specified for one of the streams, the other must be the same.

Finally, `handler` &mdash; a function that builds content from the execution result.
Hint: if you are not sure, start with `handler: it => [#it]` (which is default).
The function call may initially complain that the file with the execution results does not exist, 
but TypstEscape passes a key that suppresses this error, 
and if the application is running in the background, the error will quickly disappear.

Finally, at the end there is a call to `finish-escape`, which automatically inserts the keys necessary for the application to work.

### Output examples

For example, let's run the code in Kotlin:

```typ
#import "escape.typ": *

#let build = ```
        plugins {
            kotlin("jvm") version "2.0.0"
            application
        }

        application {
            mainClass = "org.example.MainKt"
        }

        group = "org.example"
        version = "1.0-SNAPSHOT"

        repositories {
            mavenCentral()
        }

        tasks.jar {
            manifest {
                attributes["Main-Class"] = "org.example.MainKt"
            }
        }
    ```

#let settings = ```
        rootProject.name = "untitled"
    ```

#let code = ```
        package org.example
        fun main() {
            val name = "Kotlin"
            println("Hello, " + name + "!")

            for (i in 1..5) {
                println("i = $i")
                System.err.println("j = $i")
            }
        }
    ```

#let run-kotlin(output-format, error-format) = escape(
  setup: (
    "build.gradle.kts": build.text,
    "settings.gradle.kts": settings.text,
    "src/main/kotlin/Main.kt": code.text,
  ),
  command("gradle", "assemble"),
  command(
    ..("java", "-jar", "build/libs/untitled-1.0-SNAPSHOT.jar"),
    output: output-format,
    error: error-format,
  ),
  handler: it => [#it],
)

#run-kotlin(
  file-format("conjoined-raw"),
  file-format("conjoined-raw", color: "ff0000"),
)

#run-kotlin(
  file-format("conjoined-list"),
  file-format("conjoined-list"),
)

#run-kotlin(
  file-format("list"),
  file-format("raw"),
)
```

We'll get the following results:

```typ
(
  (error: none, output: (stdout: none, stderr: none), "code": 0),
  (
    error: none,
    output: [
      #text(`Hello, Kotlin!`, fill: rgb("#000000"))\
      #text(`i = 1`, fill: rgb("#000000"))\
      #text(`j = 1`, fill: rgb("#ff0000"))\
      #text(`i = 2`, fill: rgb("#000000"))\
      #text(`j = 2`, fill: rgb("#ff0000"))\
      #text(`i = 3`, fill: rgb("#000000"))\
      #text(`j = 3`, fill: rgb("#ff0000"))\
      #text(`i = 4`, fill: rgb("#000000"))\
      #text(`j = 4`, fill: rgb("#ff0000"))\
      #text(`i = 5`, fill: rgb("#000000"))\
      #text(`j = 5`, fill: rgb("#ff0000"))\
    ],
    "code": 0,
  ),
)
```

```typ
(
  ("error": none, "output": ("stdout": none, "stderr": none), "code": 0),
  (
    "error": none,
    "output": (
      ("file": "out", "line": "Hello, Kotlin!"),
      ("file": "out", "line": "i = 1"),
      ("file": "err", "line": "j = 1"),
      ("file": "out", "line": "i = 2"),
      ("file": "err", "line": "j = 2"),
      ("file": "out", "line": "i = 3"),
      ("file": "err", "line": "j = 3"),
      ("file": "out", "line": "i = 4"),
      ("file": "err", "line": "j = 4"),
      ("file": "out", "line": "i = 5"),
      ("file": "err", "line": "j = 5"),
    ),
    "code": 0,
  ),
)
```

```typ
(
  ("error": none, "output": ("stdout": none, "stderr": none), "code": 0),
  (
    "error": none,
    "output": (
      "stdout": ("Hello, Kotlin!", "i = 1", "i = 2", "i = 3", "i = 4", "i = 5"),
      "stderr": raw("j = 1\nj = 2\nj = 3\nj = 4\nj = 5"),
    ),
    "code": 0,
  ),
)
```

Here `error` is either `none`, `"forbidden"` or `"timeout"`.
Yes, you can set the timeout for a command by passing it as an argument (in milliseconds):

```typ
command("gradle", "assemble", timeout:1000)
```

## Application side

Running the application is simple: you will need Java installed.

```shell
java -jar TypstEscape-0.1.jar FILE
```

- If `FILE` is a directory, the application will search for `.typ` files in it and process it one by one
- Otherwise, the application will handle it as file regardless of the extension.

By default, the application will run in an endless loop processing your files.
You can set a delay between iterations in milliseconds: `--delay 1000`, or specify to process only `--once`.

You can also request to create a file with helper functions (The file will be counted from the project root):

```shell
java -jar TypstEscape-0.1.jar /path/to/your/project --init-lib escape.typ
```

Or print a help message:

```shell
java -jar TypstEscape-0.1.jar --help
```

## Safety

This app is incredibly dangerous to use.

This code (or a similar one for Windows) will erase your entire home directory and tell you that it was so.

```typ
#escape(
  command("rm", "-rf", "~/", output: none, error:none),
  handler: it => [Ha-ha],
)
```

There is a command validation system for this.

- Firstly, for each specific command:
  
  ```shell
  java -jar TypstEscape-0.1.jar --allow gradle --ask java --forbid rm --forbid mv
  ```

  Thus, we allow the `gradle` command to run, 
  the `java` command must require confirmation, 
  the `rm` and `mv` commands are always prohibited.  

- Secondly, for all commands:

  - `--allow-all` --- allow all commands to run
  - `--ask-each` --- ask every time
  - `--forbid-all` --- forbid everything

  `--forbid-all` is default. 
  
Specific instructions take precedence over general instructions.
If a single command is specified as both `--allow` and `--forbid`, the behavior is undefined!

## Installation

The jar compiled along with its dependencies lies at the root of the repository.

## Build

You will need gradle and two other libraries:

```shell
git clone https://github.com/LDemetrios/LDemetriosCommons.git
cd LDemetriosCommons 
gradle publish
cd ..

git clone https://github.com/LDemetrios/Typst4k.git
cd Typst4k
gradle publish
cd ..

git clone https://github.com/LDemetrios/TypstEscape.git
cd TypstEscape 
gradle shadowJar
cd ..

cp TypstEscape/build/libs/TypstEscape-0.1.jar ./TypstEscape-0.1.jar
```


## Contacts
If you experience bugs or have a proposal for improvements, feel free to open issues. 
PRs are also welcome, feel free to ask questions about the internal structure of the project.

tg: @LDemetrios

mail: ldemetrios@yandex.ru