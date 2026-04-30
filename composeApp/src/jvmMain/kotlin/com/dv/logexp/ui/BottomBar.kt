package com.dv.logexp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

@Composable
fun BottomBar(
    initialAppFilter: String,
    logCount: Int,
    onSearchChange: (String) -> Unit,
    onAppFilterChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.padding(8.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val searchState = rememberTextFieldState("")
        val appFilterState = rememberTextFieldState(initialAppFilter)

        LaunchedEffect(searchState.text) {
            onSearchChange(searchState.text.toString())
        }

        LaunchedEffect(appFilterState.text) {
            onAppFilterChange(appFilterState.text.toString())
        }

        TextField(
            state = searchState,
            placeholder = { Text("Search logs...") },
            modifier = Modifier.weight(1f),
        )
        TextField(
            state = appFilterState,
            placeholder = { Text("App/PID...") },
            modifier = Modifier.width(200.dp),
        )
        Text(
            text = "Logs: $logCount",
            fontSize = 12.sp,
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(start = 8.dp).widthIn(min = 100.dp),
        )
    }
}
