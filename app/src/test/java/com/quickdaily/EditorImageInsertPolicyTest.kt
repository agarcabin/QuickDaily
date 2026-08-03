package com.quickdaily

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorImageInsertPolicyTest {
    @Test
    fun processesSelectedImagesInSelectionOrder_evenWhenOneTransformIsSlow() = runBlocking {
        val visited = mutableListOf<String>()

        val result = EditorImageInsertPolicy.processInSelectionOrder(
            items = listOf("first", "middle", "last"),
        ) { item ->
            if (item == "middle") delay(20)
            visited += item
            "link:$item"
        }

        assertEquals(listOf("first", "middle", "last"), visited)
        assertEquals(
            listOf("link:first", "link:middle", "link:last"),
            result,
        )
    }
}
