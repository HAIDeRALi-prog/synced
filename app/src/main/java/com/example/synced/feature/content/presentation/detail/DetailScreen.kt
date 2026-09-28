package com.example.synced.feature.content.presentation.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.synced.core.common.UiState
import com.example.synced.core.ui.theme.LocalSpacing
import com.example.synced.feature.content.domain.Comment
import com.example.synced.feature.content.domain.Post
import com.example.synced.ui.components.AuthorChip
import com.example.synced.ui.components.InlineLoading
import com.example.synced.ui.components.LoadingState
import com.example.synced.ui.components.StateMessage
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when (val postState = state.post) {
            is UiState.Loading -> LoadingState(Modifier.padding(padding))
            is UiState.Error -> StateMessage(
                icon = Icons.Outlined.ErrorOutline,
                message = postState.message,
                modifier = Modifier.padding(padding),
                actionLabel = "Retry",
                onAction = viewModel::refreshPost,
            )

            UiState.Empty -> StateMessage(
                icon = Icons.Outlined.ErrorOutline,
                message = "This post isn't available.",
                modifier = Modifier.padding(padding),
            )

            is UiState.Content -> PostDetail(
                post = postState.data,
                commentsState = state.comments,
                onRetryComments = viewModel::refreshComments,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun PostDetail(
    post: Post,
    commentsState: UiState<List<Comment>>,
    onRetryComments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = String.format(Locale.ROOT, "%02d", post.id),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
            post.authorName?.let { AuthorChip(name = it, modifier = Modifier.weight(1f)) }
        }
        Text(
            text = post.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = post.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Text(
            text = "Comments".uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (commentsState) {
            is UiState.Loading -> InlineLoading(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
            )

            is UiState.Error -> StateMessage(
                icon = Icons.Outlined.ErrorOutline,
                message = commentsState.message,
                actionLabel = "Retry",
                onAction = onRetryComments,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.md),
            )

            UiState.Empty -> Text(
                text = "No comments yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = spacing.xs),
            )

            is UiState.Content -> Column(
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                commentsState.data.forEach { comment -> CommentCard(comment) }
            }
        }
    }
}

@Composable
private fun CommentCard(comment: Comment) {
    val spacing = LocalSpacing.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = String.format(Locale.ROOT, "%02d", comment.id),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = comment.name.uppercase(Locale.ROOT),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = comment.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
