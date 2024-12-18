package io.github.fvasco.pinpoi.importer

import io.github.fvasco.pinpoi.model.Placemark
import io.github.fvasco.pinpoi.util.assertDebug
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.InputStream
import java.util.*

/**
 * Base XML importer
 *
 * @author Francesco Vasco
 */
abstract class AbstractXmlImporter : AbstractImporter() {
    protected val parser: XmlPullParser = XML_PULL_PARSER_FACTORY.newPullParser()
    protected var placemark: Placemark? = null
    protected var text: String = ""
    protected var namespace: String = ""
    protected var tag: String = DOCUMENT_TAG
    private val tagStack = ArrayDeque<Pair<String, String>>()

    @Throws(IOException::class)
    override fun importImpl(inputStream: InputStream) {
        try {
            parser.setInput(inputStream, null)
            var eventType = parser.eventType
            tag = DOCUMENT_TAG
            handleStartTag()
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        tagStack.addLast(namespace to tag)
                        namespace = parser.namespace
                        tag = parser.name
                        text = ""
                        handleStartTag()
                    }

                    XmlPullParser.TEXT -> if (text.isEmpty()) text =
                        parser.text else text += parser.text

                    XmlPullParser.END_TAG -> {
                        handleEndTag()
                        val namespaceTag = tagStack.removeLast()
                        namespace = namespaceTag.first
                        tag = namespaceTag.second
                        text = ""
                    }
                }
                eventType = parser.next()
            }
            assertDebug(tag === DOCUMENT_TAG)
            assertDebug(placemark == null, placemark)
            assertDebug(text == "", text)
        } catch (e: XmlPullParserException) {
            throw IOException("Error reading XML file", e)
        }
    }

    /**
     * Create a new placemark and saves old one
     */
    protected fun newPlacemark() {
        assertDebug(placemark == null, placemark)
        placemark = Placemark()
    }

    protected fun importPlacemark() {
        val p = checkNotNull(placemark) { "No placemark to import" }
        if (p.name.isNotBlank()) {
            importPlacemark(p)
        }
        placemark = null
    }

    /**
     * Check if current path match given tags, except current tag in [.tag]
     */
    protected fun checkCurrentPath(vararg tags: String): Boolean {
        if (tags.size != tagStack.size - 1) return false
        val iterator = tagStack.descendingIterator()
        for (i in tags.indices.reversed()) {
            if (tags[i] != iterator.next().second) return false
        }
        return true
    }

    /**
     * Handle a start tag
     */
    @Throws(IOException::class)
    protected abstract fun handleStartTag()

    /**
     * Handle a end tag, text is in [.text] attribute
     */
    @Throws(IOException::class)
    protected abstract fun handleEndTag()

    companion object {
        private const val DOCUMENT_TAG = "<XML>"

        private val XML_PULL_PARSER_FACTORY: XmlPullParserFactory by lazy {
            XmlPullParserFactory.newInstance().apply {
                isNamespaceAware = true
                isValidating = false
            }
        }
    }
}
