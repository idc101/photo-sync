package net.cardnell

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

val dryRun = false

fun main(): Unit {
    Image.create(File("D:\\Pictures-Originals\\2017\\2017-07 July\\20170721-IMG_6522.PNG")).dumpInfo()
//    if (check()) {
//        archiveAndSync()
//    }
}

fun check(): Boolean {
    var success = true
    walkAlbums { year, albumDir ->
        val sourceFiles = albumDir.listFilesAsSet().filter { it.extension.toLowerCase() in listOf("jpg", "jpeg", "heif", "heic") }.toSet()
        sourceFiles.forEach { file ->
            try {
                val image = Image.create(file)
                // Check
                val checks = image.checkImage(year, albumDir.name)
                checks.forEach { println(it) }
                if (checks.isNotEmpty()) {
                    success = false
                }
            } catch (ex: Exception) {
                println("failed to read: $file - ${ex.message}")
            }
        }
    }
    return success
}

fun archiveAndSync() {
    walkAlbums { year, albumDir ->
        val sourceFiles = albumDir.listFilesAsSet().filter { it.extension.toLowerCase() in listOf("jpg", "jpeg", "heif", "heic") }.toSet()
        sourceFiles.forEach { file ->
            try {
                val image = Image.create(file)
                // Archive
                if (image.getPick() == Pick.Reject) {
                    val destDir = Paths.get("D:\\Pictures-Archive", year, albumDir.name).toFile()
                    move(file, destDir)
                }
            } catch (ex: Exception) {
                println("failed to read: $file - ${ex.message}")
            }
        }

        // Sync
        if (year.toIntOrNull() ?: 0 >= 2011) {
            val destDir = Paths.get("D:\\Pictures", year, albumDir.name).toFile()
            syncDir(albumDir, destDir)
        }
    }
}

fun walkAlbums(action: (String, File) -> Unit) {
    val yearDirs = Paths.get("D:\\Pictures-Originals").toFile()
        .listDirectoriesAsSet().filter { it.isDirectory && it.name != ".dtrash"}.toSet()
    yearDirs.forEach { yearDir ->
        val albumFolders = yearDir.listDirectoriesAsSet()
        albumFolders.forEach { albumFolder ->
            action(yearDir.name, albumFolder)
        }
    }
}

fun syncDir(sourceDir: File, destDir: File) {
    val sourceFiles = sourceDir.listFilesAsSet().filter { it.extension.toLowerCase() in listOf("jpg", "jpeg", "png", "heif", "heic") }.toSet()
    val destFiles = destDir.listFilesAsSet()
    val source = sourceFiles.associateBy { it.name.toLowerCase() }
    val dest = destFiles.associateBy { it.name.toLowerCase() }
    diffSets(source.keys, dest.keys).forEach { diff ->
        when (diff) {
            is Left -> {
                try {
                    val image = Image.create(source[diff.t]!!)
                    if (image.includeInSlideShow()) {
                        copy(source[diff.t]!!, destDir)
                    } else {
                        println("Skipping ${source[diff.t]} - Doesn't match filter")
                    }
                } catch (ex: Exception) {
                    println("failed to read: ${source[diff.t]!!} - ${ex.message}")
                }
            }
            is Right -> delete(dest[diff.t]!!)
            is Match -> {
                try {
                    val image = Image.create(source[diff.t]!!)
                    if (!image.includeInSlideShow()) {
                        delete(dest[diff.t]!!)
                    } else {
                        if (source[diff.t]!!.length() != dest[diff.t]!!.length()) {
                            println("Different sizes copying ${source[diff.t]}")
                            copy(source[diff.t]!!, destDir)
                        } else {
                            println("Skipping ${source[diff.t]} - Matches filter but already in destination")
                        }
                    }
                } catch (ex: Exception) {
                    println("failed to read: ${source[diff.t]!!} - ${ex.message}")
                }
            }
        }
    }

    // Delete Empty dest dirs
    if (destDir.list()?.isEmpty() == true) {
        println("Deleting empty dest dir $destDir")
        destDir.delete()
    }
}

fun copy(sourceFile: File, destDir: File) {
    println("Copying $sourceFile to $destDir")
    if (!dryRun) {
        destDir.mkdirs()
        val destFile = File(destDir, sourceFile.name)
        Files.copy(
            sourceFile.toPath(),
            destFile.toPath(),
            StandardCopyOption.COPY_ATTRIBUTES,
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}

fun move(sourceFile: File, destDir: File) {
    println("Moving $sourceFile to $destDir")
    if (!dryRun) {
        destDir.mkdirs()
        val destFile = File(destDir, sourceFile.name)
        Files.move(
            sourceFile.toPath(),
            destFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
    }
}

fun delete(file: File) {
    println("Deleting file: $file")
    if (!dryRun) {
        file.delete()
    }
}
