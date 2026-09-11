package org.routingplatform.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.routingplatform.app.profile.ProfileAccentColor

internal data class NavigationSettingTile(
    val testTag:
        String,

    val title:
        String,

    val value:
        String,

    val onClick:
        () -> Unit,
)

@Composable
internal fun NavigationSettingsGrid(
    items:
        List<NavigationSettingTile>,

    accentColor:
        ProfileAccentColor,

    modifier:
        Modifier =
        Modifier,
) {
    require(
        items.size ==
            10
    ) {
        "Navigation settings grid requires exactly 10 items."
    }

    val dark =
        MaterialTheme
            .colorScheme
            .surface
            .luminance() <
            0.5f

    Column(
        modifier =
            modifier.fillMaxWidth(),

        verticalArrangement =
            Arrangement.spacedBy(
                4.dp
            ),
    ) {
        items
            .chunked(
                2
            )
            .forEachIndexed {
                    rowIndex,
                    rowItems ->

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.spacedBy(
                            4.dp
                        ),
                ) {
                    rowItems
                        .forEachIndexed {
                                columnIndex,
                                item ->

                            val itemIndex =
                                rowIndex *
                                    2 +
                                    columnIndex

                            val contentColor =
                                NavigationAccentColorPresentation
                                    .tileColor(
                                        preference =
                                            accentColor,

                                        index =
                                            itemIndex,

                                        dark =
                                            dark,

                                        fallback =
                                            MaterialTheme
                                                .colorScheme
                                                .primary,
                                    )

                            OutlinedButton(
                                modifier =
                                    Modifier
                                        .weight(
                                            1.0f
                                        )
                                        .heightIn(
                                            min =
                                                60.dp
                                        )
                                        .testTag(
                                            item.testTag
                                        ),

                                colors =
                                    ButtonDefaults
                                        .outlinedButtonColors(
                                            contentColor =
                                                contentColor
                                        ),

                                contentPadding =
                                    PaddingValues(
                                        horizontal =
                                            8.dp,

                                        vertical =
                                            5.dp,
                                    ),

                                onClick =
                                    item.onClick,
                            ) {
                                Column(
                                    modifier =
                                        Modifier.fillMaxWidth(),

                                    horizontalAlignment =
                                        Alignment.Start,
                                ) {
                                    Text(
                                        text =
                                            item.title,

                                        fontWeight =
                                            FontWeight.Medium,

                                        style =
                                            MaterialTheme
                                                .typography
                                                .labelMedium,
                                    )

                                    Text(
                                        text =
                                            item.value,

                                        style =
                                            MaterialTheme
                                                .typography
                                                .bodySmall,
                                    )
                                }
                            }
                        }

                    if (
                        rowItems.size ==
                            1
                    ) {
                        Spacer(
                            modifier =
                                Modifier.weight(
                                    1.0f
                                )
                        )
                    }
                }
            }
    }
}