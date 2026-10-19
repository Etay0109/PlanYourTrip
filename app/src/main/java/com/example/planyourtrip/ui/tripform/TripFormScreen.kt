package com.example.planyourtrip.ui.tripform

import androidx.annotation.StringRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planyourtrip.R
import com.example.planyourtrip.data.place.PlaceSuggestion
import com.example.planyourtrip.data.trip.TextLimits
import com.example.planyourtrip.data.trip.limitTo
import com.example.planyourtrip.ui.common.components.BackTopAppBar
import com.example.planyourtrip.ui.common.components.CurrencyPickerSheet
import com.example.planyourtrip.ui.common.components.DateField
import com.example.planyourtrip.ui.common.components.FlagEmoji
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.common.currencyFlag
import com.example.planyourtrip.ui.common.errorSupportingText
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import com.example.planyourtrip.ui.theme.Spacing
import java.time.LocalDate

@Composable
fun TripFormScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: TripFormViewModel = viewModel(factory = TripFormViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    TripFormContent(
        state = state,
        onBack = onBack,
        onDestinationChange = viewModel::onDestinationChange,
        onSuggestionPicked = viewModel::onSuggestionPicked,
        onSuggestionsDismissed = viewModel::onSuggestionsDismissed,
        onStartDateChange = viewModel::onStartDateChange,
        onEndDateChange = viewModel::onEndDateChange,
        onChooseCurrency = viewModel::onChooseCurrency,
        onClearCurrency = viewModel::onClearCurrency,
        onSave = viewModel::onSave,
    )

    state.currencyPicker?.let { picker ->
        CurrencyPickerSheet(
            state = picker,
            onQueryChange = viewModel::onCurrencyQueryChange,
            onSelect = viewModel::onCurrencySelected,
            onRetry = viewModel::onRetryCurrencies,
            onDismiss = viewModel::onDismissCurrencyPicker,
        )
    }
}

@Composable
fun TripFormContent(
    state: TripFormUiState,
    onBack: () -> Unit,
    onDestinationChange: (String) -> Unit,
    onStartDateChange: (LocalDate) -> Unit,
    onEndDateChange: (LocalDate) -> Unit,
    onChooseCurrency: () -> Unit,
    onClearCurrency: () -> Unit,
    onSave: () -> Unit,
    onSuggestionPicked: (PlaceSuggestion) -> Unit = {},
    onSuggestionsDismissed: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    // Where the destination field and the Save button end in the form, and how far the suggestions reach below the
    // field (px): the room the list needs past the button.
    var fieldBottom by remember { mutableFloatStateOf(0f) }
    var buttonBottom by remember { mutableFloatStateOf(0f) }
    var overlayHeight by remember { mutableIntStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val title = if (state.isEditing) R.string.edit_trip_title else R.string.new_trip_title
            BackTopAppBar(onBack = onBack, title = stringResource(title))
        },
    ) { innerPadding ->
        // Editing: wait for the trip's values so the fields never flash empty.
        if (state.isLoading) return@Scaffold
        // Typed text is Compose state (starting from the loaded or restored value), never read back while typing.
        var destination by rememberSaveable { mutableStateOf(state.destination) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            DestinationFieldWithSuggestions(
                isSearching = state.isSearching,
                suggestions = state.suggestions,
                needsInternet = state.searchNeedsInternet,
                onPick = { suggestion ->
                    destination = suggestion.label
                    onSuggestionPicked(suggestion)
                    // Closes the keyboard; the field shows the whole picked name.
                    focusManager.clearFocus()
                },
                onOverlayHeight = { overlayHeight = it },
                modifier = Modifier.onPlaced { fieldBottom = it.positionInParent().y + it.size.height },
            ) {
                DestinationTextField(
                    destination = destination,
                    onDestinationChange = {
                        destination = it.limitTo(TextLimits.DESTINATION)
                        onDestinationChange(destination)
                    },
                    error = state.destinationError,
                    onFocusLost = onSuggestionsDismissed,
                )
            }

            TripDateFields(state, onStartDateChange, onEndDateChange)

            CurrencyField(
                code = state.currencyCode,
                name = state.currencyName,
                onClick = onChooseCurrency,
                onClear = onClearCurrency,
            )

            Spacer(Modifier.height(Spacing.l))

            PrimaryButton(
                text = stringResource(if (state.isEditing) R.string.edit_trip_save else R.string.new_trip_save),
                onClick = onSave,
                loading = state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .onPlaced { buttonBottom = it.positionInParent().y + it.size.height },
            )

            // The list is drawn over the fields below the destination; when it reaches past the Save button, this
            // room (after everything, so nothing moves) lets the form scroll its last rows into view.
            val listOverhang = (fieldBottom + overlayHeight - buttonBottom).coerceAtLeast(0f)
            if (listOverhang > 0f) {
                Spacer(Modifier.height(with(LocalDensity.current) { listOverhang.toDp() }))
            }
        }
    }
}

