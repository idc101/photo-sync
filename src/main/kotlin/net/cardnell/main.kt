package net.cardnell

import com.drew.imaging.ImageMetadataReader
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

enum class Pick(v: Int) {
    None(0),
    Reject(1),
    Pending(2),
    Flag(3)
}

fun main(): Unit {
    if (checkPictures()) {
        //archivePictures()
        syncPictures()
    }
}

fun testFile() {
    val file = File("D:\\Pictures-Originals\\2021\\2021-06-11 Thredbo\\20210610-IMG_3769.HEIC.xmp")
    dumpInfo(file)
    val metadata = ImageMetadataReader.readMetadata(file)
    println(getDateTimeOriginalAsOffsetDateTime(metadata))
    println(getRating(metadata))
}

fun syncPictures() {
    walkAlbums { year, albumDir ->
        if (year.toIntOrNull() ?: 0 >= 2011) {
            val destDir = Paths.get("D:\\Pictures", year, albumDir.name).toFile()
            syncDir(albumDir, destDir)
        }
    }
}

fun checkPictures(): Boolean {
    var success = true
    walkAlbums { year, albumDir ->
        val sourceFiles = albumDir.listFilesAsSet().filter { it.extension.toLowerCase() in listOf("jpg", "jpeg", "heif", "heic") }.toSet()
        sourceFiles.forEach {
            try {
                val metadata = ImageMetadataReader.readMetadata(it)
                val date = getDateTimeOriginalAsOffsetDateTime(metadata)
                if (date == null) {
                    println("Date not found: $it")
                    success = false
                } else {
                    val checkFilename = checkFileName(date, it)
                    if (checkFilename != null) {
                        println(checkFilename)
                        success = false
                    }
                    val checkDate = checkDate(date, year, albumDir.name)
                    if (checkDate != null) {
                        println("$checkDate: $it")
                        success = false
                    }
                }
            } catch (ex: Exception) {
                println("failed to read: $it - ${ex.message}")
            }
        }
    }
    return success
}

val yearMonthRegex = Regex("^\\d\\d\\d\\d-\\d\\d.*")
fun checkDate(pictureDate: OffsetDateTime, dirYear: String, albumName: String): String? {
    if (albumName in allowedDatesOutsideAlbumDate) {
        return null;
    }
    if (albumName.matches(yearMonthRegex)) {
        val albumYear = albumName.substring(0, 4).toInt()
        val albumMonth = albumName.substring(5, 7).toInt()
        val start = OffsetDateTime.of(LocalDate.of(albumYear, albumMonth, 1), LocalTime.MIDNIGHT, ZoneOffset.UTC)
        if (pictureDate < start.minusMonths(1) || pictureDate > start.plusMonths(2)) {
            return "Photo date (${pictureDate}) more than 1 month outside of album date ($albumName)"
        }
    } else if (pictureDate.year.toString() != dirYear) {
        return "Photo date (${pictureDate}) not same as dir year ($dirYear)"
    }

    return null
}

val dateRegex1 = Regex("^(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d).*")
val dateRegex2 = Regex("^(\\d\\d\\d\\d)_(\\d\\d)_(\\d\\d).*")
val dateRegex3 = Regex("^(\\d\\d\\d\\d)_(\\d\\d)(\\d\\d).*")
val dateRegex4 = Regex("^(\\d\\d\\d\\d)(\\d\\d)(\\d\\d).*")

fun checkFileName(pictureDate: OffsetDateTime, file: File): String? {
    val fileName = file.name
    fun check(match: MatchResult?): String? {
        return if (match != null &&
            !(match.groupValues[1] == pictureDate.year.toString() && match.groupValues[2] == pictureDate.month.value.toString().padStart(2, '0') && match.groupValues[3] == pictureDate.dayOfMonth.toString().padStart(2, '0'))
        ) {
            "Photo date (${pictureDate}) does not match filename date ($file)"
        } else {
            null
        }
    }
    return check(dateRegex1.find(fileName)) ?: check(dateRegex2.find(fileName)) ?: check(dateRegex3.find(fileName)) ?: check(dateRegex4.find(fileName))
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
    val source = sourceFiles.map { Pair(it.name.toLowerCase(), it) }.toMap()
    val dest = destFiles.map { Pair(it.name.toLowerCase(), it) }.toMap()
    diffSets(source.keys, dest.keys).forEach { diff ->
        when (diff) {
            is Left -> {
                if (includeFile(source[diff.t]!!)) {
                    copy(source[diff.t]!!, destDir)
                } else {
                    println("Skipping ${source[diff.t]} - Doesn't match filter")
                }
            }
            is Right -> delete(dest[diff.t]!!)
            is Match -> {
                if (!includeFile(source[diff.t]!!)) {
                    delete(dest[diff.t]!!)
                } else {
                    if (source[diff.t]!!.length() != dest[diff.t]!!.length()) {
                        println("Different sizes copying ${source[diff.t]}")
                        copy(source[diff.t]!!, destDir)
                    } else {
                        println("Skipping ${source[diff.t]} - Matches filter but already in destination")
                    }
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

fun includeFile(sourceFile: File): Boolean {
    return try {
        if (sourceFile.extension.toLowerCase() == "heic") {
            val xmpFile = File(sourceFile.absolutePath + ".xmp")
            if (xmpFile.exists()) {
                getRatingFromXmp(xmpFile) ?: 0 >= 3
            } else {
                false
            }
        } else {
            val metadata = ImageMetadataReader.readMetadata(sourceFile)
            getRating(metadata) ?: 0 >= 3 && getPick(metadata) != Pick.Reject
        }
    } catch(ex: Exception) {
        println("Failed to read: $sourceFile. Will not include")
        false
    }
}

fun copy(sourceFile: File, destDir: File) {
    println("Copying $sourceFile to $destDir")
    destDir.mkdirs()
    val destFile = File(destDir, sourceFile.name)
    Files.copy(sourceFile.toPath(), destFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
}

fun delete(file: File) {
    println("Deleting extra file: $file")
    file.delete()
}

fun isFriendOrFamily(picture: File): Boolean {
    //[IPTC] Keywords - Leanne Cardnell;Holly Cardnell
    val metadata = ImageMetadataReader.readMetadata(picture)
    for (directory in metadata.directories) {
        for (tag in directory.tags) {
            //if (tag.description.contains("Mark Bridges")) {
                System.out.println(tag)
            //}
        }
    }
    return true
}


val allowedDatesOutsideAlbumDate = listOf(
    "2001-10 Autumn Term 2nd Year",
    "2002-01 Spring Term 2nd Year",
    "2003-05 Summer Term 3rd Year",
    "2015-01 Iconic Singapore"
)

val friendsOrFamily = setOf(
    "Iain Cardnell",
    "Elena Cardnell",
    "Holly Cardnell",
    "Ewan Cardnell",
    "Dave Cardnell",
    "Jacky Cardnell",
    "Jayne Cardnell",
    "James Whitehead",
    "Joshua Whitehead",
    "Isla Whitehead",
    "Oscar Whitehead",
    "Leanne Cardnell",
    "Tom Price",
    "Jack Price",
    "Jack Crute",
    "Joyce Crute",
    "Stan Cardnell",
    "Evelyn Cardnell"
)

fun dumpInfo(file: File) {
    val metadata = ImageMetadataReader.readMetadata(file)
    for (directory in metadata.directories) {
        for (tag in directory.tags) {
            System.out.println("[${tag.directoryName}] ${tag.tagName} (${tag.tagType}) - ${tag.description}")
        }
    }
}