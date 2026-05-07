package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.CodexAccessMode
import com.remodex.mobile.core.model.CodexPluginMetadata
import com.remodex.mobile.core.model.CodexTurnMention
import com.remodex.mobile.core.model.CodexTurnSkillMention
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.model.RPCError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TurnStartRpcCompatTest {

    @Test
    fun serviceTierBridgeUpdateCommand_matchesIosRuntimeCompatPrompt() {
        assertEquals("npm install -g remodex@latest", SERVICE_TIER_BRIDGE_UPDATE_COMMAND)
    }

    @Test
    fun mergeTurnStartParams_preservesOrderAndOverlays() {
        val base =
            JSONValue.Obj(
                linkedMapOf(
                    "threadId" to JSONValue.Str("t1"),
                    "model" to JSONValue.Str("m1"),
                ),
            )
        val merged =
            mergeTurnStartParams(
                base,
                mapOf("approvalPolicy" to JSONValue.Str("never")),
            )
        assertEquals(JSONValue.Str("t1"), merged.map["threadId"])
        assertEquals(JSONValue.Str("m1"), merged.map["model"])
        assertEquals(JSONValue.Str("never"), merged.map["approvalPolicy"])
    }

    @Test
    fun shouldFallbackFromSandboxPolicy_falseForThreadNotFound() {
        val err =
            CodexServiceError.RpcFailure(
                RPCError(-32602, "Thread not found"),
            )
        assertFalse(shouldFallbackFromSandboxPolicy(err))
    }

    @Test
    fun shouldFallbackFromSandboxPolicy_trueForUnknownField() {
        val err =
            CodexServiceError.RpcFailure(
                RPCError(-32602, "unknown field sandboxPolicy"),
            )
        assertTrue(shouldFallbackFromSandboxPolicy(err))
    }

    @Test
    fun runtimeSandboxPolicyObject_matchesAccessMode() {
        val onReq = runtimeSandboxPolicyObject(CodexAccessMode.onRequest)
        assertEquals(JSONValue.Str("workspaceWrite"), onReq.map["type"])
        assertEquals(JSONValue.Bool(true), onReq.map["networkAccess"])
        val full = runtimeSandboxPolicyObject(CodexAccessMode.fullAccess)
        assertEquals(JSONValue.Str("dangerFullAccess"), full.map["type"])
    }

    @Test
    fun shouldWireServiceTier_trueOnlyWhenBridgeSupportsAndUserSelected() {
        assertTrue(shouldWireServiceTier(supportsBridgeServiceTier = true, hasTierSelection = true))
        assertFalse(shouldWireServiceTier(supportsBridgeServiceTier = false, hasTierSelection = true))
        assertFalse(shouldWireServiceTier(supportsBridgeServiceTier = true, hasTierSelection = false))
        assertFalse(shouldWireServiceTier(supportsBridgeServiceTier = false, hasTierSelection = false))
    }

    @Test
    fun shouldRetryTurnStartWithoutServiceTier_trueForServiceTierUnknownField() {
        val err =
            CodexServiceError.RpcFailure(
                RPCError(-32602, "Unknown field serviceTier"),
            )
        assertTrue(shouldRetryTurnStartWithoutServiceTier(err))
    }

    @Test
    fun shouldRetryTurnStartWithoutServiceTier_falseForUnrelatedRpc() {
        val err = CodexServiceError.RpcFailure(RPCError(-32603, "method not found"))
        assertFalse(shouldRetryTurnStartWithoutServiceTier(err))
    }

    @Test
    fun makeTurnInputPayload_appendsStructuredSkillAndMentionItems() {
        val payload =
            makeTurnInputPayload(
                userText = "Use this",
                attachments = emptyList(),
                imageUrlKey = "url",
                skillMentions = listOf(CodexTurnSkillMention(id = "skill-builder", name = "Skill Builder", path = "/skills/skill-builder/SKILL.md")),
                fileMentions = listOf(CodexTurnMention(name = "Main.kt", path = "app/src/main/Main.kt")),
            )

        assertEquals("text", (payload[0] as JSONValue.Obj).map["type"]?.stringValue)
        val skill = (payload[1] as JSONValue.Obj).map
        assertEquals("skill", skill["type"]?.stringValue)
        assertEquals("skill-builder", skill["id"]?.stringValue)
        assertEquals("/skills/skill-builder/SKILL.md", skill["path"]?.stringValue)
        val mention = (payload[2] as JSONValue.Obj).map
        assertEquals("mention", mention["type"]?.stringValue)
        assertEquals("Main.kt", mention["name"]?.stringValue)
    }

    @Test
    fun makeTurnInputPayload_appendsPluginMentionsAsStructuredMentionItems() {
        val payload =
            makeTurnInputPayload(
                userText = "Use Gmail",
                attachments = emptyList(),
                imageUrlKey = "url",
                fileMentions = listOf(CodexTurnMention(name = "gmail", path = "plugin://gmail@openai-curated")),
            )

        val mention = (payload[1] as JSONValue.Obj).map
        assertEquals("mention", mention["type"]?.stringValue)
        assertEquals("gmail", mention["name"]?.stringValue)
        assertEquals("plugin://gmail@openai-curated", mention["path"]?.stringValue)
    }

    @Test
    fun shouldRetryTurnStartWithoutSkillItems_matchesLegacyRuntimeErrors() {
        val err = CodexServiceError.RpcFailure(RPCError(-32602, "unsupported input item type skill"))
        assertTrue(shouldRetryTurnStartWithoutSkillItems(err))
    }

    @Test
    fun structuredItemFallbacks_matchParserStyleBridgeErrors() {
        assertTrue(
            shouldRetryTurnStartWithoutSkillItems(
                CodexServiceError.RpcFailure(RPCError(-32602, "failed to parse skill input item")),
            ),
        )
        assertTrue(
            shouldRetryTurnStartWithoutMentionItems(
                CodexServiceError.RpcFailure(RPCError(-32602, "missing field mention path")),
            ),
        )
        assertTrue(
            shouldRetryTurnStartWithoutServiceTier(
                CodexServiceError.RpcFailure(RPCError(-32602, "failed to parse serviceTier")),
            ),
        )
    }

    @Test
    fun decodeSkillMetadata_parsesFlatDataShape() {
        val skills =
            decodeSkillMetadata(
                JSONValue.Obj(
                    mapOf(
                        "data" to
                            JSONValue.Arr(
                                listOf(
                                    JSONValue.Obj(
                                        mapOf(
                                            "name" to JSONValue.Str("check-code"),
                                            "description" to JSONValue.Str("Audit code changes"),
                                            "path" to JSONValue.Str("/Users/me/.codex/skills/check-code/SKILL.md"),
                                            "scope" to JSONValue.Str("global"),
                                            "enabled" to JSONValue.Bool(true),
                                        ),
                                    ),
                                ),
                            ),
                    ),
                ),
            )

        assertEquals(1, skills?.size)
        assertEquals("check-code", skills?.single()?.name)
        assertEquals("global", skills?.single()?.scope)
    }

    @Test
    fun decodePluginMetadata_parsesMarketplaceShapeAndMentionPath() {
        val plugins =
            decodePluginMetadata(
                JSONValue.Obj(
                    mapOf(
                        "marketplaces" to
                            JSONValue.Arr(
                                listOf(
                                    JSONValue.Obj(
                                        mapOf(
                                            "name" to JSONValue.Str("openai-curated"),
                                            "path" to JSONValue.Str("/marketplaces/openai-curated"),
                                            "plugins" to
                                                JSONValue.Arr(
                                                    listOf(
                                                        JSONValue.Obj(
                                                            mapOf(
                                                                "id" to JSONValue.Str("gmail-id"),
                                                                "name" to JSONValue.Str("gmail"),
                                                                "installed" to JSONValue.Bool(true),
                                                                "enabled" to JSONValue.Bool(false),
                                                                "installPolicy" to JSONValue.Str("USER"),
                                                                "interface" to
                                                                    JSONValue.Obj(
                                                                        mapOf(
                                                                            "displayName" to JSONValue.Str("Gmail"),
                                                                            "shortDescription" to JSONValue.Str("Work with Gmail"),
                                                                        ),
                                                                    ),
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                        ),
                                    ),
                                ),
                            ),
                    ),
                ),
            )

        val plugin = plugins?.single()
        assertEquals("gmail", plugin?.name)
        assertEquals("Gmail", plugin?.displayTitle)
        assertEquals("plugin://gmail@openai-curated", plugin?.mentionPath)
    }

    @Test
    fun mentionablePluginMetadata_filtersDedupesAndSorts() {
        val plugins =
            mentionablePluginMetadata(
                listOf(
                    CodexPluginMetadata(
                        id = "z",
                        name = "zplugin",
                        marketplaceName = "openai-curated",
                        displayName = "Zed",
                        installed = false,
                        enabled = false,
                    ),
                    CodexPluginMetadata(
                        id = "gmail-1",
                        name = "gmail",
                        marketplaceName = "openai-curated",
                        displayName = "Gmail",
                        installed = true,
                    ),
                    CodexPluginMetadata(
                        id = "gmail-2",
                        name = "gmail",
                        marketplaceName = "openai-curated",
                        displayName = "Gmail Duplicate",
                        enabled = true,
                    ),
                    CodexPluginMetadata(
                        id = "calendar",
                        name = "calendar",
                        marketplaceName = "openai-curated",
                        displayName = "Calendar",
                        installPolicy = "INSTALLED_BY_DEFAULT",
                    ),
                ),
            )

        assertEquals(listOf("Calendar", "Gmail"), plugins.map { it.displayTitle })
        assertEquals(listOf("plugin://calendar@openai-curated", "plugin://gmail@openai-curated"), plugins.map { it.mentionPath })
    }

    @Test
    fun pluginNormalizedDiscoveryText_matchesSeparatorVariants() {
        val plugin =
            CodexPluginMetadata(
                id = "gmail",
                name = "gmail_plugin",
                marketplaceName = "openai-curated",
                displayName = "Gmail Plugin",
                shortDescription = "Mail connector",
                installed = true,
            )

        assertTrue(plugin.matchesSearch("openai-curated"))
        assertTrue(plugin.matchesSearch("gmail-plugin"))
        assertTrue(plugin.matchesSearch("mail_connector"))
    }
}
