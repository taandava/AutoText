package com.jmd.autotext.ui.screens.more.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmd.autotext.model.RecipientItem
import com.jmd.autotext.ui.components.BentoCard
import com.jmd.autotext.ui.theme.BentoGlassTokens

@Composable
fun BulkContactItemCard(
    recipient: RecipientItem,
    onEdit: () -> Unit,
    onCreateSequence: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    BentoCard(
        onClick = onEdit,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                com.jmd.autotext.ui.components.BentoAvatar(
                    name = recipient.name.ifBlank { recipient.phoneOrEmail },
                    size = 36.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = recipient.name,
                        fontWeight = FontWeight.Bold,
                        color = BentoGlassTokens.TextPrimary,
                        fontSize = 13.5.sp
                    )
                    Text(
                        text = recipient.phoneOrEmail,
                        color = BentoGlassTokens.NeonCyan,
                        fontSize = 12.sp
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
                        tint = BentoGlassTokens.ElectricIndigo,
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
                        tint = BentoGlassTokens.NeonCyan,
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
                        tint = BentoGlassTokens.CyberRose,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
