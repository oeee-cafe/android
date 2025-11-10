package cafe.oeee.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.oeee.R

enum class DrawingTool(val value: String, val displayName: String) {
    NEO("neo", "PaintBBS NEO"),
    TEGAKI("tegaki", "Tegaki"),
    NEO_CUCUMBER_OFFLINE("neo-cucumber-offline", "Neo Cucumber Offline")
}

data class CanvasDimensions(
    val width: Int,
    val height: Int,
    val tool: DrawingTool
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasDimensionPicker(
    onDimensionsSelected: (CanvasDimensions) -> Unit,
    onCancel: () -> Unit,
    backgroundColor: String? = null,
    foregroundColor: String? = null
) {
    var selectedWidth by remember { mutableIntStateOf(300) }
    var selectedHeight by remember { mutableIntStateOf(300) }
    var selectedTool by remember { mutableStateOf(DrawingTool.NEO) }

    val hasDefinedColors = backgroundColor != null && foregroundColor != null
    val availableTools = DrawingTool.entries.filter { it != DrawingTool.NEO_CUCUMBER_OFFLINE }
    val availableWidths = (300..1000 step 50).toList()
    val availableHeights = (300..800 step 50).toList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.draw_select_canvas_size)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Tool selection (hidden when community has defined colors)
            if (!hasDefinedColors) {
                Column {
                    Text(
                        text = stringResource(R.string.draw_drawing_tool),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        availableTools.forEachIndexed { index, tool ->
                            SegmentedButton(
                                selected = selectedTool == tool,
                                onClick = { selectedTool = tool },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = availableTools.size
                                )
                            ) {
                                Text(tool.displayName)
                            }
                        }
                    }
                }
            }

            // Width selection
            Column {
                Text(
                    text = stringResource(R.string.draw_width),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                DimensionDropdown(
                    label = stringResource(R.string.draw_width),
                    values = availableWidths,
                    selectedValue = selectedWidth,
                    onValueChange = { selectedWidth = it }
                )
            }

            // Height selection
            Column {
                Text(
                    text = stringResource(R.string.draw_height),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                DimensionDropdown(
                    label = stringResource(R.string.draw_height),
                    values = availableHeights,
                    selectedValue = selectedHeight,
                    onValueChange = { selectedHeight = it }
                )
            }

            // Preview
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.draw_preview),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "$selectedWidth × $selectedHeight",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action buttons
            Button(
                onClick = {
                    onDimensionsSelected(
                        CanvasDimensions(
                            width = selectedWidth,
                            height = selectedHeight,
                            tool = selectedTool
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.draw_start_drawing))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DimensionDropdown(
    label: String,
    values: List<Int>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedValue.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(type = androidx.compose.material3.MenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value.toString()) },
                    onClick = {
                        onValueChange(value)
                        expanded = false
                    }
                )
            }
        }
    }
}
