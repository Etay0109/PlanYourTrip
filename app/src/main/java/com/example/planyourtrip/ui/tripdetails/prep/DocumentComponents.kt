package com.example.planyourtrip.ui.tripdetails.prep

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.document.DocumentKind
import com.example.planyourtrip.data.document.DocumentType
import com.example.planyourtrip.ui.common.components.ConfirmDeleteDialog
import com.example.planyourtrip.ui.common.components.Pill
import com.example.planyourtrip.ui.common.errorSupportingText
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.Spacing

@DrawableRes
fun DocumentType.iconRes(): Int = when (this) {
    DocumentType.FLIGHT -> R.drawable.ic_flight
    DocumentType.LODGING -> R.drawable.ic_hotel
    DocumentType.INSURANCE -> R.drawable.ic_shield
    DocumentType.TICKET -> R.drawable.ic_ticket
    DocumentType.OTHER -> R.drawable.ic_description
}

@StringRes
fun DocumentType.labelRes(): Int = when (this) {
    DocumentType.FLIGHT -> R.string.document_type_flight
    DocumentType.LODGING -> R.string.document_type_lodging
    DocumentType.INSURANCE -> R.string.document_type_insurance
    DocumentType.TICKET -> R.string.document_type_ticket
    DocumentType.OTHER -> R.string.document_type_other
}

/**
 * "Documents" + count + "+ Add", a horizontally scrolling row of cards ending with the dashed "Add document" card,
 * and the "Stored on this device only" note. The cards share the height of the tallest one.
 */
@Composable
internal fun DocumentsSection(documents: List<DocumentCardState>, actions: PrepActions) {
    Column(Modifier.fillMaxWidth().padding(top = Spacing.s)) {
        PrepSectionTitle(stringResource(R.string.prep_documents)) {
            Pill(
                text = documents.size.toString(),
                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = actions.onAddDocument) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(IconSize.m))
                Spacer(Modifier.width(Spacing.xs))
                Text(stringResource(R.string.prep_add), style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(Spacing.s))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .height(IntrinsicSize.Min)
                .padding(horizontal = Dimens.ScreenPadding, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            documents.forEach { document ->
                DocumentCard(
                    document = document,
                    onView = { actions.onViewDocument(document.id) },
                    onEdit = { actions.onEditDocument(document.id) },
                    onDelete = { actions.onDeleteDocument(document.id) },
                    modifier = Modifier.fillMaxHeight(),
                )
            }
            AddDocumentCard(onClick = actions.onAddDocument, modifier = Modifier.fillMaxHeight())
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = stringResource(R.string.prep_documents_local_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimens.ScreenPadding),
        )
    }
}

/** Type icon in a tinted square, PDF/Photo badge, menu, name, detail line and "View file" (the whole card opens it). */
@Composable
private fun DocumentCard(
    document: DocumentCardState,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onView,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = Dimens.CardElevation,
        modifier = modifier.width(Dimens.DocumentCardWidth),
    ) {
        Column(Modifier.padding(start = Spacing.l, top = Spacing.l, bottom = Spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(Dimens.IconChipSize)
                        .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(document.type.iconRes()),
                        contentDescription = stringResource(document.type.labelRes()),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(IconSize.l),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(
                        if (document.kind == DocumentKind.PDF) R.string.prep_badge_pdf else R.string.prep_badge_photo,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.extraSmall)
                        .padding(horizontal = Spacing.s, vertical = TagPaddingVertical),
                )
                DocumentMenu(document.name, onEdit, onDelete)
            }
            Spacer(Modifier.height(Spacing.m))
            Column(Modifier.padding(end = Spacing.l).weight(1f)) {
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                document.detail?.let {
                    Spacer(Modifier.height(Spacing.xxs))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.m))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = MetaRowMinHeight)) {
                Icon(
                    painter = painterResource(R.drawable.ic_visibility),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.s),
                )
                Spacer(Modifier.width(Spacing.xsPlus))
                Text(
                    text = stringResource(R.string.prep_view_file),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Three dots on a document card: Edit (name, type, detail line) and Delete. Consumes its taps. */
@Composable
private fun DocumentMenu(name: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(Dimens.TouchTarget)) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.prep_document_options, name),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.CardMenuIconSize),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.prep_edit)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_edit), contentDescription = null) },
                onClick = {
                    open = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}

/** The last card of the row: dashed outline, "+" and "Add document". */
@Composable
private fun AddDocumentCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .width(Dimens.DocumentCardWidth)
            .heightIn(min = DocumentCardMinHeight)
            .clip(shape)
            .drawBehind {
                val stroke = DashedStroke.toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    // The same corners as shapes.large (the document cards).
                    cornerRadius = CornerRadius(AddCardCorner.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx())),
                    ),
                )
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.IconChipSize)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.xl),
            )
        }
        Spacer(Modifier.height(Spacing.sPlus))
        Text(
            text = stringResource(R.string.prep_add_document),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
    }
}

/** New document (after the file was picked) or edit: name, type chips and the optional detail line. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentFormDialog(
    state: DocumentFormState,
    onNameChange: (String) -> Unit,
    onTypeChange: (DocumentType) -> Unit,
    onDetailChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (state.isNew) R.string.prep_document_new_title else R.string.prep_document_edit_title,
                ),
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.prep_document_name)) },
                    isError = state.nameError,
                    supportingText = errorSupportingText(R.string.prep_document_name_empty.takeIf { state.nameError }),
                    singleLine = true,
                    enabled = !state.isSaving,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.m))
                Text(
                    text = stringResource(R.string.prep_document_type),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    DocumentType.entries.forEach { type ->
                        FilterChip(
                            selected = state.type == type,
                            onClick = { onTypeChange(type) },
                            enabled = !state.isSaving,
                            label = { Text(stringResource(type.labelRes())) },
                            leadingIcon = {
                                Icon(
                                    painterResource(type.iconRes()),
                                    contentDescription = null,
                                    modifier = Modifier.size(IconSize.s),
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.m))
                OutlinedTextField(
                    value = state.detail,
                    onValueChange = onDetailChange,
                    label = { Text(stringResource(R.string.prep_document_detail)) },
                    placeholder = { Text(stringResource(R.string.prep_document_detail_hint)) },
                    singleLine = true,
                    enabled = !state.isSaving,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !state.isSaving) {
                if (state.isSaving) {
                    CircularProgressIndicator(Modifier.size(IconSize.s), strokeWidth = Dimens.ProgressStroke)
                } else {
                    Text(stringResource(R.string.action_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** "Delete this document?" — the row and its file go. */
@Composable
fun DeleteDocumentDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDeleteDialog(
        title = stringResource(R.string.prep_delete_document_title),
        body = stringResource(R.string.prep_delete_document_body, name),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** The small type tag on a document card. */
private val TagPaddingVertical = 3.dp

/** The card's bottom row (file name and menu). */
private val MetaRowMinHeight = 32.dp

private val DocumentCardMinHeight = 168.dp

/** The dashed "Add document" card. */
private val DashedStroke = 1.5.dp
private val AddCardCorner = 20.dp
private val DashOn = 8.dp
private val DashOff = 6.dp
