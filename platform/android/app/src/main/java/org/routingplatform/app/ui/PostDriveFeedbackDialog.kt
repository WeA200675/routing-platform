package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.routingplatform.app.ai.RouteFeedbackQuestion

@Composable
internal fun PostDriveFeedbackDialog(
    question: RouteFeedbackQuestion,
    routeSummary: String,
    onAnswer: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(20.dp).heightIn(max = 600.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Fahrt abgeschlossen", style = MaterialTheme.typography.titleLarge)
                Text(routeSummary, style = MaterialTheme.typography.bodyMedium)
                Text(question.prompt, style = MaterialTheme.typography.titleMedium)
                question.options.forEachIndexed { index, option ->
                    val action = { onAnswer(index.toString()) }
                    if (index == 0) {
                        Button(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(option) }
                    } else {
                        OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(option) }
                    }
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss) { Text("Jetzt nicht") }
                }
            }
        }
    }
}
