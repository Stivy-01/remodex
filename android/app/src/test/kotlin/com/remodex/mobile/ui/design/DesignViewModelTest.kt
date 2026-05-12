package com.remodex.mobile.ui.design

import com.remodex.mobile.ui.design.data.DesignRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class DesignViewModelTest {
    @Test
    fun submitPrompt_cancelsPriorGenerationBeforeStartingNewOne() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val repository = RecordingDesignRepository()
                val viewModel = DesignViewModel(repository)

                viewModel.onPromptTextChanged("first")
                viewModel.onSubmitPrompt()
                advanceTimeBy(1_000)

                viewModel.onPromptTextChanged("second")
                viewModel.onSubmitPrompt()
                advanceUntilIdle()

                assertEquals(listOf("second"), repository.generatedPrompts)
                assertEquals("doc-second", viewModel.currentDocument.value?.id)
                assertEquals("done", viewModel.generationState.value.status)
            } finally {
                Dispatchers.resetMain()
            }
        }
}

private class RecordingDesignRepository : DesignRepository {
    val generatedPrompts = mutableListOf<String>()

    override suspend fun generateDesign(
        projectId: String,
        prompt: String,
        target: String?,
    ): GenerationState {
        generatedPrompts += prompt
        return GenerationState(
            generationId = "gen-$prompt",
            status = "done",
            documentId = "doc-$prompt",
            documentVersion = generatedPrompts.size,
            snapshotUrl = "snapshot-$prompt",
        )
    }

    override suspend fun getGenerationStatus(generationId: String): GenerationState = error("unused")

    override suspend fun getDocument(documentId: String): DesignDocument = error("unused")

    override suspend fun editDocument(
        documentId: String,
        prompt: String,
        selectedNodeId: String?,
    ): GenerationState = error("unused")

    override suspend fun exportDocument(
        documentId: String,
        target: ExportTarget,
    ): ExportResult = error("unused")
}
