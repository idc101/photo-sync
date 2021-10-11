package net.cardnell

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.Metadata
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.xmp.XmpDirectory
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

enum class Pick(v: Int) {
    None(0),
    Reject(1),
    Pending(2),
    Flag(3)
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

open class Image(private val sourceFile: File, private val metadata: Metadata) {
    private val yearMonthRegex = Regex("^\\d\\d\\d\\d-\\d\\d.*")
    private val dateRegex1 = Regex("^(\\d\\d\\d\\d)-(\\d\\d)-(\\d\\d).*")
    private val dateRegex2 = Regex("^(\\d\\d\\d\\d)_(\\d\\d)_(\\d\\d).*")
    private val dateRegex3 = Regex("^(\\d\\d\\d\\d)_(\\d\\d)(\\d\\d).*")
    private val dateRegex4 = Regex("^(\\d\\d\\d\\d)(\\d\\d)(\\d\\d).*")

    fun includeInSlideShow(): Boolean {
        return getRating() ?: 0 >= 3 && getPick() != Pick.Reject
    }

    open fun getRating(): Int? {
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

    open fun getPick(): Pick {
        val directory = metadata.getFirstDirectoryOfType<XmpDirectory>(XmpDirectory::class.java)
        val pick = directory.xmpProperties["digikam:Pick"] ?: directory.xmpProperties["digiKam:PickLabel"]
        return when (pick) {
            "1" -> Pick.Reject
            "2" -> Pick.Pending
            "3" -> Pick.Flag
            else -> Pick.None
        }
    }

    private fun getDateTimeOriginalAsOffsetDateTime(): OffsetDateTime? {
        //[Exif SubIFD] Date/Time Original - 2012:06:16 14:59:34
        val directory: ExifSubIFDDirectory? =
            metadata.getDirectoriesOfType<ExifSubIFDDirectory>(ExifSubIFDDirectory::class.java).firstOrNull { it.tagCount > 0 }
        return directory?.getDateOriginalAsOffsetDateTime()
    }

    fun isFriendOrFamily(): Boolean {
        //[IPTC] Keywords - Leanne Cardnell;Holly Cardnell
        for (directory in metadata.directories) {
            for (tag in directory.tags) {
                //if (tag.description.contains("Mark Bridges")) {
                println(tag)
                //}
            }
        }
        return true
    }

    fun checkImage(year: String, albumName: String): List<String> {
        val date = getDateTimeOriginalAsOffsetDateTime()
        return if (date == null) {
            listOf("Date not found: $sourceFile")
        } else {
            val checkFilename = checkFileName(date)
            val checkDate = checkDate(date, year, albumName)
            checkFilename + checkDate
        }

    }

    private fun checkFileName(pictureDate: OffsetDateTime): List<String> {
        val fileName = sourceFile.name
        fun check(match: MatchResult?): List<String> {
            return if (match != null &&
                !(match.groupValues[1] == pictureDate.year.toString() && match.groupValues[2] == pictureDate.month.value.toString().padStart(2, '0') && match.groupValues[3] == pictureDate.dayOfMonth.toString().padStart(2, '0'))
            ) {
                listOf("Photo date (${pictureDate}) does not match filename date ($sourceFile)")
            } else {
                emptyList()
            }
        }
        return check(dateRegex1.find(fileName)) + check(dateRegex2.find(fileName)) + check(dateRegex3.find(fileName)) + check(dateRegex4.find(fileName))
    }

    private fun checkDate(pictureDate: OffsetDateTime, dirYear: String, albumName: String): List<String> {
        if (albumName in allowedDatesOutsideAlbumDate) {
            return emptyList()
        }
        if (albumName.matches(yearMonthRegex)) {
            val albumYear = albumName.substring(0, 4).toInt()
            val albumMonth = albumName.substring(5, 7).toInt()
            val start = OffsetDateTime.of(LocalDate.of(albumYear, albumMonth, 1), LocalTime.MIDNIGHT, ZoneOffset.UTC)
            if (pictureDate < start.minusMonths(1) || pictureDate > start.plusMonths(2)) {
                return listOf("Photo date (${pictureDate}) more than 1 month outside of album date ($albumName)")
            }
        } else if (pictureDate.year.toString() != dirYear) {
            return listOf("Photo date (${pictureDate}) not same as dir year ($dirYear)")
        }

        return emptyList()
    }

    fun dumpInfo() {
        for (directory in metadata.directories) {
            if (directory is XmpDirectory) {
                directory.xmpProperties.forEach { k, v -> println("[XMP] $k - $v") }
            } else {
                for (tag in directory.tags) {
                    println("[${tag.directoryName}] ${tag.tagName} (${tag.tagType}) - ${tag.description}")
                }
            }
        }
        println("DateTime: ${getDateTimeOriginalAsOffsetDateTime()}")
        println("Pick: ${getPick()}")
        println("Rating: ${getRating()}")
    }

    companion object {
        fun create(sourceFile: File): Image {
            val metadata = ImageMetadataReader.readMetadata(sourceFile)
            val xmpFile = File(sourceFile.absolutePath + ".xmp")
            return if (sourceFile.extension.toLowerCase() == "heic" && xmpFile.exists()) {
                ImageWithXmp(sourceFile, metadata, xmpFile)
            } else {
                Image(sourceFile, metadata)
            }
        }
    }
}

class ImageWithXmp(sourceFile: File, metadata: Metadata, private val xmpFile: File) : Image(sourceFile, metadata) {
    override fun getRating(): Int? {
        val content = xmpFile.readText()
        val regex = Regex("xmp:Rating[ ]*=[ ]*\"(\\d)\"")
        val result = regex.find(content)
        return result?.groupValues?.get(1)?.toIntOrNull()
    }

    override fun getPick(): Pick {
        val content = xmpFile.readText()
        val regex = Regex("digiKam:PickLabel[ ]*=[ ]*\"(\\d)\"")
        val result = regex.find(content)
        return when (result?.groupValues?.get(1)) {
            "1" -> Pick.Reject
            "2" -> Pick.Pending
            "3" -> Pick.Flag
            else -> Pick.None
        }
    }
}