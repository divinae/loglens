package com.dv.logexp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

@Composable
fun QuickFilterBar(
    onSearchChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = 8.dp).padding(horizontal = 8.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val searchState = rememberTextFieldState("")

        LaunchedEffect(searchState.text) {
            onSearchChange(searchState.text.toString())
        }

        TextField(
            state = searchState,
            placeholder = { Text("Filter logs...") },
            modifier = Modifier.weight(1f),
        )
    }
}
