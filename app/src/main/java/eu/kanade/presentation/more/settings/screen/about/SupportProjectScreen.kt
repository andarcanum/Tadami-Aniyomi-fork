package eu.kanade.presentation.more.settings.screen.about

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.AURORA_SETTINGS_CARD_HORIZONTAL_INSET
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.canScroll
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.more.settings.widget.AuroraSettingsCard
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.auroraFloatingSurface
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

class SupportProjectScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val navigator = LocalNavigator.currentOrThrow
        val handleBack = LocalBackPress.current
        val uiStyle = rememberResolvedSettingsUiStyle()
        val auroraColors = AuroraTheme.colors

        val options = remember(context) { DonationOptionsHelper.loadFromAssets(context) }
        val state = rememberLazyListState()

        val backAction: () -> Unit = handleBack ?: { navigator.pop() }

        SettingsScaffold(
            title = stringResource(MR.strings.support_project),
            uiStyle = uiStyle,
            onBackPressed = backAction,
            topBarCanScroll = { state.canScroll() },
        ) { contentPadding ->
            LazyColumn(
                state = state,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    SupportIntroCard(
                        auroraColors = auroraColors,
                        modifier = Modifier
                            .padding(horizontal = AURORA_SETTINGS_CARD_HORIZONTAL_INSET)
                            .padding(top = 12.dp),
                    )
                }

                items(options, key = { it.id }) { option ->
                    val icon = when (option.id) {
                        "cloudtips" -> CloudTipsIcon
                        "boosty" -> BoostyIcon
                        "crypto_usdt_tron" -> UsdtIcon
                        "crypto_usdt" -> TonIcon
                        else -> Icons.Outlined.Favorite
                    }

                    val brandColor = when (option.id) {
                        "cloudtips" -> if (auroraColors.isDark) Color(0xFF00C4B4) else Color(0xFF009B8E)
                        "boosty" -> if (auroraColors.isDark) Color(0xFFF15F2C) else Color(0xFFD84B1A)
                        "crypto_usdt_tron" -> if (auroraColors.isDark) Color(0xFF009393) else Color(0xFF007575)
                        "crypto_usdt" -> if (auroraColors.isDark) Color(0xFF4DB8FF) else Color(0xFF0088CC)
                        else -> auroraColors.accent
                    }

                    DonationOptionCard(
                        option = option,
                        brandColor = brandColor,
                        icon = icon,
                        auroraColors = auroraColors,
                        context = context,
                        uriHandler = uriHandler,
                        modifier = Modifier.padding(horizontal = AURORA_SETTINGS_CARD_HORIZONTAL_INSET),
                    )
                }
            }
        }
    }
}

@Composable
private fun SupportIntroCard(
    auroraColors: AuroraColors,
    modifier: Modifier = Modifier,
) {
    if (auroraColors.isDark) {
        val shape = RoundedCornerShape(24.dp)
        val surfaceColor = Color.White.copy(alpha = 0.05f)
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    color = surfaceColor,
                    shape = shape,
                ),
        ) {
            SupportIntroCardContent(auroraColors)
        }
    } else {
        AuroraSettingsCard(
            modifier = modifier,
            onClick = null,
        ) {
            SupportIntroCardContent(auroraColors)
        }
    }
}

