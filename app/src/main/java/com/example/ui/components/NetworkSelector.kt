package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R

data class NetworkProvider(
    val id: String,          // Gsubz airtime serviceID — exact case matters
    val displayName: String,
    val logoRes: Int
)

val networkProviders = listOf(
    NetworkProvider(id = "mtn", displayName = "MTN", logoRes = R.drawable.ic_provider_mtn),
    NetworkProvider(id = "airtel", displayName = "Airtel", logoRes = R.drawable.ic_provider_airtel),
    NetworkProvider(id = "glo", displayName = "GLO", logoRes = R.drawable.ic_provider_glo),
    NetworkProvider(id = "etisalat", displayName = "9mobile", logoRes = R.drawable.ic_provider_9mobile)
)

@Composable
fun NetworkLogoIcon(
    provider: com.example.data.model.NetworkProvider,
    size: Dp = 32.dp,
    isCircular: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = if (isCircular) CircleShape else RoundedCornerShape(10.dp)
    val bgColor = when (provider) {
        com.example.data.model.NetworkProvider.MTN -> Color(0xFFFFCB05)
        com.example.data.model.NetworkProvider.GLO -> Color(0xFF188832)
        com.example.data.model.NetworkProvider.NINEMOBILE -> Color.White
        com.example.data.model.NetworkProvider.AIRTEL -> Color(0xFFE40000)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(bgColor)
            .border(
                width = if (provider == com.example.data.model.NetworkProvider.NINEMOBILE) 1.dp else 0.5.dp,
                color = if (provider == com.example.data.model.NetworkProvider.NINEMOBILE)
                    Color(0xFF0A6847).copy(alpha = 0.35f)
                else
                    Color.White.copy(alpha = 0.25f),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = provider.logoRes),
            contentDescription = provider.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun PhoneInputNetworkPrefix(
    selectedNetwork: com.example.data.model.NetworkProvider,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable { onClick() }
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
    ) {
        NetworkLogoIcon(
            provider = selectedNetwork,
            size = 30.dp,
            isCircular = true
        )
        Spacer(modifier = Modifier.width(2.dp))
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = "Select Network",
            tint = Color(0xFF00C853),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(22.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

@Composable
fun NetworkPickerDialog(
    selectedNetwork: com.example.data.model.NetworkProvider,
    onSelectNetwork: (com.example.data.model.NetworkProvider) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1C1C1E),
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                com.example.data.model.NetworkProvider.entries.forEachIndexed { index, network ->
                    val isSelected = network == selectedNetwork
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelectNetwork(network)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NetworkLogoIcon(
                                provider = network,
                                size = 40.dp,
                                isCircular = true
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = network.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onSelectNetwork(network)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF00C853),
                                unselectedColor = Color.Gray
                            )
                        )
                    }
                    if (index < com.example.data.model.NetworkProvider.entries.lastIndex) {
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.07f),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkSelector(
    providers: List<NetworkProvider> = networkProviders,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(providers) { provider ->
            val isSelected = provider.id == selectedId
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(provider.id) }
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                Image(
                    painter = painterResource(id = provider.logoRes),
                    contentDescription = provider.displayName,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.height(4.dp))
                Text(provider.displayName, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
