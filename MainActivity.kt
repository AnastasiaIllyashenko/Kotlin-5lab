package softserve.academy.mylist

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource // Додано для локалізації
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import softserve.academy.mylist.ui.theme.MyListTheme
import androidx.lifecycle.viewmodel.compose.viewModel


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyListTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        ShoppingListScreen()
                    }
                }
            }
        }
    }
}

@Entity(tableName = "shopping_items")
data class ShoppingItem(
    val name: String,
    val isBought: Boolean = false,
    @PrimaryKey(autoGenerate = true) val id: Int = 0
)

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY id DESC")
    fun getAllItems(): List<ShoppingItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertItem(item: ShoppingItem)

    @Update
    fun updateItem(item: ShoppingItem)

    @Delete
    fun deleteItem(item: ShoppingItem)
}

@Database(entities = [ShoppingItem::class], version = 1)
abstract class ShoppingDatabase : RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        @Volatile
        private var INSTANCE: ShoppingDatabase? = null

        fun getInstance(context: Context): ShoppingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShoppingDatabase::class.java,
                    "shopping_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class ShoppingListViewModel(application: Application) : AndroidViewModel(application) {
    private val dao: ShoppingDao = ShoppingDatabase.getInstance(application).shoppingDao()
    private val _shoppingList = mutableStateListOf<ShoppingItem>()
    val shoppingList: List<ShoppingItem> get() = _shoppingList

    init {
        loadShoppingList()
    }

    private fun loadShoppingList() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = dao.getAllItems()
            _shoppingList.clear()
            _shoppingList.addAll(items)
        }
    }

    fun addItem(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val newItem = ShoppingItem(name = name)
            dao.insertItem(newItem)
            loadShoppingList()
        }
    }

    fun toggleBought(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = _shoppingList[index]
            val updatedItem = item.copy(isBought = !item.isBought)
            dao.updateItem(updatedItem)
            _shoppingList[index] = updatedItem
        }
    }

    // Додано: редагування товару
    fun editItem(index: Int, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = _shoppingList[index]
            val updatedItem = item.copy(name = newName)
            dao.updateItem(updatedItem)
            _shoppingList[index] = updatedItem
        }
    }

    // Додано: видалення товару
    fun deleteItem(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = _shoppingList[index]
            dao.deleteItem(item)
            _shoppingList.removeAt(index)
        }
    }
}

class ShoppingListViewModelFactory(private val application: Application) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShoppingListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShoppingListViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun AddItemButton(addItem: (String) -> Unit = {}) {
    var text by remember { mutableStateOf("") }

    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(id = R.string.add_item)) } // Локалізація
        )
        Button(onClick = {
            if (text.isNotEmpty()) {
                addItem(text)
                text = ""
            }
        }) {
            Text(stringResource(id = R.string.add)) // Локалізація
        }
    }
}

@Composable
fun ShoppingItemCard(
    item: ShoppingItem,
    onToggleBought: () -> Unit = {},
    onEdit: (String) -> Unit = {},
    onDelete: () -> Unit = {}
) {
    var showEditField by remember { mutableStateOf(false) }
    var editedText by remember { mutableStateOf(item.name) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(Color.LightGray)
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(checked = item.isBought, onCheckedChange = { onToggleBought() })

            if (showEditField) {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    onEdit(editedText)
                    showEditField = false
                }) {
                    Text("OK")
                }
            } else {
                Text(
                    text = item.name,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleBought() },
                    fontSize = 18.sp
                )
            }

            Button(onClick = { showEditField = !showEditField }) {
                Text(text = stringResource(id = R.string.edit)) //  Локалізація
            }
            Button(onClick = onDelete) {
                Text(text = stringResource(id = R.string.delete)) //  Локалізація
            }
        }
    }
}

@Composable
fun ShoppingListScreen(viewModel: ShoppingListViewModel = viewModel(
    factory = ShoppingListViewModelFactory(LocalContext.current.applicationContext as Application)
)) {
    val boughtCount = viewModel.shoppingList.count { it.isBought }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Додано: лічильник куплених товарів
        Text(
            text = stringResource(id = R.string.bought_items_count, boughtCount),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        AddItemButton { viewModel.addItem(it) }

        LazyColumn {
            itemsIndexed(viewModel.shoppingList) { index, item ->
                ShoppingItemCard(
                    item = item,
                    onToggleBought = { viewModel.toggleBought(index) },
                    onEdit = { newName -> viewModel.editItem(index, newName) },
                    onDelete = { viewModel.deleteItem(index) }
                )
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ShoppingListScreenPreview() {
    ShoppingListScreen()
}

@Composable
fun ShoppingItemCardPreview() {
    var toggleState by remember { mutableStateOf(false) }
    ShoppingItemCard(
        ShoppingItem("Молоко", isBought = toggleState),
        onToggleBought = { toggleState = !toggleState }
    )
}
