package com.kuzyamond.voidauditor.core

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ConfirmationManager {
    var currentRequest by mutableStateOf<ConfirmationRequest?>(null)
        private set

    fun requestConfirmation(
        intent: Capability,
        onConfirm: () -> Unit,
        onCancel: () -> Unit = {},
        requiredPhrase: String? = null
    ) {
        currentRequest = ConfirmationRequest(intent, onConfirm, onCancel, requiredPhrase)
    }

    fun dismiss() {
        currentRequest = null
    }
}

data class ConfirmationRequest(
    val intent: Capability,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit,
    val requiredPhrase: String? = null
)

@Composable
fun ConfirmationDialog(
    cyberBackground: Color,
    cyberSurface: Color,
    cyberAccent: Color,
    cyberWarning: Color,
    cyberText: Color
) {
    val request = ConfirmationManager.currentRequest ?: return
    val riskLabel = PolicyEngine.severityLabel(request.intent.riskScore)
    val riskColor = when (riskLabel) {
        "CRITICAL" -> Color(0xFFFF2D55)
        "HIGH" -> Color(0xFFFF9500)
        "MEDIUM" -> Color(0xFFFFCC00)
        else -> cyberAccent
    }
    val phrase = request.requiredPhrase
    var typed by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = {
            request.onCancel()
            ConfirmationManager.dismiss()
        },
        containerColor = cyberSurface,
        titleContentColor = cyberAccent,
        textContentColor = cyberText,
        title = {
            Text(
                "> CONFIRM_ACTION",
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        },
        text = {
            Column {
                Text(
                    "RISK: $riskLabel",
                    color = riskColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    request.intent.description,
                    color = cyberText,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (phrase != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "TYPE '$phrase' TO CONFIRM DESTRUCTIVE ACTION:",
                        color = cyberWarning,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { value -> typed = value },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = cyberText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    request.onConfirm()
                    ConfirmationManager.dismiss()
                },
                enabled = request.requiredPhrase == null || typed == request.requiredPhrase,
                colors = ButtonDefaults.buttonColors(
                    containerColor = cyberWarning,
                    disabledContainerColor = cyberWarning.copy(alpha = 0.3f),
                    disabledContentColor = Color.Gray
                )
            ) {
                Text("EXECUTE", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                request.onCancel()
                ConfirmationManager.dismiss()
            }) {
                Text("CANCEL", color = cyberAccent, fontWeight = FontWeight.Bold)
            }
        }
    )
}