@Composable
private fun SupportIntroCardContent(
    auroraColors: AuroraColors,
) {
    Box(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (auroraColors.isDark) {
                            auroraColors.accent.copy(alpha = 0.14f)
                        } else {
                            Color(0xFFF4F5F7)
                        },
                        shape = RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Favorite,
                    contentDescription = null,
                    tint = auroraColors.accent,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(MR.strings.support_project),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = auroraColors.textPrimary,
                )
                Text(
                    text = stringResource(MR.strings.support_info),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 20.sp,
                    ),
                    color = auroraColors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun TagCapsule(
    text: String,
    accentColor: Color,
    auroraColors: AuroraColors,
) {
    Box(
        modifier = Modifier
            .background(
                color = if (auroraColors.isDark) {
                    accentColor.copy(alpha = 0.12f)
                } else {
                    accentColor.copy(alpha = 0.07f).compositeOver(Color(0xFFF7F8FA))
                },
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = if (auroraColors.isDark) {
                accentColor.copy(alpha = 0.90f)
            } else {
                accentColor.copy(alpha = 0.82f)
            },
        )
    }
}

@Composable
private fun TactileButton(
    onClick: () -> Unit,
    text: String,
    icon: ImageVector,
    brandColor: Color,
    auroraColors: AuroraColors,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(16.dp)
    val isDark = auroraColors.isDark

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMedium,
            dampingRatio = Spring.DampingRatioMediumBouncy,
        ),
        label = "button_scale",
    )

    val containerColor = if (isDark) {
        brandColor.copy(alpha = if (isPressed) 0.24f else 0.18f)
    } else {
        if (isPressed) brandColor.copy(alpha = 0.85f) else brandColor
    }
    val innerGlowBrush = if (isDark) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color.Transparent,
                0.44f to brandColor.copy(alpha = 0.08f),
                0.76f to brandColor.copy(alpha = 0.18f),
                1.00f to brandColor.copy(alpha = 0.28f),
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.15f), Color.Transparent),
        )
    }
    val highlightBrush = if (isDark) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color.White.copy(alpha = 0.20f),
                0.32f to Color.White.copy(alpha = 0.10f),
                0.68f to Color.Transparent,
                1.00f to Color.Transparent,
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.25f), Color.Transparent),
        )
    }
    val contentColor = Color.White

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .auroraFloatingSurface(
                colors = auroraColors,
                level = AuroraSurfaceLevel.Glass,
                shape = shape,
            )
            .background(
                color = containerColor,
                shape = shape,
            )
            .background(
                brush = innerGlowBrush,
                shape = shape,
            )
            .background(
                brush = highlightBrush,
                shape = shape,
            )
            .border(
                width = 1.dp,
                color = if (isDark) brandColor.copy(alpha = 0.25f) else brandColor.copy(alpha = 0.35f),
                shape = shape,
            )
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = Color.White.copy(alpha = if (isDark) 0.14f else 0.22f),
                        shape = RoundedCornerShape(8.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = contentColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun DonationOptionCard(
    option: DonationOption,
    brandColor: Color,
    icon: ImageVector,
    auroraColors: AuroraColors,
    context: Context,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    modifier: Modifier = Modifier,
) {
    val isDark = auroraColors.isDark
    val onClickAction = {
        if (option.type == "crypto") {
            context.copyToClipboard(option.title, option.value)
        } else {
            uriHandler.openUri(option.value)
        }
    }

    if (isDark) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val shape = RoundedCornerShape(20.dp)

        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.98f else 1.0f,
            animationSpec = spring(
                stiffness = Spring.StiffnessLow,
                dampingRatio = Spring.DampingRatioMediumBouncy,
            ),
            label = "card_scale",
        )

        val containerColor by animateColorAsState(
            targetValue = brandColor.copy(alpha = if (isPressed) 0.10f else 0.06f)
                .compositeOver(Color(0xFF171A20)),
            label = "card_container",
        )

        val glowColor = brandColor.copy(alpha = if (isPressed) 0.18f else 0.12f)

        Box(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = if (isPressed) 8.dp else 14.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = 0.22f),
                    spotColor = glowColor,
                )
                .background(
                    color = containerColor,
                    shape = shape,
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClickAction,
                ),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.05f),
                                Color.Transparent,
                                glowColor.copy(alpha = 0.16f),
                            ),
                        ),
                        shape = shape,
                    ),
            )
            DonationOptionCardContent(
                option = option,
                brandColor = brandColor,
                icon = icon,
                auroraColors = auroraColors,
                context = context,
                uriHandler = uriHandler,
                isDark = true,
            )
        }
    } else {
        AuroraSettingsCard(
            modifier = modifier,
            onClick = onClickAction,
        ) {
            DonationOptionCardContent(
                option = option,
                brandColor = brandColor,
                icon = icon,
                auroraColors = auroraColors,
                context = context,
                uriHandler = uriHandler,
                isDark = false,
            )
        }
    }
}

