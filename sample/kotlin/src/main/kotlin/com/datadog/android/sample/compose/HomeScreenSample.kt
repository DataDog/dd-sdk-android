/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A realistic monitoring-dashboard home screen used to exercise the DatadogVital span PoC.
 *
 * The UI is organised in independently-traced sections so the compose and render vitals show
 * a realistic multi-level waterfall:
 *
 * DatadogFirstRenderRoot("HomeScreen")
 *   DatadogVital("home_root")
 *     DatadogVital("header")
 *       DatadogVital("greeting")
 *       DatadogVital("header_actions")
 *     DatadogVital("system_status")
 *     DatadogVital("stats_row")
 *       DatadogVital("stat_monitors")
 *       DatadogVital("stat_alerts")
 *       DatadogVital("stat_incidents")
 *     DatadogVital("quick_actions")
 *       DatadogVital("action_logs")
 *       DatadogVital("action_apm")
 *       DatadogVital("action_rum")
 *       DatadogVital("action_infra")
 *     DatadogVital("recent_alerts")
 *       DatadogVital("alert_item_0..2")
 *     DatadogVital("featured_dashboards")
 *       DatadogVital("dashboard_card_0..2")
 */
@Composable
internal fun HomeScreenSample() {
    DatadogFirstRenderRoot(screenName = "HomeScreen") {
        DatadogTrace(tag = "home_root") {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item { HomeHeader() }
                item { Spacer(modifier = Modifier.height(12.dp)) }
                item { SystemStatusBanner() }
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item { StatsRow() }
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item { QuickActionsGrid() }
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item { RecentAlertsSection() }
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item { FeaturedDashboardsSection() }
            }
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────

@Composable
private fun HomeHeader() {
    DatadogTrace(tag = "header") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DatadogTrace(tag = "greeting", modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colors.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Good morning,",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Platform Team",
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            DatadogTrace(tag = "header_actions") {
                Row {
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Filled.Notifications,
                            contentDescription = "Notifications"
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            }
        }
    }
}

// ── System status banner ───────────────────────────────────────────────────────

@Composable
private fun SystemStatusBanner() {
    DatadogTrace(tag = "system_status") {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = Color(0xFF1A7F5A),
            elevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "All Systems Operational",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "99.98% uptime over the last 30 days",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    }
}

// ── Stats row ─────────────────────────────────────────────────────────────────

private data class StatItem(val label: String, val value: String, val tag: String, val color: Color)

private val statItems = listOf(
    StatItem("Monitors", "1 284", "stat_monitors", Color(0xFF6366F1)),
    StatItem("Open Alerts", "3", "stat_alerts", Color(0xFFF59E0B)),
    StatItem("Incidents", "0", "stat_incidents", Color(0xFF10B981))
)

@Composable
private fun StatsRow() {
    DatadogTrace(tag = "stats_row") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            statItems.forEach { stat ->
                DatadogTrace(
                    tag = stat.tag,
                    modifier = Modifier.weight(1f)
                ) {
                    StatCard(stat = stat)
                }
            }
        }
    }
}

@Composable
private fun StatCard(stat: StatItem) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(stat.color)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stat.value,
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stat.label,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

// ── Quick actions grid ─────────────────────────────────────────────────────────

private data class QuickAction(
    val label: String,
    val tag: String,
    val color: Color,
    val icon: ImageVector
)

private val quickActions = listOf(
    QuickAction("Logs", "action_logs", Color(0xFF6366F1), Icons.Filled.Check),
    QuickAction("APM", "action_apm", Color(0xFF3B82F6), Icons.Filled.Check),
    QuickAction("RUM", "action_rum", Color(0xFFF59E0B), Icons.Filled.Check),
    QuickAction("Infra", "action_infra", Color(0xFF10B981), Icons.Filled.Check)
)

@Composable
private fun QuickActionsGrid() {
    DatadogTrace(tag = "quick_actions") {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "Quick Access",
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                quickActions.forEach { action ->
                    DatadogTrace(
                        tag = action.tag,
                        modifier = Modifier.weight(1f)
                    ) {
                        QuickActionTile(action = action)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionTile(action: QuickAction) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(action.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.label,
                    tint = action.color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = action.label,
                style = MaterialTheme.typography.caption,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ── Recent alerts ──────────────────────────────────────────────────────────────

private data class AlertItem(
    val title: String,
    val service: String,
    val severity: String,
    val time: String,
    val isWarning: Boolean
)

private val recentAlerts = listOf(
    AlertItem("p99 latency > 500ms", "payments-api", "WARN", "2 min ago", true),
    AlertItem("Error rate spike", "checkout-service", "CRITICAL", "11 min ago", true),
    AlertItem("CPU usage recovered", "worker-pool-3", "OK", "34 min ago", false)
)

@Composable
private fun RecentAlertsSection() {
    DatadogTrace(tag = "recent_alerts") {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Alerts",
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "View all",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = 2.dp
            ) {
                Column {
                    recentAlerts.forEachIndexed { index, alert ->
                        DatadogTrace(tag = "alert_item_$index") {
                            AlertRow(alert = alert)
                        }
                        if (index < recentAlerts.lastIndex) {
                            Divider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertRow(alert: AlertItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val iconTint = when {
            alert.severity == "CRITICAL" -> Color(0xFFEF4444)
            alert.isWarning -> Color(0xFFF59E0B)
            else -> Color(0xFF10B981)
        }
        Icon(
            imageVector = if (alert.isWarning) Icons.Filled.Warning else Icons.Filled.Check,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = alert.title,
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${alert.service} · ${alert.time}",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = iconTint.copy(alpha = 0.12f)
        ) {
            Text(
                text = alert.severity,
                style = MaterialTheme.typography.overline,
                color = iconTint,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

// ── Featured dashboards ────────────────────────────────────────────────────────

private data class DashboardCard(
    val title: String,
    val subtitle: String,
    val tag: String,
    val color: Color
)

private val featuredDashboards = listOf(
    DashboardCard(
        "API Overview",
        "Response times & error rates",
        "dashboard_card_api",
        Color(0xFF6366F1)
    ),
    DashboardCard(
        "Infrastructure",
        "Host health & resource usage",
        "dashboard_card_infra",
        Color(0xFF3B82F6)
    ),
    DashboardCard(
        "RUM Sessions",
        "User journeys & crashes",
        "dashboard_card_rum",
        Color(0xFFF59E0B)
    )
)

@Composable
private fun FeaturedDashboardsSection() {
    DatadogTrace(tag = "featured_dashboards") {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Featured Dashboards",
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "View all",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(featuredDashboards) { _, dashboard ->
                    DatadogTrace(tag = dashboard.tag) {
                        DashboardCardItem(dashboard = dashboard)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardCardItem(dashboard: DashboardCard) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = 3.dp,
        modifier = Modifier
            .width(200.dp)
            .height(120.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            dashboard.color.copy(alpha = 0.15f),
                            dashboard.color.copy(alpha = 0.05f)
                        )
                    )
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(dashboard.color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = dashboard.color,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = dashboard.title,
                    style = MaterialTheme.typography.body2,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dashboard.subtitle,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
