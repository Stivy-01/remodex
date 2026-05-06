package com.remodex.mobile.services

import com.remodex.mobile.core.error.CodexServiceError
import com.remodex.mobile.core.model.CodexAccessMode
import com.remodex.mobile.core.model.CodexModelOption
import com.remodex.mobile.core.model.CodexServiceTier
import com.remodex.mobile.core.model.JSONValue
import com.remodex.mobile.core.persistence.RuntimeSelectionSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun CodexService.refreshModelsInternal() {
    if (!sessionReady) throw CodexServiceError.Disconnected
    _isLoadingModels.value = true
    try {
        val response =
            sendRequestImpl(
                method = "model/list",
                params =
                    JSONValue.Obj(
                        mapOf(
                            "cursor" to JSONValue.Null,
                            "limit" to JSONValue.NumLong(50),
                            "includeHidden" to JSONValue.Bool(false),
                        ),
                    ),
            )
        val result = response.result?.objectValue
            ?: throw CodexServiceError.InvalidResponse("model/list missing result")
        val items =
            result["items"]?.arrayValue
                ?: result["data"]?.arrayValue
                ?: result["models"]?.arrayValue
                ?: emptyList()
        val decoded =
            items.mapNotNull { item ->
                val obj = item as? JSONValue.Obj ?: return@mapNotNull null
                runCatching { CodexModelOption.fromJsonObject(jsonObjectFromRpc(obj)) }.getOrNull()
            }.filter { it.model.isNotBlank() || it.id.isNotBlank() }

        _availableModels.value = decoded
        _modelsErrorMessage.value = null
        normalizeRuntimeSelectionsAfterModelsUpdate()
    } catch (e: Throwable) {
        _modelsErrorMessage.value = e.message ?: e.javaClass.simpleName
        throw e
    } finally {
        _isLoadingModels.value = false
    }
}

suspend fun CodexService.refreshModelsForRepository() =
    withContext(Dispatchers.IO) {
        refreshModelsInternal()
    }

suspend fun CodexService.setSelectedModelIdForRepository(modelId: String?) =
    withContext(Dispatchers.IO) {
        _selectedModelId.value = modelId?.trim()?.takeIf { it.isNotEmpty() }
        normalizeRuntimeSelectionsAfterModelsUpdate()
        persistRuntimeSelection()
    }

suspend fun CodexService.setSelectedReasoningEffortForRepository(reasoningEffort: String?) =
    withContext(Dispatchers.IO) {
        _selectedReasoningEffort.value = reasoningEffort?.trim()?.takeIf { it.isNotEmpty() }
        normalizeRuntimeSelectionsAfterModelsUpdate()
        persistRuntimeSelection()
    }

suspend fun CodexService.setSelectedAccessModeForRepository(accessMode: CodexAccessMode) =
    withContext(Dispatchers.IO) {
        _selectedAccessMode.value = accessMode
        persistRuntimeSelection()
    }

suspend fun CodexService.setSelectedServiceTierForRepository(serviceTier: CodexServiceTier?) =
    withContext(Dispatchers.IO) {
        _selectedServiceTier.value = serviceTier
        persistRuntimeSelection()
    }

internal fun CodexService.selectedModelOption(): CodexModelOption? {
    val models = _availableModels.value
    val selected = _selectedModelId.value?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        models.isEmpty() -> null
        selected != null -> models.firstOrNull { it.id == selected || it.model == selected }
        else -> null
    } ?: models.firstOrNull { it.isDefault } ?: models.firstOrNull()
}

internal fun CodexService.runtimeModelIdentifierForTurn(threadId: String): String? =
    selectedModelOption()?.model?.trim()?.takeIf { it.isNotEmpty() }
        ?: _threads.value.firstOrNull { it.id == threadId }?.model?.trim()?.takeIf { it.isNotEmpty() }

internal fun CodexService.selectedReasoningEffortForSelectedModel(): String? {
    val model = selectedModelOption() ?: return null
    val supported = model.supportedReasoningEfforts.map { it.reasoningEffort }.filter { it.isNotBlank() }
    if (supported.isEmpty()) return null

    val selected = _selectedReasoningEffort.value?.trim()?.takeIf { it.isNotEmpty() }
    if (selected != null && supported.contains(selected)) return selected

    val defaultEffort = model.defaultReasoningEffort?.trim()?.takeIf { it.isNotEmpty() }
    if (defaultEffort != null && supported.contains(defaultEffort)) return defaultEffort

    return if (supported.contains("medium")) "medium" else supported.firstOrNull()
}

internal fun CodexService.normalizeRuntimeSelectionsAfterModelsUpdate() {
    val models = _availableModels.value
    if (models.isEmpty()) {
        persistRuntimeSelection()
        return
    }

    val selected = _selectedModelId.value?.trim()?.takeIf { it.isNotEmpty() }
    val resolvedModel =
        selected?.let { id -> models.firstOrNull { it.id == id || it.model == id } }
            ?: models.firstOrNull { it.isDefault }
            ?: models.firstOrNull()
    _selectedModelId.value = resolvedModel?.id?.takeIf { it.isNotBlank() }

    val supported = resolvedModel?.supportedReasoningEfforts?.map { it.reasoningEffort } ?: emptyList()
    val selectedReasoning = _selectedReasoningEffort.value?.trim()?.takeIf { it.isNotEmpty() }
    if (selectedReasoning != null && !supported.contains(selectedReasoning)) {
        _selectedReasoningEffort.value = null
    }

    persistRuntimeSelection()
}

internal fun CodexService.persistRuntimeSelection() {
    sessionPersistence.saveRuntimeSelection(
        RuntimeSelectionSnapshot(
            selectedModelId = _selectedModelId.value,
            selectedReasoningEffort = _selectedReasoningEffort.value,
            selectedAccessMode = _selectedAccessMode.value.name,
            selectedServiceTier = _selectedServiceTier.value?.name,
        ),
    )
}
