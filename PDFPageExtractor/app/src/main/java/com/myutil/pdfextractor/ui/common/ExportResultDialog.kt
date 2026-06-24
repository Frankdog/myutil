package com.myutil.pdfextractor.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ExportResultDialog(
    message: String,
    onDismiss: () -> Unit,
    onShare: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (message.contains("成功")) "完成" else "提示")
        },
        text = {
            Text(message)
        },
        confirmButton = {
            Row {
                if (onSave != null) {
                    TextButton(onClick = onSave) {
                        Text("保存到...")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                if (onShare != null) {
                    TextButton(onClick = onShare) {
                        Text("分享")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text("确定")
                }
            }
        }
    )
}