@Composable
private fun DonationOptionCardContent(
    option: DonationOption,
    brandColor: Color,
    icon: ImageVector,
    auroraColors: AuroraColors,
    context: Context,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    isDark: Boolean,
) {
    val secondarySurfaceColor = if (isDark) {
        Color.White.copy(alpha = 0.06f)
    } else {
        Color(0xFFF6F7F8)
    }

    val title = when (option.id) {
        "cloudtips" -> stringResource(MR.strings.donation_option_cloudtips_title)
        "boosty" -> stringResource(MR.strings.donation_option_boosty_title)
        "crypto_usdt_tron" -> stringResource(MR.strings.donation_option_crypto_usdt_tron_title)
        "crypto_usdt" -> stringResource(MR.strings.donation_option_crypto_usdt_ton_title)
        else -> option.title
    }

    val description = when (option.id) {
        "cloudtips" -> stringResource(MR.strings.donation_option_cloudtips_desc)
        "boosty" -> stringResource(MR.strings.donation_option_boosty_desc)
        "crypto_usdt_tron" -> stringResource(MR.strings.donation_option_crypto_usdt_tron_desc)
        "crypto_usdt" -> stringResource(MR.strings.donation_option_crypto_usdt_ton_desc)
        else -> option.description
    }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = if (isDark) {
                            brandColor.copy(alpha = 0.14f)
                        } else {
                            Color(0xFFF3F4F6)
                        },
                        shape = RoundedCornerShape(14.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = auroraColors.textPrimary,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        lineHeight = 18.sp,
                    ),
                    color = auroraColors.textSecondary,
                )
            }
        }

        if (option.tags.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                option.tags.forEach { tag ->
                    TagCapsule(tag, brandColor, auroraColors)
                }
            }
        }

        if (option.type == "crypto") {
            SelectionContainer {
                Text(
                    text = option.value,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    ),
                    color = auroraColors.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = secondarySurfaceColor,
                            shape = RoundedCornerShape(14.dp),
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }

            TactileButton(
                onClick = { context.copyToClipboard(title, option.value) },
                text = stringResource(MR.strings.copy_address),
                icon = Icons.Outlined.ContentCopy,
                brandColor = brandColor,
                auroraColors = auroraColors,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TactileButton(
                onClick = { uriHandler.openUri(option.value) },
                text = stringResource(MR.strings.open_link),
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                brandColor = brandColor,
                auroraColors = auroraColors,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private val CloudTipsIcon = ImageVector.Builder(
    name = "CloudTips",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color(0xFF00C4B4)),
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(6f, 3f)
        lineTo(18f, 3f)
        curveTo(19.66f, 3f, 21f, 4.34f, 21f, 6f)
        lineTo(21f, 18f)
        curveTo(21f, 19.66f, 19.66f, 21f, 18f, 21f)
        lineTo(6f, 21f)
        curveTo(4.34f, 21f, 3f, 19.66f, 3f, 18f)
        lineTo(3f, 6f)
        curveTo(3f, 4.34f, 4.34f, 3f, 6f, 3f)
        close()
    }
    path(
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(8.2f, 15.8f)
        lineTo(15.8f, 15.8f)
        curveTo(17.32f, 15.8f, 18.55f, 14.62f, 18.55f, 13.18f)
        curveTo(18.55f, 11.94f, 17.62f, 10.88f, 16.37f, 10.64f)
        curveTo(16.03f, 8.73f, 14.31f, 7.3f, 12.23f, 7.3f)
        curveTo(10.52f, 7.3f, 9.04f, 8.28f, 8.33f, 9.72f)
        curveTo(6.58f, 9.84f, 5.2f, 11.18f, 5.2f, 12.88f)
        curveTo(5.2f, 14.49f, 6.55f, 15.8f, 8.2f, 15.8f)
        close()
    }
    path(
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(12f, 10.1f)
        lineTo(12.76f, 11.72f)
        lineTo(14.5f, 11.98f)
        lineTo(13.22f, 13.26f)
        lineTo(13.52f, 15.02f)
        lineTo(12f, 14.18f)
        lineTo(10.48f, 15.02f)
        lineTo(10.78f, 13.26f)
        lineTo(9.5f, 11.98f)
        lineTo(11.24f, 11.72f)
        close()
    }
}.build()

private val BoostyIcon = ImageVector.Builder(
    name = "Boosty",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color(0xFFF15F2C)),
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(6f, 3f)
        lineTo(18f, 3f)
        curveTo(19.66f, 3f, 21f, 4.34f, 21f, 6f)
        lineTo(21f, 18f)
        curveTo(21f, 19.66f, 19.66f, 21f, 18f, 21f)
        lineTo(6f, 21f)
        curveTo(4.34f, 21f, 3f, 19.66f, 3f, 18f)
        lineTo(3f, 6f)
        curveTo(3f, 4.34f, 4.34f, 3f, 6f, 3f)
        close()
    }
    path(
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(8f, 7f)
        lineTo(11.4f, 7f)
        lineTo(16.5f, 11.8f)
        lineTo(11.1f, 17f)
        lineTo(7.6f, 17f)
        lineTo(12.1f, 12.3f)
        close()
    }
}.build()

