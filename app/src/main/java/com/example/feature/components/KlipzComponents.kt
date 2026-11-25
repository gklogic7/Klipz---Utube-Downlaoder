package com.example.feature.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.models.Platform
import com.example.ui.theme.BrandDailymotion
import com.example.ui.theme.BrandDirect
import com.example.ui.theme.BrandFacebook
import com.example.ui.theme.BrandInstagram
import com.example.ui.theme.BrandPinterest
import com.example.ui.theme.BrandReddit
import com.example.ui.theme.BrandSoundCloud
import com.example.ui.theme.BrandTed
import com.example.ui.theme.BrandTikTok
import com.example.ui.theme.BrandWhatsApp
import com.example.ui.theme.BrandX
import com.example.ui.theme.BrandYouTube

@Composable
fun KlipzBrandLogo(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(surface),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(22.dp)) {
                val w = size.width
                val h = size.height
                val stroke = Stroke(width = w * 0.14f, cap = StrokeCap.Square, join = StrokeJoin.Miter)
                // Left K spine
                drawLine(primary, Offset(w * 0.22f, h * 0.15f), Offset(w * 0.22f, h * 0.85f), strokeWidth = w * 0.15f)
                // Upper diagonal arm
                drawLine(primary, Offset(w * 0.26f, h * 0.52f), Offset(w * 0.78f, h * 0.16f), strokeWidth = w * 0.14f)
                // Lower download arrow integrated into K
                val arrowPath = Path().apply {
                    moveTo(w * 0.54f, h * 0.45f)
                    lineTo(w * 0.72f, h * 0.62f)
                    lineTo(w * 0.72f, h * 0.84f)
                }
                drawPath(arrowPath, color = primary, style = stroke)
                val headPath = Path().apply {
                    moveTo(w * 0.56f, h * 0.70f)
                    lineTo(w * 0.72f, h * 0.86f)
                    lineTo(w * 0.88f, h * 0.70f)
                }
                drawPath(headPath, color = primary, style = stroke)
            }
        }
        Column {
            Text(
                text = "Klipz",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun MonochromeCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    cornerRadius: Dp = 14.dp,
    contentPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun PlatformIconBadge(
    platform: Platform,
    shortcutId: String = platform.name,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val (bgColor, icon, contentDesc) = when {
        shortcutId == "yt_music" -> Triple(BrandYouTube, Icons.Filled.Audiotrack, "YT Music")
        shortcutId == "whatsapp" -> Triple(BrandWhatsApp, Icons.AutoMirrored.Filled.Chat, "WhatsApp")
        platform == Platform.YOUTUBE -> Triple(BrandYouTube, Icons.Filled.PlayArrow, "YouTube")
        platform == Platform.FACEBOOK -> Triple(BrandFacebook, Icons.Filled.ThumbUp, "Facebook")
        platform == Platform.INSTAGRAM -> Triple(BrandInstagram, Icons.Filled.PhotoCamera, "Instagram")
        platform == Platform.DIRECT -> Triple(BrandDirect, Icons.Filled.Link, "Direct URL")
        platform == Platform.SOUNDCLOUD -> Triple(BrandSoundCloud, Icons.Filled.GraphicEq, "SoundCloud")
        platform == Platform.PINTEREST -> Triple(BrandPinterest, Icons.Filled.PushPin, "Pinterest")
        platform == Platform.TIKTOK -> Triple(BrandTikTok, Icons.Filled.MusicNote, "TikTok")
        platform == Platform.DAILYMOTION -> Triple(BrandDailymotion, Icons.Filled.Tv, "Dailymotion")
        platform == Platform.REDDIT -> Triple(BrandReddit, Icons.Filled.Forum, "Reddit")
        platform == Platform.X -> Triple(BrandX, Icons.Filled.Tag, "X")
        platform == Platform.TED -> Triple(BrandTed, Icons.Filled.RecordVoiceOver, "TED Talks")
        else -> Triple(BrandDirect, Icons.Filled.Public, "Web")
    }

    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(size * 0.28f),
        color = bgColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = Color.White,
                modifier = Modifier.size(size * 0.52f)
            )
        }
    }
}

@Composable
fun StatusDotPill(
    label: String,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun EmptyStatePanel(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    MonochromeCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = 24.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(14.dp))
                action()
            }
        }
    }
}
