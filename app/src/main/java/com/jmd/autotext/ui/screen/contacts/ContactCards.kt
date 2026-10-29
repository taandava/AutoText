/**
 * Package: com.jmd.autotext.ui.screen.contacts
 * Module: UI Contacts Cards & Display Items
 *
 * Dedicated UI presentation card for saved custom and CSV contacts.
 * Features inline sequence creation triggers, contact avatar badges, and one-tap edit/delete actions.
 */
package com.jmd.autotext.ui.screen.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.data.model.RecipientItem
import com.jmd.autotext.ui.components.appCard
import com.jmd.autotext.ui.components.contactAvatar
import com.jmd.autotext.ui.theme.AppDesignTokens

/**
 * Individual contact item card with avatar, name, phone, sequence creation trigger, and delete action.
 *
 * @param recipient The [RecipientItem] data model to display.
 * @param onEdit Callback invoked to open the contact edit sheet.
 * @param onCreateSequence Callback invoked to launch contact sequence composer.
 * @param onDelete Callback invoked to remove the recipient.
 * @param modifier Layout [Modifier].
 */
@Composable
fun bulkContactItemCard(
    recipient: RecipientItem,
    onEdit: () -> Unit,
    onCreateSequence: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    appCard(
        onClick = onEdit,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                contactAvatar(
                    name = recipient.name.ifBlank { recipient.phoneOrEmail },
                    size = 36.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recipient.name,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = recipient.phoneOrEmail,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onCreateSequence,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.AltRoute,
                        contentDescription = "Create Sequence",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Contact",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Contact",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
