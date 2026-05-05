package com.remodex.mobile.services

import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.RPCMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class ProjectFolderServiceTest {
    @Test
    fun quickLocations_decodesBridgeLocations() =
        runTest {
            val service =
                ProjectFolderService(
                    MinimalSendRepository { method, params ->
                        assertEquals("project/quickLocations", method)
                        assertEquals(emptyMap(), (params as JSONValue.Obj).map)
                        RPCMessage.success(
                            id = null,
                            result =
                                JSONValue.Obj(
                                    mapOf(
                                        "locations" to
                                            JSONValue.Arr(
                                                listOf(
                                                    JSONValue.Obj(
                                                        mapOf(
                                                            "id" to JSONValue.Str("home"),
                                                            "label" to JSONValue.Str("Home"),
                                                            "path" to JSONValue.Str("/Users/me"),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                    ),
                                ),
                        )
                    },
                )

            val locations = service.quickLocations()

            assertEquals("home", locations.single().id)
            assertEquals("/Users/me", locations.single().path)
        }

    @Test
    fun searchDirectories_sendsRootQueryAndLimit() =
        runTest {
            var captured: Map<String, JSONValue>? = null
            val service =
                ProjectFolderService(
                    MinimalSendRepository { method, params ->
                        assertEquals("project/searchDirectories", method)
                        captured = (params as JSONValue.Obj).map
                        RPCMessage.success(
                            id = null,
                            result =
                                JSONValue.Obj(
                                    mapOf(
                                        "entries" to
                                            JSONValue.Arr(
                                                listOf(
                                                    JSONValue.Obj(
                                                        mapOf(
                                                            "name" to JSONValue.Str("remodex"),
                                                            "path" to JSONValue.Str("/Users/me/remodex"),
                                                            "isSymlink" to JSONValue.Bool(false),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                    ),
                                ),
                        )
                    },
                )

            val entries = service.searchDirectories("/Users/me", "remo")

            assertEquals("/Users/me", captured!!["path"]?.stringValue)
            assertEquals("remo", captured!!["query"]?.stringValue)
            assertEquals(80, captured!!["limit"]?.intValue)
            assertEquals("/Users/me/remodex", entries.single().path)
        }
}
