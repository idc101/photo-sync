package net.cardnell

import com.drew.metadata.exif.ExifDirectoryBase
import com.drew.metadata.exif.ExifSubIFDDirectory
import java.text.ParseException
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

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