@Composable
private fun DestinationTextField(
    destination: String,
    onDestinationChange: (String) -> Unit,
    error: TripFieldError?,
    onFocusLost: () -> Unit,
) {
    OutlinedTextField(
        value = destination,
        onValueChange = onDestinationChange,
        label = { Text(stringResource(R.string.new_trip_destination)) },
        placeholder = { Text(stringResource(R.string.new_trip_destination_placeholder)) },
        singleLine = true,
        isError = error != null,
        supportingText = errorSupportingText(error?.messageRes()),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
        ),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) onFocusLost() },
    )
}

/** Start and end date; the end date's picker starts at the start date. */
@Composable
private fun TripDateFields(
    state: TripFormUiState,
    onStartDateChange: (LocalDate) -> Unit,
    onEndDateChange: (LocalDate) -> Unit,
) {
    DateField(
        label = R.string.new_trip_start_date,
        date = state.startDate,
        errorRes = state.startError?.messageRes(),
        initialPickerDate = state.startDate ?: state.endDate,
        onDatePicked = onStartDateChange,
    )

    DateField(
        label = R.string.new_trip_end_date,
        date = state.endDate,
        errorRes = state.endError?.messageRes(),
        initialPickerDate = state.endDate ?: state.startDate,
        onDatePicked = onEndDateChange,
        // Guide the user away from an end date before the start; the ViewModel still validates.
        minDate = state.startDate,
    )
}

/**
 * Optional, read-only currency field: "EUR · Euro" or a placeholder. Tapping opens the currency list; the
 * clear icon removes the choice.
 */
@Composable
private fun CurrencyField(code: String?, name: String?, onClick: () -> Unit, onClear: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentOnClick by rememberUpdatedState(onClick)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect {
            if (it is PressInteraction.Release) currentOnClick()
        }
    }
    val value = when {
        code == null -> ""
        name == null -> code
        else -> stringResource(R.string.trip_currency_value, code, name)
    }
    val flag = currencyFlag(code)
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        leadingIcon = flag?.let { { FlagEmoji(it, style = MaterialTheme.typography.titleLarge) } },
        label = { Text(stringResource(R.string.trip_currency_label)) },
        placeholder = { Text(stringResource(R.string.trip_currency_placeholder)) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (code != null) {
                    IconButton(onClick = onClear) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.trip_currency_clear),
                        )
                    }
                }
                Icon(
                    painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    modifier = Modifier.padding(end = Spacing.m),
                )
            }
        },
        singleLine = true,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    )
}

@StringRes
private fun TripFieldError.messageRes(): Int = when (this) {
    TripFieldError.EMPTY_DESTINATION -> R.string.error_empty_destination
    TripFieldError.MISSING_START -> R.string.error_missing_start
    TripFieldError.MISSING_END -> R.string.error_missing_end
    TripFieldError.END_BEFORE_START -> R.string.error_end_before_start
}

@Preview(showBackground = true)
@Composable
internal fun TripFormPreview() {
    PlanYourTripTheme(darkTheme = false) {
        TripFormContent(
            state = TripFormUiState(
                destination = "",
                startDate = LocalDate.of(2026, 11, 17),
                endDate = LocalDate.of(2026, 11, 12),
                destinationError = TripFieldError.EMPTY_DESTINATION,
                endError = TripFieldError.END_BEFORE_START,
            ),
            onBack = {}, onDestinationChange = {}, onStartDateChange = {}, onEndDateChange = {},
            onChooseCurrency = {}, onClearCurrency = {}, onSave = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun TripFormSuggestionsPreview() {
    PlanYourTripTheme(darkTheme = false) {
        TripFormContent(
            state = TripFormUiState(
                destination = "Lond",
                suggestions = listOf(
                    PlaceSuggestion("London", "England", "United Kingdom", "GB", 51.5, -0.13),
                    PlaceSuggestion("London", "Ontario", "Canada", "CA", 42.98, -81.25),
                    PlaceSuggestion("Londrina", "Paraná", "Brazil", "BR", -23.3, -51.16),
                ),
            ),
            onBack = {}, onDestinationChange = {}, onStartDateChange = {}, onEndDateChange = {},
            onChooseCurrency = {}, onClearCurrency = {}, onSave = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 320, fontScale = 1.5f)
@Composable
internal fun TripFormCurrencyPreview() {
    PlanYourTripTheme(darkTheme = false) {
        TripFormContent(
            state = TripFormUiState(
                isEditing = true,
                destination = "Zurich, Switzerland",
                startDate = LocalDate.of(2026, 11, 12),
                endDate = LocalDate.of(2026, 11, 17),
                currencyCode = "CHF",
                currencyName = "Swiss Franc",
            ),
            onBack = {}, onDestinationChange = {}, onStartDateChange = {}, onEndDateChange = {},
            onChooseCurrency = {}, onClearCurrency = {}, onSave = {},
        )
    }
}
