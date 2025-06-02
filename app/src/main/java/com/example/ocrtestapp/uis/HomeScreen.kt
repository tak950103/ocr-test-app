package com.example.ocrtestapp.uis

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun HomeScreen(navController: NavController) {
    var ocrText by remember { mutableStateOf("")}
    var barcodeText by remember { mutableStateOf("") }

    val currentBackStackEntry = navController.currentBackStackEntryAsState().value

    // LaunchedEffect で状態更新（1回限り）
    LaunchedEffect(currentBackStackEntry) {
        currentBackStackEntry?.savedStateHandle?.get<String>("ocr_result")?.let {
            ocrText = it
            currentBackStackEntry.savedStateHandle.remove<String>("ocr_result")
        }
        currentBackStackEntry?.savedStateHandle?.get<String>("barcode_result")?.let {
            barcodeText = it
            currentBackStackEntry.savedStateHandle.remove<String>("barcode_result")
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("OCR 文字認識用")
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = ocrText,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {navController.navigate("ocr")}) {
                Text("📷")
            }
        }

        Spacer (modifier = Modifier.height(32.dp))

        Text("バーコードリーダー用")
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = barcodeText,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {navController.navigate("barcode")}) {
                Text("📷")
            }
        }
    }
}

