#!/bin/bash

gradle shadowJar
cp  build/libs/TypstEscape-0.1.jar ./TypstEscape-0.1.jar
cp  build/libs/TypstEscape-0.1.jar ~/Workspace/_artifacts/TypstEscape.jar
