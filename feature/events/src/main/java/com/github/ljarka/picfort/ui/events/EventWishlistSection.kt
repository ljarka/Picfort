package com.github.ljarka.picfort.ui.events

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.ljarka.picfort.core.model.WishlistItem
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency

@Composable
internal fun EventWishlistSection(
    items: List<WishlistItem>,
    memberNames: Map<String, String>,
    myId: String,
    onToggleBought: (WishlistItem) -> Unit,
    onToggleReserve: (WishlistItem) -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            text = "Gift ideas & Wishlist",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.id }) { item ->
                var menu by remember { mutableStateOf(false) }
                val hideSecret = item.targetMemberId == myId
                val reservedName = item.reservedBy?.let(memberNames::get)
                Card(
                    modifier = Modifier
                        .width(164.dp)
                        .alpha(if (item.bought) 0.6f else 1f)
                        .clickable {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                            }
                        },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            if (!item.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.imageUrl,
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = item.title.take(1).uppercase(),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Box(Modifier.align(Alignment.TopEnd)) {
                                IconButton(onClick = { menu = true }, modifier = Modifier.size(30.dp)) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "Wishlist actions",
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (item.bought) "Mark as not bought" else "Mark as bought") },
                                        onClick = {
                                            menu = false
                                            onToggleBought(item)
                                        },
                                    )
                                    if (!hideSecret && !item.bought) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (item.reservedBy ==
                                                        null
                                                    ) {
                                                        "Reserve gift"
                                                    } else {
                                                        "Release reservation"
                                                    },
                                                )
                                            },
                                            onClick = {
                                                menu = false
                                                onToggleReserve(item)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (item.bought) TextDecoration.LineThrough else TextDecoration.None,
                        )
                        if (item.priceMinor != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = formatWishlistPrice(item.priceMinor!!, item.currency),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (!hideSecret && reservedName != null && !item.bought) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Reserved by $reservedName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatWishlistPrice(minor: Long, currencyCode: String): String {
    val amount = BigDecimal.valueOf(minor, 2)
    val currency = runCatching { Currency.getInstance(currencyCode) }.getOrNull()
        ?: return "${amount.toPlainString()} $currencyCode"
    return NumberFormat.getCurrencyInstance().apply {
        this.currency = currency
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(amount)
}
