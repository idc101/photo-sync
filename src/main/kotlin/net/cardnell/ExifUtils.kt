package net.cardnell

import com.drew.metadata.Metadata
import com.drew.metadata.StringValue
import com.drew.metadata.exif.ExifDirectoryBase
import com.drew.metadata.exif.ExifIFD0Directory
import com.drew.metadata.exif.ExifSubIFDDirectory
import com.drew.metadata.xmp.XmpDirectory
import java.io.File
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.regex.Pattern

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

fun getRatingFromXmp(xmpFile: File): Int? {
    val content = xmpFile.readText()
    val regex = Regex("xmp:Rating[ ]*=[ ]*\"(\\d)\"")
    val result = regex.find(content)
    return result?.groupValues?.get(1)?.toIntOrNull()
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

fun getDateTimeOriginalAsOffsetDateTime(metadata: Metadata): OffsetDateTime? {
    //[Exif SubIFD] Date/Time Original - 2012:06:16 14:59:34
    // obtain the Exif directory
    val directory =
        metadata.getDirectoriesOfType<ExifSubIFDDirectory>(ExifSubIFDDirectory::class.java).firstOrNull { it.tagCount > 0 }

    // query the tag's value
    return directory?.getDateOriginalAsOffsetDateTime()
}

fun ExifSubIFDDirectory.getDateOriginalAsOffsetDateTime(): OffsetDateTime? {
    val timeZoneOriginal = this.getTimeZone(ExifDirectoryBase.TAG_TIME_ZONE_ORIGINAL)
    val timeZone = this.getTimeZone(ExifDirectoryBase.TAG_TIME_ZONE)
    return this.getDateAsOffsetDateTime(ExifDirectoryBase.TAG_DATETIME_ORIGINAL,timeZoneOriginal ?: timeZone)
}

fun ExifSubIFDDirectory.getDateAsOffsetDateTime(tagType: Int, timeZone: ZoneOffset?): OffsetDateTime? {
    var timeZone = timeZone
    var dateString = this.getObject(tagType).toString()
    var date: OffsetDateTime? = null
    val datePatterns = arrayOf(
        "yyyy:MM:dd HH:mm:ss",
        "yyyy:MM:dd HH:mm",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy.MM.dd HH:mm:ss",
        "yyyy.MM.dd HH:mm",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd",
        "yyyy-MM",
        "yyyyMMdd",
        "yyyy"
    )

    val subsecondPattern = Pattern.compile("(\\d\\d:\\d\\d:\\d\\d)(\\.\\d+)")
    val subsecondMatcher = subsecondPattern.matcher(dateString)
    if (subsecondMatcher.find()) {
        dateString = subsecondMatcher.replaceAll("$1")
    }
    val timeZonePattern = Pattern.compile("(Z|[+-]\\d\\d:\\d\\d|[+-]\\d\\d\\d\\d)$")
    val timeZoneMatcher = timeZonePattern.matcher(dateString)
    if (timeZoneMatcher.find()) {
        timeZone = ZoneOffset.of(timeZoneMatcher.group())
        dateString = timeZoneMatcher.replaceAll("")
    }
    val var13 = datePatterns.size
    var var14 = 0
    while (var14 < var13) {
        val datePattern = datePatterns[var14]
        try {
            val localDateTime = LocalDateTime.parse(dateString, DateTimeFormatter.ofPattern(datePattern))
            date = if (timeZone != null) {
                localDateTime.atOffset(timeZone)
            } else {
                localDateTime.atOffset(ZoneOffset.UTC)
            }
            break
        } catch (var18: ParseException) {
            ++var14
        }
    }
    return date
}

fun ExifSubIFDDirectory.getTimeZone(tagType: Int): ZoneOffset? {
    val timeOffset = this.getString(tagType)
    return if (timeOffset != null && timeOffset.matches(Regex("[\\+\\-]\\d\\d:\\d\\d")))
        ZoneOffset.of(timeOffset)
    else
        null
}