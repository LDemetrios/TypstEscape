
# Typst Escape

Это приложение для исполнения консольных команд изнутри Typst-документа. 
Зачем? Я нахожу это полезным для, например, документирования библиотек, 
написанных на других языках. 
В частности, я планирую однажды добавить Typst Escape мануал, 
написанный с помощью него же! Эх, если бы только Typst умел компилироваться в Markdown...

!!! Это приложение невероятно опасно в использовании. 
Убедитесь, что вы прочитали раздел `Safety`. 
Автор не несёт ответственности за утеренные файлы при неосторожном использовании приложения !!!

## Как это работает?

Вы вставляете в документ специальным образом (см. далее) сконфигурированную метаинформацию, 
помеченную уникальными ключами. Также вы вставляете метаинформацию со списком этих ключей, 
помеченную `<typst-escape-keys>`. 

Затем вы запускаете приложение, передав ему в параметры нужную директорию или файл, 
оно запрашивает у документа эту метаинформацию, исполняет команды, 
а затем кладёт вывод в файлы с нужными именами. 

Впрочем, есть также несколько Typst-функций, облегачающих работу.
Приложение запишет их вам в нужный файл, если запустить его с `--init-lib <FILENAME>`.


## Typst side

### Простой пример

```typ
#escape(
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

Словарь `setup` описывает состояние рабочей директории до начала работы. 
В данном случае --- это два текстовых файла в корне директории.

Затем идёт перечень команд. Каждая команда состоит из:

- Исполнителя и набора аргументов. В данном случае, `ls` с аргументом `-Ali`
- Указания, в каком формате вернуть `stdout` команды
- Указания, в каком формате вернуть `stderr` команды.

Последние два могут быть:

- `none` (не возвращать)
- `file-format("raw")` &mdash; блоком кода (опционально &mdash;)
- `file-format("list")` &mdash; списком строк
- `file-format("conjoined-raw", color:"000000")` &mdash;
строки `stdout` и `stderr`, составленные в порядке вывода (опционально раскрашенные, цвет в hex rgb без решётки) в качестве code line 
- `file-format("conjoined-list")`&mdash;
  строки `stdout` и `stderr`, составленные в порядке вывода и помеченные, из какого потока.

Если для одного из потоков указан `"conjoined-raw"` или `"conjoined-list"`, для другого должен быть такой же.

Наконец, `handler` &mdash; функция, которая строит контент из результата исполнения. 
Подсказка: если вы не уверены, начните с `handler: it => [#it]` (по дефолту). 
Вызов функции может сначала ругаться, что файла с результатами исполнения не существует, 
но TypstEscape передаёт ключ, подавляющий эту ошибку, и если приложение работает в фоновом режиме, 
ошибка быстро пропадёт.

Наконец, в конце происходит вызов `finish-escape`, 
который автоматически вставляет нужные для работы приложения ключи.

### Примеры вывода.

Для примера запустим код на Kotlin:

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

Мы получим следующие результаты:

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

Здесь `error` &mdash; `none`, `"forbidden"` или `"timeout"`. 
Да, вы можете установить timeout для команды, передав ей соответствующий аргумент (в миллисекундах):
```typ
command("gradle", "assemble", timeout:1000)
```

## Application side

Запуск приложения прост: вам понадобится установленная Java:

```shell
java -jar TypstEscape-0.1.jar FILE
```

- Если `FILE` &mdash; директория, приложение будет искать `.typ` файлы в ней и обрабатывать по очереди
- Если нет, приложение будет обрабатывать его независимо от расширения.

По умолчанию приложение будет работать, в бесконечном цикле обрабатывая ваши файлы. 
Вы можете поставить задержку между итерациями в миллисекундах: `--delay 1000`, 
или указать обработать только один раз: `--once`.

Вы также можете потребовать создать файл со вспомогательными функциями (Файл будет отсчитываться от корня проекта):

```shell
java -jar TypstEscape-0.1.jar /path/to/your/project --init-lib escape.typ
```

Или напечатать help message:

```shell
java -jar TypstEscape-0.1.jar --help
```

## Safety

Это приложение невероятно опасно в использовании. 

Вот такой (или аналогичный для Windows) код сотрёт вам домашнюю директорию, и скажет, что так и было.

```typ
#escape(
  command("rm", "-rf", "~/", output: none, error:none),
  handler: it => [Ha-ha],
)
```

Для этого есть система валидации команд.

- Во-первых, для каждой конкретной команды:
  
  ```shell
  java -jar TypstEscape-0.1.jar --allow gradle --ask java --forbid rm --forbid mv
  ```
  
  Таким образом, мы разрешаем запускаться команде `gradle`, 
  команда `java` должна требовать подтверждения, команды `rm` и `mv` всегда запрещены.
  

- Во-вторых, для всех команд:
  
  - `--allow-all` --- разрешать все команды
  - `--ask-each` --- спрашивать каждый раз
  - `--forbid-all` --- запретить всё
  
  По умолчанию `--forbid-all`. 
  
  Частные указания имеют больший приоритет, чем общие. 
  Если отдельная команда указана и как `--allow`, и как `--forbid`, поведение не определено!
  
## Установка

Скомпилированный вместе с зависимостями jar лежит в корне репозитория.

## Сборка

Вам понадобится gradle и две другие библиотеки:

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