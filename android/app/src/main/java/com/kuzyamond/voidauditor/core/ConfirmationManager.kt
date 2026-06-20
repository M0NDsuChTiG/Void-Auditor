package com.kuzyamond.voidauditor.core

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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
        onCancel: () -> Unit = {}
    ) {
        currentRequest = ConfirmationRequest(intent, onConfirm, onCancel)
    }

    fun dismiss() {
        currentRequest = null
    }
}

data class ConfirmationRequest(
    val intent: Capability,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit
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
            androidx.compose.foundation.layout.Column {
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    request.onConfirm()
                    ConfirmationManager.dismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = cyberWarning)
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
