package fr.cklla.pellicule.ui.jellyfin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.cklla.pellicule.R
import fr.cklla.pellicule.ui.theme.AccentPurple
import fr.cklla.pellicule.ui.theme.AccentPurpleLight
import fr.cklla.pellicule.ui.theme.AccentPurpleMuted
import fr.cklla.pellicule.ui.theme.BackgroundDark
import fr.cklla.pellicule.ui.theme.BorderHairline
import fr.cklla.pellicule.ui.theme.ErrorCoral
import fr.cklla.pellicule.ui.theme.PelliculeTextStyles
import fr.cklla.pellicule.ui.theme.PelliculeTheme
import fr.cklla.pellicule.ui.theme.SuccessGreen
import fr.cklla.pellicule.ui.theme.SurfaceCard
import fr.cklla.pellicule.ui.theme.TextMuted
import fr.cklla.pellicule.ui.theme.TextPrimary
import fr.cklla.pellicule.ui.theme.TextTertiary

@Composable
fun JellyfinSettingsScreen(onBackClick: () -> Unit, viewModel: JellyfinSettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    JellyfinSettingsContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onServerUrlChanged = viewModel::onServerUrlChanged,
        onUsernameChanged = viewModel::onUsernameChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onConnectClick = viewModel::onConnectClicked,
        onDisconnectClick = viewModel::onDisconnectClicked,
    )
}

@Composable
private fun JellyfinSettingsContent(
    uiState: JellyfinSettingsUiState,
    onBackClick: () -> Unit,
    onServerUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(BackgroundDark)) {
        BackHeader(onBackClick = onBackClick)
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column {
                Text(text = stringResource(R.string.jellyfin_kicker), style = PelliculeTextStyles.kicker, color = AccentPurpleLight)
                Text(text = stringResource(R.string.jellyfin_title), style = PelliculeTextStyles.screenTitle, color = TextPrimary)
            }
            if (uiState.connectedUsername != null) {
                ConnectedStatus(
                    username = uiState.connectedUsername,
                    serverUrl = uiState.connectedServerUrl.orEmpty(),
                    onDisconnectClick = onDisconnectClick,
                )
            } else {
                ConnectForm(
                    uiState = uiState,
                    onServerUrlChanged = onServerUrlChanged,
                    onUsernameChanged = onUsernameChanged,
                    onPasswordChanged = onPasswordChanged,
                    onConnectClick = onConnectClick,
                )
            }
        }
    }
}

@Composable
private fun BackHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onBackClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = TextPrimary)
        Text(text = stringResource(R.string.detail_back), style = PelliculeTextStyles.backLabel, color = TextTertiary)
    }
}

@Composable
private fun ConnectedStatus(username: String, serverUrl: String, onDisconnectClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceCard)
                .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.6f)), RoundedCornerShape(8.dp))
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.jellyfin_connected_label),
                style = PelliculeTextStyles.sectionLabel,
                color = SuccessGreen,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = username, style = PelliculeTextStyles.cardTitle, color = TextPrimary)
            Text(text = serverUrl, style = PelliculeTextStyles.cardSubtitle, color = TextMuted)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onDisconnectClick),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = stringResource(R.string.jellyfin_disconnect_action),
                style = PelliculeTextStyles.linkLabel.copy(textDecoration = TextDecoration.Underline),
                color = ErrorCoral,
            )
        }
    }
}

@Composable
private fun ConnectForm(
    uiState: JellyfinSettingsUiState,
    onServerUrlChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConnectClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FormField(
            value = uiState.serverUrl,
            onValueChange = onServerUrlChanged,
            placeholder = stringResource(R.string.jellyfin_server_url_placeholder),
            keyboardType = KeyboardType.Uri,
        )
        if (uiState.isCleartextServerUrl) {
            Text(
                text = stringResource(R.string.jellyfin_cleartext_warning),
                style = PelliculeTextStyles.emptyMessage,
                color = TextTertiary,
            )
        }
        FormField(
            value = uiState.username,
            onValueChange = onUsernameChanged,
            placeholder = stringResource(R.string.jellyfin_username_placeholder),
        )
        FormField(
            value = uiState.password,
            onValueChange = onPasswordChanged,
            placeholder = stringResource(R.string.jellyfin_password_placeholder),
            keyboardType = KeyboardType.Password,
            visualTransformation = PasswordVisualTransformation(),
            imeAction = ImeAction.Done,
        )
        uiState.errorMessage?.let { message ->
            Text(text = message, style = PelliculeTextStyles.emptyMessage, color = ErrorCoral)
        }
        ConnectButton(isLoading = uiState.isConnecting, onClick = onConnectClick)
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Next,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceCard)
            .border(BorderStroke(0.5.dp, BorderHairline.copy(alpha = 0.6f)), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = PelliculeTextStyles.chipLabel.copy(color = TextPrimary),
                cursorBrush = SolidColor(AccentPurple),
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (value.isEmpty()) {
                        Text(text = placeholder, style = PelliculeTextStyles.chipLabel, color = AccentPurpleMuted)
                    }
                    innerTextField()
                },
            )
        }
    }
}

@Composable
private fun ConnectButton(isLoading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AccentPurple)
            .clickable(enabled = !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = TextPrimary, modifier = Modifier.height(20.dp))
        } else {
            Text(text = stringResource(R.string.jellyfin_connect_action), style = PelliculeTextStyles.statusPillLabel, color = TextPrimary)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun JellyfinSettingsDisconnectedPreview() {
    PelliculeTheme {
        JellyfinSettingsContent(
            uiState = JellyfinSettingsUiState(),
            onBackClick = {},
            onServerUrlChanged = {},
            onUsernameChanged = {},
            onPasswordChanged = {},
            onConnectClick = {},
            onDisconnectClick = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0812)
@Composable
private fun JellyfinSettingsConnectedPreview() {
    PelliculeTheme {
        JellyfinSettingsContent(
            uiState = JellyfinSettingsUiState(connectedUsername = "stef", connectedServerUrl = "https://jellyfin.exemple.fr"),
            onBackClick = {},
            onServerUrlChanged = {},
            onUsernameChanged = {},
            onPasswordChanged = {},
            onConnectClick = {},
            onDisconnectClick = {},
        )
    }
}
