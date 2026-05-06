package com.remodex.mobile.ui.design.data

import com.remodex.mobile.ui.design.DesignDocument
import com.remodex.mobile.ui.design.DesignDocumentStatus
import com.remodex.mobile.ui.design.ExportFile
import com.remodex.mobile.ui.design.ExportResult
import com.remodex.mobile.ui.design.ExportTarget
import com.remodex.mobile.ui.design.GenerationState
import com.remodex.mobile.ui.design.GenerationStep
import com.remodex.mobile.ui.design.GenerationStepStatus
import kotlinx.coroutines.delay

class MockDesignRepository : DesignRepository {

    override suspend fun generateDesign(
        projectId: String,
        prompt: String,
        target: String?,
    ): GenerationState {
        val genId = "gen_${System.currentTimeMillis()}"
        val docId = "doc_${System.currentTimeMillis()}"
        val snapshotUrl = "https://picsum.photos/seed/${docId}/800/600"

        val steps = listOf(
            GenerationStep("Understanding prompt", GenerationStepStatus.PENDING),
            GenerationStep("Creating layout", GenerationStepStatus.PENDING),
            GenerationStep("Adding components", GenerationStepStatus.PENDING),
            GenerationStep("Styling screen", GenerationStepStatus.PENDING),
            GenerationStep("Rendering preview", GenerationStepStatus.PENDING),
            GenerationStep("Creating snapshot", GenerationStepStatus.PENDING),
        )

        val stepLabels = steps.map { it.label }
        for (i in stepLabels.indices) {
            delay(800)
        }

        return GenerationState(
            generationId = genId,
            status = "done",
            steps = steps.map { GenerationStep(it.label, GenerationStepStatus.DONE) },
            documentId = docId,
            documentVersion = 1,
            snapshotUrl = snapshotUrl,
        )
    }

    override suspend fun getGenerationStatus(generationId: String): GenerationState {
        return GenerationState(
            generationId = generationId,
            status = "done",
            steps = listOf(),
        )
    }

    override suspend fun getDocument(documentId: String): DesignDocument {
        return DesignDocument(
            id = documentId,
            projectId = "mock_project",
            version = 1,
            opFileUrl = null,
            localOpJson = null,
            snapshotUrl = "https://picsum.photos/seed/${documentId}/800/600",
            thumbnailUrl = null,
            status = DesignDocumentStatus.READY,
        )
    }

    override suspend fun editDocument(
        documentId: String,
        prompt: String,
        selectedNodeId: String?,
    ): GenerationState {
        delay(1200)
        val newVersion = 2
        val snapshotUrl = "https://picsum.photos/seed/${documentId}_v${newVersion}/800/600"

        return GenerationState(
            generationId = "edit_${System.currentTimeMillis()}",
            status = "done",
            steps = listOf(
                GenerationStep("Applying edit", GenerationStepStatus.DONE),
                GenerationStep("Rendering preview", GenerationStepStatus.DONE),
            ),
            documentId = documentId,
            documentVersion = newVersion,
            snapshotUrl = snapshotUrl,
        )
    }

    override suspend fun exportDocument(
        documentId: String,
        target: ExportTarget,
    ): ExportResult {
        delay(800)

        val files = when (target) {
            ExportTarget.JETPACK_COMPOSE -> listOf(
                ExportFile(
                    path = "OnboardingScreen.kt",
                    language = "kotlin",
                    content = composeStub(),
                ),
            )
            ExportTarget.REACT_NATIVE -> listOf(
                ExportFile(
                    path = "OnboardingScreen.tsx",
                    language = "typescript",
                    content = reactNativeStub(),
                ),
            )
            ExportTarget.FLUTTER -> listOf(
                ExportFile(
                    path = "onboarding_screen.dart",
                    language = "dart",
                    content = flutterStub(),
                ),
            )
            ExportTarget.REACT_TAILWIND -> listOf(
                ExportFile(
                    path = "OnboardingScreen.tsx",
                    language = "typescript",
                    content = reactTailwindStub(),
                ),
            )
            ExportTarget.HTML_CSS -> listOf(
                ExportFile(
                    path = "index.html",
                    language = "html",
                    content = htmlCssStub(),
                ),
            )
        }

        return ExportResult(
            exportId = "exp_${System.currentTimeMillis()}",
            files = files,
        )
    }

    private fun composeStub(): String = """
@Composable
fun OnboardingScreen(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Welcome",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onGetStarted) {
            Text("Get Started")
        }
    }
}
    """.trimIndent()

    private fun reactNativeStub(): String = """
import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';

export default function OnboardingScreen({ onGetStarted }) {
  return (
    <View style={styles.container}>
      <Text style={styles.title}>Welcome</Text>
      <TouchableOpacity style={styles.button} onPress={onGetStarted}>
        <Text style={styles.buttonText}>Get Started</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24 },
  title: { fontSize: 24, fontWeight: '600', marginBottom: 16 },
  button: { backgroundColor: '#000', paddingHorizontal: 24, paddingVertical: 12, borderRadius: 8 },
  buttonText: { color: '#fff', fontSize: 16 },
});
    """.trimIndent()

    private fun flutterStub(): String = """
import 'package:flutter/material.dart';

class OnboardingScreen extends StatelessWidget {
  final VoidCallback onGetStarted;
  const OnboardingScreen({super.key, required this.onGetStarted});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text('Welcome', style: Theme.of(context).textTheme.headlineMedium),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: onGetStarted,
              child: const Text('Get Started'),
            ),
          ],
        ),
      ),
    );
  }
}
    """.trimIndent()

    private fun reactTailwindStub(): String = """
export default function OnboardingScreen({ onGetStarted }) {
  return (
    <div className="flex flex-col items-center justify-center min-h-screen p-6">
      <h1 className="text-2xl font-semibold mb-4">Welcome</h1>
      <button
        className="bg-black text-white px-6 py-3 rounded-lg text-base"
        onClick={onGetStarted}
      >
        Get Started
      </button>
    </div>
  );
}
    """.trimIndent()

    private fun htmlCssStub(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Onboarding</title>
  <style>
    body { display:flex; align-items:center; justify-content:center; min-height:100vh; margin:0; font-family:system-ui; }
    .container { text-align:center; padding:24px; }
    h1 { font-size:24px; margin-bottom:16px; }
    button { background:#000; color:#fff; border:none; padding:12px 24px; border-radius:8px; font-size:16px; cursor:pointer; }
  </style>
</head>
<body>
  <div class="container">
    <h1>Welcome</h1>
    <button onclick="alert('Get Started')">Get Started</button>
  </div>
</body>
</html>
    """.trimIndent()
}
