package com.smartledger.ui.category

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smartledger.SmartLedgerApp
import com.smartledger.data.db.entity.Category
import com.smartledger.ui.components.SmartLedgerDialog
import com.smartledger.ui.components.SmartLedgerInputDialog
import com.smartledger.ui.theme.SmartLedgerColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// ═══════════════════════════════════════════════════════
// ViewModel
// ═══════════════════════════════════════════════════════

class CategoryManageViewModel(application: Application) : AndroidViewModel(application) {
    private val categoryRepo = (application as SmartLedgerApp).categoryRepository

    val expenseCategories: Flow<List<Category>> = categoryRepo.getByType("expense")
    val incomeCategories: Flow<List<Category>> = categoryRepo.getByType("income")

    fun insertCategory(name: String, type: String, color: Long) {
        viewModelScope.launch {
            categoryRepo.insert(
                Category(name = name, type = type, color = color, icon = "default")
            )
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            categoryRepo.update(category)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            categoryRepo.delete(category)
        }
    }
}

// ═══════════════════════════════════════════════════════
// Screen
// ═══════════════════════════════════════════════════════

@Composable
fun CategoryManageScreen(
    onBack: () -> Unit = {},
    viewModel: CategoryManageViewModel = viewModel()
) {
    var selectedType by remember { mutableStateOf("expense") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }

    val expenseCategories by viewModel.expenseCategories.collectAsState(initial = emptyList())
    val incomeCategories by viewModel.incomeCategories.collectAsState(initial = emptyList())

    val currentCategories = if (selectedType == "expense") expenseCategories else incomeCategories

    Box(modifier = Modifier.fillMaxSize().background(SmartLedgerColors.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // ═══ 顶部栏 ═══
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = SmartLedgerColors.fg)
                    }
                    Text(
                        text = "分类管理",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = SmartLedgerColors.fg
                    )
                }
            }

            // ═══ 类型切换 ═══
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TypeTab("支出", selectedType == "expense") { selectedType = "expense" }
                    Spacer(modifier = Modifier.width(48.dp))
                    TypeTab("收入", selectedType == "income") { selectedType = "income" }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            // ═══ 分类列表 ═══
            items(currentCategories) { category ->
                CategoryItem(
                    category = category,
                    onEdit = { editingCategory = category },
                    onDelete = { viewModel.deleteCategory(category) }
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // ═══ 添加按钮 ═══
            item {
                TextButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加分类")
                }
            }
        }
    }

    // ═══ 添加分类弹窗 ═══
    if (showAddDialog) {
        AddCategoryDialog(
            type = selectedType,
            onAdd = { name, color ->
                viewModel.insertCategory(name, selectedType, color)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    // ═══ 编辑分类弹窗 ═══
    if (editingCategory != null) {
        EditCategoryDialog(
            category = editingCategory!!,
            onSave = { viewModel.updateCategory(it); editingCategory = null },
            onDismiss = { editingCategory = null }
        )
    }
}

@Composable
private fun TypeTab(text: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) SmartLedgerColors.fg else SmartLedgerColors.fgSecondary
        )
        if (selected) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(2.dp)
                    .background(SmartLedgerColors.fg, RoundedCornerShape(1.dp))
            )
        }
    }
}

@Composable
private fun CategoryItem(
    category: Category,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(SmartLedgerColors.surfaceHover, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                getCategoryIcon(category.name),
                contentDescription = category.name,
                tint = SmartLedgerColors.fgSecondary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyLarge,
            color = SmartLedgerColors.fg,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onEdit) {
            Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = SmartLedgerColors.fgSecondary, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { showDeleteConfirm = true }) {
            Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = SmartLedgerColors.fgSecondary, modifier = Modifier.size(20.dp))
        }
    }

    if (showDeleteConfirm) {
        SmartLedgerDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = "删除分类",
            text = "确定要删除「${category.name}」吗？",
            confirmText = "删除",
            confirmColor = SmartLedgerColors.expense,
            onConfirm = { onDelete(); showDeleteConfirm = false },
            dismissText = "取消"
        )
    }
}

@Composable
private fun AddCategoryDialog(
    type: String,
    onAdd: (String, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    val colors = listOf(0xFFD94848, 0xFF3E63DD, 0xFF2D9D63, 0xFF9C27B0, 0xFFFF7043, 0xFF795548)
    var selectedColor by remember { mutableStateOf(colors[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = SmartLedgerColors.surface,
        titleContentColor = SmartLedgerColors.fg,
        title = {
            Text("添加${if (type == "expense") "支出" else "收入"}分类", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分类名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SmartLedgerColors.accent,
                        focusedLabelColor = SmartLedgerColors.accent,
                        cursorColor = SmartLedgerColors.accent
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("选择颜色", style = MaterialTheme.typography.labelMedium, color = SmartLedgerColors.fgSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(color), RoundedCornerShape(8.dp))
                                .clickable { selectedColor = color }
                                .then(
                                    if (selectedColor == color)
                                        Modifier.padding(2.dp)
                                    else Modifier
                                )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) onAdd(name, selectedColor)
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SmartLedgerColors.accent)
            ) { Text("添加", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = SmartLedgerColors.fgSecondary) }
        }
    )
}

@Composable
private fun EditCategoryDialog(
    category: Category,
    onSave: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(category.name) }

    SmartLedgerInputDialog(
        onDismissRequest = onDismiss,
        title = "编辑分类",
        label = "分类名称",
        value = name,
        onValueChange = { name = it },
        confirmText = "保存",
        onConfirm = {
            if (name.isNotBlank()) onSave(category.copy(name = name))
        }
    )
}

private fun getCategoryIcon(name: String): ImageVector {
    return when (name) {
        "餐饮" -> Icons.Outlined.Restaurant
        "交通" -> Icons.Outlined.DirectionsBus
        "购物" -> Icons.Outlined.ShoppingBag
        "娱乐" -> Icons.Outlined.OndemandVideo
        "居住" -> Icons.Outlined.Home
        "医疗" -> Icons.Outlined.FavoriteBorder
        "教育" -> Icons.Outlined.MenuBook
        "通讯" -> Icons.Outlined.Phone
        "日用" -> Icons.Outlined.ShoppingCart
        "工资" -> Icons.Outlined.AttachMoney
        "理财" -> Icons.Outlined.TrendingUp
        "红包" -> Icons.Outlined.CardGiftcard
        "转账" -> Icons.Outlined.SwapHoriz
        else -> Icons.Outlined.MoreHoriz
    }
}
