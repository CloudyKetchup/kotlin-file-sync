package org.osaaka.extensions

import java.io.File
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.Files

fun File.basicFileAttributes(): BasicFileAttributes =
    Files.readAttributes(this.toPath(), BasicFileAttributes::class.java)