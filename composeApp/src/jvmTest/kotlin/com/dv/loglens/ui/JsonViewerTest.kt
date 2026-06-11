package com.dv.loglens.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonViewerTest {

    @Test
    fun formatsPlainJsonObject() {
        val out = formatJson("""{"a":1,"b":[2,3]}""")
        assertEquals(
            """
            {
                "a": 1,
                "b": [
                    2,
                    3
                ]
            }
            """.trimIndent(),
            out,
        )
    }

    @Test
    fun extractsJsonEmbeddedInFoldedHttpLog() {
        val log = """
            RESPONSE: 200 OK
            METHOD: GET
            FROM: https://example.com/spaces
            BODY Content-Type: application/json
            BODY START
            {"data":[{"id":"x","name":"Greenbuild"}],"page":0,"total":9}
            BODY END
        """.trimIndent()

        val out = formatJson(log)
        assertTrue(out != null && out.contains("\"name\": \"Greenbuild\""))
        assertTrue(out.startsWith("{"))
    }

    @Test
    fun ignoresBracesInsideStrings() {
        val log = "prefix {\"k\":\"a}b{c\",\"n\":1} suffix"
        val out = formatJson(log)
        assertTrue(out != null && out.contains("\"k\": \"a}b{c\""))
    }

    @Test
    fun returnsNullWhenNoJsonPresent() {
        assertNull(formatJson("RESPONSE: 200 OK\nMETHOD: GET"))
    }

    @Test
    fun findMatchesIsCaseInsensitiveAndNonOverlapping() {
        val text = "aXaXaxA"
        assertEquals(listOf(1..2, 3..4, 5..6), findMatches(text, "Xa"))
    }

    @Test
    fun findMatchesEmptyQueryYieldsNothing() {
        assertTrue(findMatches("anything", "").isEmpty())
    }

    @Test
    fun findMatchesFindsEveryOccurrence() {
        val matches = findMatches("co2 co2 co2", "co2")
        assertEquals(3, matches.size)
        assertEquals(0..2, matches.first())
        assertEquals(8..10, matches.last())
    }
}
