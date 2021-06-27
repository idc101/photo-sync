package net.cardnell

interface Diff<T>
data class Match<T>(val t: T): Diff<T>
data class Left<T>(val t: T): Diff<T>
data class Right<T>(val t: T): Diff<T>

fun <T> diffSets(left: Set<T>, right: Set<T>): Set<Diff<T>> {
    val matching = left.intersect(right).map { Match(it) }
    val onlyLeft = left.minus(right).map { Left(it) }
    val onlyRight = right.minus(left).map { Right(it) }

    return emptySet<Diff<T>>().plus(matching).plus(onlyLeft).plus(onlyRight)
}
