package com.psymap.app.fundpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FundFavoritesPage(vm: FundPickerViewModel, onFundClick: (Fund) -> Unit) {
    val favorites by vm.favorites.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(FundBg),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text("我的自选", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
        }

        if (favorites.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = FundCardBg)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val up = favorites.count { it.dayChange > 0 }
                        val down = favorites.count { it.dayChange < 0 }
                        val flat = favorites.size - up - down
                        StatItem("关注", "${favorites.size}", FundTextPrimary)
                        StatItem("今日涨", "$up", FundRed)
                        StatItem("今日跌", "$down", FundGreen)
                        StatItem("平", "$flat", FundTextPrimary)
                    }
                }
            }
        }

        if (favorites.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⭐", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("暂无自选基金", fontSize = 16.sp, color = FundTextSecondary)
                        Text("在基金详情页点击☆添加关注",
                            fontSize = 13.sp, color = FundTextSecondary)
                    }
                }
            }
        }

        items(favorites, key = { it.code }) { fund ->
            FavoriteItem(fund = fund, onClick = { onFundClick(fund) })
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = FundTextSecondary)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun FavoriteItem(fund: Fund, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = FundCardBg),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fund.name, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    Text(fund.code, fontSize = 12.sp, color = FundTextSecondary)
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    Text("%.4f".format(fund.nav), fontSize = 14.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(formatChange(fund.dayChange), fontSize = 14.sp,
                        color = changeColor(fund.dayChange), fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text("AI ${fund.aiScore}%", fontSize = 13.sp,
                color = FundBlue, fontWeight = FontWeight.Medium)
        }
    }
}
