package cafe.oeee.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.oeee.data.model.RecentComment
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecentCommentCard(
    comment: RecentComment,
    onPostClick: () -> Unit,
    onProfileClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp)
        ) {
            // Post Thumbnail
            comment.postImageUrl?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onPostClick)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Comment Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Actor Info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.then(
                        if (onProfileClick != null) {
                            Modifier.clickable(onClick = onProfileClick)
                        } else {
                            Modifier
                        }
                    )
                ) {
                    Text(
                        text = comment.actorName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (comment.isLocal && comment.actorLoginName != null) {
                            "@${comment.actorLoginName}"
                        } else {
                            comment.actorHandle
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Comment Text (strip HTML if present)
                Text(
                    text = comment.contentHtml?.let { stripHtml(it) } ?: comment.content,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Post Title and Time
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    comment.postTitle?.let { title ->
                        Text(
                            text = "on \"$title\"",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable(onClick = onPostClick)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = formatRelativeTime(comment.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Helper function to strip HTML tags
private fun stripHtml(html: String): String {
    return html.replace(Regex("<[^>]*>"), "").trim()
}

// Helper function to format relative time
private fun formatRelativeTime(date: Date): String {
    val now = System.currentTimeMillis()
    val diff = now - date.time
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "${days}d"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "now"
    }
}
