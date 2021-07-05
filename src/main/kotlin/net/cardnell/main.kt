package net.cardnell

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.xmp.XmpDirectory
import java.io.File
import java.nio.file.Paths
import java.util.*

enum class Pick(v: Int) {
    None(0,),
    Reject(1),
    Pending(2),
    Flag(3)
}

fun main(): Unit {
    val source = "D:\\Pictures-Originals"
    val destination = "D:\\Pictures"

    checkPictures()
    //syncPictures()
//    val file = File("D:\\Pictures-Originals\\2013\\2013-09 September\\IMG_1537.JPG")
//    dumpInfo(file)
//    val metadata = ImageMetadataReader.readMetadata(file)
//    val date = getDateTimeOriginal(metadata)
//    println(date)
}

fun syncPictures() {
    walkAlbums { year, albumDir ->
        val destDir = Paths.get("D:\\Pictures", year, albumDir.name).toFile()
        syncDir(albumDir, destDir)
    }
}

fun checkPictures() {
    walkAlbums { year, albumDir ->
        val sourceFiles = albumDir.listFilesAsSet().filter { it.extension.toLowerCase() in listOf("jpg", "jpeg", "png", "heif", "heic") }.toSet()
        sourceFiles.forEach {
            try {
                val metadata = ImageMetadataReader.readMetadata(it)
                val date = getDateTimeOriginal(metadata)
                if (date == null) {
                    println("Date not found: $it")
                }
            } catch (ex: Exception) {
                println("failed to read: $it")
            }
        }
    }
}

fun walkAlbums(action: (String, File) -> Unit) {
    val yearDirs = Paths.get("D:\\Pictures-Originals").toFile().listDirectoriesAsSet()
    yearDirs.forEach { yearDir ->
        val albumFolders = yearDir.listDirectoriesAsSet().filter { it.isDirectory }
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
                if (checkFile(source[diff.t]!!)) {
                    copy(source[diff.t]!!, destDir)
                } else {
                    println("Skipping ${source[diff.t]} - Doesn't match filter")
                }
            }
            is Right -> delete(dest[diff.t]!!)
            is Match -> {
                if (!checkFile(source[diff.t]!!)) {
                    delete(dest[diff.t]!!)
                } else {
                    println("Skipping ${source[diff.t]} - Matches filter but already in destination")
                }
            }
        }
    }
}

fun copy(sourceFile: File, destDir: File) {
     println("Copying $sourceFile to $destDir")
     //sourceFile.copyTo(destDir, overwrite = true)
}

fun checkFile(sourceFile: File): Boolean {
    val metadata = ImageMetadataReader.readMetadata(sourceFile)
    return getRating(metadata) ?: 0 >= 3 && getPick(metadata) != Pick.Reject
}

fun delete(file: File) {
    println("Deleting extra file: $file")
    //file.delete()
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

fun getRating(metadata: Metadata): Int? {
    //[Exif IFD0] Rating - 3
    val directory = metadata.getFirstDirectoryOfType<ExifIFD0Directory>(ExifIFD0Directory::class.java)

    // query the tag's value
    return if (directory != null) {
        val rating = directory.getString(ExifIFD0Directory.TAG_RATING)
        rating?.toInt()
    } else {
        null
    }
}

fun getPick(metadata: Metadata): Pick {
    val directory = metadata.getFirstDirectoryOfType<XmpDirectory>(XmpDirectory::class.java)
    return when (directory.xmpProperties["digikam:Pick"]) {
        "1" -> Pick.Reject
        "2" -> Pick.Pending
        "3" -> Pick.Flag
        else -> Pick.None
    }
}

fun getDateTimeOriginal(metadata: Metadata): Date? {
    //[Exif SubIFD] Date/Time Original - 2012:06:16 14:59:34
    // obtain the Exif directory
    val directory =
        metadata.getDirectoriesOfType<ExifSubIFDDirectory>(ExifSubIFDDirectory::class.java).firstOrNull { it.tagCount > 0 }

    // query the tag's value
    return directory?.dateOriginal
}

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