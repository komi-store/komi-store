package zed.rainxch.details.presentation.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.apk.ApkInspection
import zed.rainxch.core.domain.model.apk.ApkPermission
import zed.rainxch.core.domain.model.apk.ProtectionLevel
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfaceElevation
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.locals.LocalStatusColors
import zed.rainxch.core.presentation.utils.formatFileSize
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_compatibility
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_components
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_debuggable
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_empty
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_file_info
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_identity
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_min_sdk
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_permissions
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_permissions_empty
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_permissions_title
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_protection_dangerous
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_protection_normal
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_protection_privileged
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_protection_signature
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_protection_unknown
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_section_activities
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_section_main_activity
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_section_receivers
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_section_services
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_signing
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_size
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_source_file
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_source_installed
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_target_sdk
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_title
import zed.rainxch.githubstore.core.presentation.res.apk_inspect_version_code
import zed.rainxch.githubstore.core.presentation.res.apps_section_state_collapsed
import zed.rainxch.githubstore.core.presentation.res.apps_section_state_expanded

@Composable
fun ApkInspectSheet(
    inspection: ApkInspection?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
) {
    KomiSheet(
        onDismiss = onDismiss,
        placement = KomiSheetPlacement.Bottom,
    ) {
        when {
            isLoading -> LoadingState()
            inspection != null -> InspectionContent(inspection)
            else -> EmptyState()
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        KomiCircularProgress()
    }
}

@Composable
private fun EmptyState() {
    val colors = LocalPersonality.current.colors
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        KomiText(
            text = stringResource(Res.string.apk_inspect_empty),
            role = KomiTextRole.Body,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun InspectionContent(inspection: ApkInspection) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Header(inspection) }
        item { IdentitySection(inspection) }
        item { CompatibilitySection(inspection) }
        item { PermissionsSection(inspection.permissions) }
        item { ComponentsSection(inspection) }
        item { FileSection(inspection) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Header(inspection: ApkInspection) {
    val colors = LocalPersonality.current.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KomiText(
            text = stringResource(Res.string.apk_inspect_title),
            role = KomiTextRole.Overline,
            color = colors.primary,
        )
        KomiText(
            text = inspection.appLabel,
            role = KomiTextRole.CardTitle,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.copyableOnLongPress(inspection.appLabel),
        )
        KomiText(
            text = inspection.packageName,
            role = KomiTextRole.Mono,
            color = colors.onSurfaceVariant,
            modifier = Modifier.copyableOnLongPress(inspection.packageName),
        )
        val sourceLabel =
            when (inspection.source) {
                ApkInspection.Source.FILE -> stringResource(Res.string.apk_inspect_source_file)
                ApkInspection.Source.INSTALLED -> stringResource(Res.string.apk_inspect_source_installed)
            }
        SourceChip(label = sourceLabel)
    }
}

@Composable
private fun SourceChip(label: String) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    KomiText(
        text = label,
        role = KomiTextRole.LabelSmall,
        color = colors.onSurfaceVariant,
        modifier =
            Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(shape.cornerSmall))
                .background(colors.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun IdentitySection(inspection: ApkInspection) {
    InspectSection(
        title = stringResource(Res.string.apk_inspect_identity),
        icon = Icons.Default.Fingerprint,
    ) {
        InspectRow(
            label = stringResource(Res.string.apk_inspect_version_code),
            value =
                inspection.versionName?.let { name ->
                    inspection.versionCode?.let { code -> "$name  ($code)" } ?: name
                } ?: inspection.versionCode?.toString() ?: "—",
        )
        inspection.signingFingerprint?.let { fingerprint ->
            InspectRow(
                label = stringResource(Res.string.apk_inspect_signing),
                value = fingerprint,
                monospace = true,
            )
        }
        if (inspection.debuggable) {
            DangerNote(text = stringResource(Res.string.apk_inspect_debuggable))
        }
    }
}

@Composable
private fun CompatibilitySection(inspection: ApkInspection) {
    InspectSection(
        title = stringResource(Res.string.apk_inspect_compatibility),
        icon = Icons.Default.Tune,
    ) {
        if (inspection.minSdk != null) {
            InspectRow(
                label = stringResource(Res.string.apk_inspect_min_sdk),
                value = "API ${inspection.minSdk}",
            )
        }
        if (inspection.targetSdk != null) {
            InspectRow(
                label = stringResource(Res.string.apk_inspect_target_sdk),
                value = "API ${inspection.targetSdk}",
            )
        }
    }
}

@Composable
private fun PermissionsSection(permissions: List<ApkPermission>) {
    val grouped = permissions.groupBy { it.protectionLevel }
    val orderedLevels =
        listOf(
            ProtectionLevel.DANGEROUS,
            ProtectionLevel.PRIVILEGED,
            ProtectionLevel.SIGNATURE,
            ProtectionLevel.NORMAL,
            ProtectionLevel.UNKNOWN,
        )

    val dangerousWord = stringResource(Res.string.apk_inspect_protection_dangerous).lowercase()
    val privilegedWord = stringResource(Res.string.apk_inspect_protection_privileged).lowercase()
    val signatureWord = stringResource(Res.string.apk_inspect_protection_signature).lowercase()
    val normalWord = stringResource(Res.string.apk_inspect_protection_normal).lowercase()
    val unknownWord = stringResource(Res.string.apk_inspect_protection_unknown).lowercase()
    val baseTitle = stringResource(Res.string.apk_inspect_permissions_title)

    val title =
        if (permissions.isEmpty()) {
            stringResource(Res.string.apk_inspect_permissions, 0)
        } else {
            val parts =
                orderedLevels.mapNotNull { level ->
                    val count = grouped[level]?.size ?: 0
                    if (count == 0) return@mapNotNull null
                    val word =
                        when (level) {
                            ProtectionLevel.DANGEROUS -> dangerousWord
                            ProtectionLevel.PRIVILEGED -> privilegedWord
                            ProtectionLevel.SIGNATURE -> signatureWord
                            ProtectionLevel.NORMAL -> normalWord
                            ProtectionLevel.UNKNOWN -> unknownWord
                        }
                    "$count $word"
                }
            "$baseTitle  ·  ${parts.joinToString("  ·  ")}"
        }

    val colors = LocalPersonality.current.colors
    InspectSection(title = title, icon = Icons.Default.PrivacyTip) {
        if (permissions.isEmpty()) {
            KomiText(
                text = stringResource(Res.string.apk_inspect_permissions_empty),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
            )
            return@InspectSection
        }

        val expanded =
            remember(permissions) {
                mutableStateMapOf<ProtectionLevel, Boolean>().apply {
                    orderedLevels.forEach { put(it, defaultExpanded(it)) }
                }
            }

        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            orderedLevels.forEach { level ->
                val rows = grouped[level].orEmpty()
                if (rows.isEmpty()) return@forEach
                val isExpanded = expanded[level] ?: defaultExpanded(level)
                val collapsible = isCollapsibleByDefault(level)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PermissionGroupHeader(
                        level = level,
                        count = rows.size,
                        expanded = isExpanded,
                        collapsible = collapsible,
                        onToggle = { expanded[level] = !isExpanded },
                    )
                    if (isExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            rows
                                .sortedBy { it.displayName }
                                .forEach { perm -> PermissionRow(perm) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionGroupHeader(
    level: ProtectionLevel,
    count: Int,
    expanded: Boolean,
    collapsible: Boolean,
    onToggle: () -> Unit,
) {
    val (color, label) = protectionStyle(level)
    val expandedDesc = stringResource(Res.string.apps_section_state_expanded)
    val collapsedDesc = stringResource(Res.string.apps_section_state_collapsed)

    val baseModifier =
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp)

    val gestureModifier =
        if (collapsible) {
            baseModifier
                .clickable(onClick = onToggle)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    heading()
                    contentDescription = "$label, $count"
                    stateDescription = if (expanded) expandedDesc else collapsedDesc
                }
        } else {
            baseModifier.semantics(mergeDescendants = true) {
                heading()
                contentDescription = "$label, $count"
            }
        }

    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Row(
        modifier = gestureModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KomiText(
            text = label,
            role = KomiTextRole.LabelSmall,
            color = color,
            modifier = Modifier.weight(1f, fill = false),
        )
        KomiText(
            text = count.toString(),
            role = KomiTextRole.LabelSmall,
            color = color,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(shape.cornerSmall))
                    .background(color.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 1.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        if (collapsible) {
            KomiIcon(
                imageVector =
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionRow(permission: ApkPermission) {
    val (chipColor, chipLabel) = protectionStyle(permission.protectionLevel)
    val chipAlpha = if (permission.protectionLevel == ProtectionLevel.DANGEROUS) 0.22f else 0.16f

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val colors = LocalPersonality.current.colors
        val shape = LocalPersonality.current.shape
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            KomiText(
                text = permission.displayName,
                role = KomiTextRole.Label,
                color = colors.onSurface,
            )
            permission.description?.let { desc ->
                KomiText(
                    text = desc,
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                )
            }

            KomiText(
                text = permission.name,
                role = KomiTextRole.Mono,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.copyableOnLongPress(permission.name),
            )
        }
        KomiText(
            text = chipLabel,
            role = KomiTextRole.LabelSmall,
            color = chipColor,
            modifier =
                Modifier
                    .clip(RoundedCornerShape(shape.cornerSmall))
                    .background(chipColor.copy(alpha = chipAlpha))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
        )
    }
}

private fun defaultExpanded(level: ProtectionLevel): Boolean =
    when (level) {
        ProtectionLevel.DANGEROUS,
        ProtectionLevel.PRIVILEGED,
        ProtectionLevel.SIGNATURE,
        -> true

        ProtectionLevel.NORMAL,
        ProtectionLevel.UNKNOWN,
        -> false
    }

private fun isCollapsibleByDefault(level: ProtectionLevel): Boolean = level == ProtectionLevel.NORMAL || level == ProtectionLevel.UNKNOWN

@Composable
private fun ComponentsSection(inspection: ApkInspection) {
    InspectSection(
        title = stringResource(Res.string.apk_inspect_components),
        icon = Icons.Default.Apps,
    ) {
        InspectRow(
            label = stringResource(Res.string.apk_inspect_section_activities),
            value = inspection.activityCount.toString(),
        )
        InspectRow(
            label = stringResource(Res.string.apk_inspect_section_services),
            value = inspection.serviceCount.toString(),
        )
        InspectRow(
            label = stringResource(Res.string.apk_inspect_section_receivers),
            value = inspection.receiverCount.toString(),
        )
        inspection.mainActivity?.let { entry ->
            InspectRow(
                label = stringResource(Res.string.apk_inspect_section_main_activity),
                value = entry,
                monospace = true,
            )
        }
    }
}

@Composable
private fun FileSection(inspection: ApkInspection) {
    InspectSection(
        title = stringResource(Res.string.apk_inspect_file_info),
        icon = Icons.Default.Folder,
    ) {
        inspection.fileSizeBytes?.let { size ->
            InspectRow(
                label = stringResource(Res.string.apk_inspect_size),
                value = formatFileSize(size),
            )
        }
        inspection.filePath?.let { path ->
            InspectRow(
                label = "",
                value = path,
                monospace = true,
            )
        }
    }
}

@Composable
private fun InspectSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(personality.shape.cornerSmall))
                        .background(colors.primaryTint),
                contentAlignment = Alignment.Center,
            ) {
                KomiIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            KomiText(
                text = title,
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
            )
        }
        KomiSurface(
            modifier = Modifier.fillMaxWidth(),
            elevation = KomiSurfaceElevation.Flat,
            contentPadding = PaddingValues(16.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun InspectRow(
    label: String,
    value: String,
    monospace: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val colors = LocalPersonality.current.colors
        if (label.isNotEmpty()) {
            KomiText(
                text = label,
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
                modifier = Modifier.width(120.dp),
            )
        }
        val valueModifier =
            if (monospace) {
                Modifier.weight(1f).copyableOnLongPress(value)
            } else {
                Modifier.weight(1f)
            }
        KomiText(
            text = value,
            role = if (monospace) KomiTextRole.Mono else KomiTextRole.Body,
            color = colors.onSurface,
            modifier = valueModifier,
        )
    }
}

@Composable
private fun DangerNote(text: String) {
    val colors = LocalPersonality.current.colors
    val noteShape = RoundedCornerShape(LocalPersonality.current.shape.cornerSmall)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            Modifier
                .clip(noteShape)
                .background(colors.error)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        KomiIcon(
            imageVector = Icons.Default.Apartment,
            contentDescription = null,
            tint = colors.onError,
            modifier = Modifier.size(16.dp),
        )
        KomiText(
            text = text,
            role = KomiTextRole.LabelSmall,
            color = colors.onError,
        )
    }
}

@Composable
private fun protectionStyle(level: ProtectionLevel): Pair<Color, String> {
    val colors = LocalPersonality.current.colors
    val red = colors.error
    val amber = LocalStatusColors.current.protectionSignature
    val deepAmber = LocalStatusColors.current.protectionPrivileged
    val neutral = colors.onSurfaceVariant
    val muted = colors.outline
    return when (level) {
        ProtectionLevel.DANGEROUS -> red to stringResource(Res.string.apk_inspect_protection_dangerous)
        ProtectionLevel.PRIVILEGED -> deepAmber to stringResource(Res.string.apk_inspect_protection_privileged)
        ProtectionLevel.SIGNATURE -> amber to stringResource(Res.string.apk_inspect_protection_signature)
        ProtectionLevel.NORMAL -> neutral to stringResource(Res.string.apk_inspect_protection_normal)
        ProtectionLevel.UNKNOWN -> muted to stringResource(Res.string.apk_inspect_protection_unknown)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.copyableOnLongPress(value: String): Modifier {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    return this.combinedClickable(
        onClick = {},
        onLongClick = {
            clipboard.setText(AnnotatedString(value))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        },
    )
}
