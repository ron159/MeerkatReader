package com.jocmp.capy.accounts

import com.jocmp.capy.Folder
import com.jocmp.capy.common.escapingSpecialXMLCharacters
import com.jocmp.capy.common.prepending
import com.jocmp.capy.common.repeatTab

internal fun Folder.asOPML(indentLevel: Int = 0): String {
    val parsedTitle = title.escapingSpecialXMLCharacters

    if (feeds.isEmpty()) {
        val opml = "<outline text=\"${parsedTitle}\" title=\"${parsedTitle}\"/>\n"
        return opml.prepending(tabCount = indentLevel)
    }

    val opening = "<outline text=\"${parsedTitle}\" title=\"${parsedTitle}\">\n"
    val opml = StringBuilder(opening.prepending(tabCount = indentLevel))

    feeds.forEach { feed ->
        opml.append(feed.asOPML(indentLevel = indentLevel + 1))
    }

    opml.append(repeatTab(tabCount = indentLevel)).append("</outline>\n")

    return opml.toString()
}
