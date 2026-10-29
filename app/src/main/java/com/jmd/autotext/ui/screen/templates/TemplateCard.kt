/**
 * Package: com.jmd.autotext.ui.screen.templates
 * Module: UI Message Template Card Item
 *
 * Interactive presentation card displaying an individual message template with title, category,
 * preview text snippet, edit button, and delete action.
 */
package com.jmd.autotext.ui.screen.templates

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.jmd.autotext.data.model.MessageTemplate
import com.jmd.autotext.ui.components.appCard
import com.jmd.autotext.ui.theme.AppDesignTokens

/**
 * Interactive card displaying an individual message template item.
 *
 * @param template The [MessageTemplate] entity to render.
 * @param onEdit Callback invoked when user taps the card or edit button.
 * @param onDelete Callback invoked to remove the template from database.
 * @param modifier Layout [Modifier].
 */
@Composable
fun templateItemCard(
    template: MessageTemplate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    appCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onEdit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "📄 ${template.title}",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(28.dp),
                ) {
                    Text("✏️", fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp),
                ) {
                    Text("🗑️", fontSize = 15.sp)
                }
            }
        }
        Text(
            template.content,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.5.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Text(
            "Category: ${template.category}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}
