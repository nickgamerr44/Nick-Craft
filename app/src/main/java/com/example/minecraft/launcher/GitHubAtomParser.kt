package com.example.minecraft.launcher

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class AtomEntry(
  val id: String,
  val title: String,
  val link: String,
  val updated: String,
  val authorName: String,
  val contentText: String
)

object GitHubAtomParser {

  fun parseEntries(xml: String): List<AtomEntry> {
    if (xml.isBlank()) return emptyList()
    return runCatching {
      val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
      }
      val doc = factory.newDocumentBuilder()
        .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
      val entryNodes = doc.getElementsByTagName("entry")
      val entries = mutableListOf<AtomEntry>()
      for (i in 0 until entryNodes.length) {
        val el = entryNodes.item(i) as? Element ?: continue
        val id = childText(el, "id")
        val title = childText(el, "title").trim()
        val updated = childText(el, "updated").ifBlank { childText(el, "published") }
        val link = extractLinkHref(el)
        val authorName = extractAuthorName(el)
        val rawContent = childText(el, "content")
        val cleanContent = rawContent
          .replace(Regex("(?i)<br\\s*/?>"), "\n")
          .replace(Regex("(?i)</(p|li|div|h[1-6]|tr)>"), "\n")
          .replace(Regex("(?i)<li[^>]*>"), "• ")
          .replace(Regex("<[^>]+>"), "")
          .replace("&amp;", "&")
          .replace("&lt;", "<")
          .replace("&gt;", ">")
          .replace("&quot;", "\"")
          .replace("&#39;", "'")
          .lines()
          .map { it.trim().replace(Regex("[ \\t]+"), " ") }
          .filter { it.isNotEmpty() }
          .joinToString("\n")

        entries.add(
          AtomEntry(
            id = id,
            title = title,
            link = link,
            updated = updated,
            authorName = authorName,
            contentText = cleanContent
          )
        )
      }
      entries
    }.getOrDefault(emptyList())
  }

  private fun childText(parent: Element, tagName: String): String {
    val children = parent.childNodes
    for (i in 0 until children.length) {
      val node = children.item(i)
      if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == tagName) {
        return node.textContent.orEmpty()
      }
    }
    return ""
  }

  private fun extractLinkHref(parent: Element): String {
    val children = parent.childNodes
    for (i in 0 until children.length) {
      val node = children.item(i)
      if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == "link") {
        val href = (node as Element).getAttribute("href")
        if (href.isNotBlank()) return href
      }
    }
    return ""
  }

  private fun extractAuthorName(parent: Element): String {
    val children = parent.childNodes
    for (i in 0 until children.length) {
      val node = children.item(i)
      if (node.nodeType == Node.ELEMENT_NODE && node.nodeName == "author") {
        return childText(node as Element, "name").trim()
      }
    }
    return ""
  }
}
