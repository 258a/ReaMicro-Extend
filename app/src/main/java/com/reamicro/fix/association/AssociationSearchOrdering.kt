package com.reamicro.fix.association

import com.reamicro.fix.association.model.BookSearchResult
import java.util.Locale

private val ASSOCIATION_SEARCH_IGNORED_CHARS =
    Regex("[\\s\\u3000\\p{Punct}\\u3001-\\u303F\\uFF01-\\uFF65]+")

private val ASSOCIATION_TITLE_IGNORED_CHARS =
    Regex("[\\s\\u3000\\-_()\\[\\]<>\\\\\"'\\uFF08\\uFF09\\u3010\\u3011\\u300A\\u300B\\u3008\\u3009\\u300C\\u300D\\u300E\\u300F\\u2010\\u2011\\u2012\\u2013\\u2014\\u2015\\u201C\\u201D\\u2018\\u2019]+")

fun List<BookSearchResult>.orderExactTitleMatchesFirst(keyword: String): List<BookSearchResult> {
    return orderAssociationMatches(keyword, "")
}

fun List<BookSearchResult>.orderAssociationMatches(
    titleKeyword: String,
    authorKeyword: String,
): List<BookSearchResult> {
    val normalizedKeyword = titleKeyword.normalizedAssociationTitleKey()
    if (normalizedKeyword.isBlank() || size < 2) return this
    val normalizedAuthor = authorKeyword.normalizedAssociationSearchKey()
    return mapIndexed { index, result -> index to result }
        .sortedWith(
            compareBy<Pair<Int, BookSearchResult>> { (_, result) ->
                if (result.title.normalizedAssociationTitleKey() == normalizedKeyword) 0 else 1
            }.thenBy { (_, result) ->
                if (normalizedAuthor.isNotBlank() &&
                    result.author.normalizedAssociationSearchKey() == normalizedAuthor
                ) {
                    0
                } else {
                    1
                }
            }.thenBy { it.first },
        )
        .map { it.second }
}

fun String.normalizedAssociationSearchKey(): String =
    lowercase(Locale.ROOT).replace(ASSOCIATION_SEARCH_IGNORED_CHARS, "")

fun String.normalizedAssociationTitleKey(): String =
    lowercase(Locale.ROOT).replace(ASSOCIATION_TITLE_IGNORED_CHARS, "")