private val TonIcon = ImageVector.Builder(
    name = "TON",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color(0xFF4DB8FF)),
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(12f, 2.5f)
        lineTo(19.5f, 6.8f)
        lineTo(19.5f, 15.5f)
        lineTo(12f, 20f)
        lineTo(4.5f, 15.5f)
        lineTo(4.5f, 6.8f)
        close()
    }
    path(
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(9.2f, 8f)
        lineTo(14.8f, 8f)
        lineTo(16f, 9.8f)
        lineTo(12f, 16.5f)
        lineTo(8f, 9.8f)
        close()
    }
}.build()

private val UsdtIcon = ImageVector.Builder(
    name = "USDT",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = SolidColor(Color(0xFF009393)),
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(12f, 2f)
        curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
        curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
        curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
        curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f)
        close()
    }
    path(
        fill = SolidColor(Color.White),
        pathFillType = PathFillType.NonZero,
    ) {
        moveTo(6.8f, 7.2f)
        lineTo(17.2f, 7.2f)
        lineTo(17.2f, 9.2f)
        lineTo(13.2f, 9.2f)
        lineTo(13.2f, 10.6f)
        curveTo(15.94f, 10.76f, 18f, 11.4f, 18f, 12.32f)
        curveTo(18f, 13.24f, 15.94f, 13.88f, 13.2f, 14.04f)
        lineTo(13.2f, 16.8f)
        lineTo(10.8f, 16.8f)
        lineTo(10.8f, 14.04f)
        curveTo(8.06f, 13.88f, 6f, 13.24f, 6f, 12.32f)
        curveTo(6f, 11.4f, 8.06f, 10.76f, 10.8f, 10.6f)
        lineTo(10.8f, 9.2f)
        lineTo(6.8f, 9.2f)
        close()

        moveTo(8.8f, 12.32f)
        curveTo(8.8f, 12.62f, 10.23f, 12.96f, 12f, 12.96f)
        curveTo(13.77f, 12.96f, 15.2f, 12.62f, 15.2f, 12.32f)
        curveTo(15.2f, 12.02f, 13.77f, 11.68f, 12f, 11.68f)
        curveTo(10.23f, 11.68f, 8.8f, 12.02f, 8.8f, 12.32f)
        close()
    }
}.build()
