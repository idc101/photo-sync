package net.cardnell

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.xmp.XmpDirectory
import java.io.File
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.*

enum class Pick(v: Int) {
    None(0,),
    Reject(1),
    Pending(2),
    Flag(3)
}

fun main(): Unit {
    //val metadata = ImageMetadataReader.readMetadata(File("D:\\Pictures-Originals\\2012\\2012-06 Family BBQ\\DSC_0524.JPG"))
    val metadata = ImageMetadataReader.readMetadata(File("D:\\Pictures-Originals\\2002\\2002-01 Spring Term 2nd Year\\Image07.jpg"))
    for (directory in metadata.directories) {
        for (tag in directory.tags) {
            //if (tag.description.contains("Mark Bridges")) {
            System.out.println(tag)
            //}
        }
    }

//    val yearDirs = Paths.get("D:\Pictures-Originals").toFile().listFiles().filter { it.isDirectory }
//    yearDirs.forEach { yearDir ->
//        val picFolders = yearDir.listFiles().filter { it.isDirectory }
//        picFolders.forEach { picFolder ->
//            // Firstly check destination and remove
//
//            // now sync each file
//            picFolder.listFiles().filter { it.isFile }.forEach { pictureOriginal ->
//                val destPicture = getDestPicture(pictureOriginal)
//                if (!destPicture.exists()) {
//                    println("Copying $pictureOriginal to $destPicture")
//                    pictureOriginal.copyTo(destPicture, overwrite = true)
//
//                }
//            }
//        }
//    }
}

fun getDestPicture(pictureOriginal: File): File {
    return File(pictureOriginal.toString().replace("Pictures-Originals", "Pictures"))
}

fun isFriendOrFamily(picture: File): Boolean {
    //[IPTC] Keywords - Leanne Cardnell;Holly Cardnell
    val metadata = ImageMetadataReader.readMetadata(File("D:\\Pictures-Originals\\2003\\2003-04 Good Good Friday\\2002_0115_013939AA.JPG"))
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
    val rating = directory.getString(ExifIFD0Directory.TAG_RATING)
    return rating?.toInt()
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

fun getDateTimeOriginal(metadata: Metadata): Date {
    //[Exif SubIFD] Date/Time Original - 2012:06:16 14:59:34
    // obtain the Exif directory
    val directory = metadata.getFirstDirectoryOfType<ExifSubIFDDirectory>(ExifSubIFDDirectory::class.java)

    // query the tag's value
    return directory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
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