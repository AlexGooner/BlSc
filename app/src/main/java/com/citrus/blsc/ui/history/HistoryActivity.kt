package com.citrus.blsc.ui.history

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.citrus.blsc.R
import com.citrus.blsc.data.model.SearchHistoryItem
import com.citrus.blsc.utils.AnimationHelper
import com.citrus.blsc.utils.ThemeHelper
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity(), HistoryAdapter.OnHistoryItemActionListener {

    private lateinit var searchEditText: TextInputEditText
    private lateinit var dateEditText: TextInputEditText
    private lateinit var clearFiltersButton: Button
    private lateinit var clearHistoryButton: Button
    private lateinit var groupByDetectionsButton: Button
    private lateinit var historyRecyclerView: androidx.recyclerview.widget.RecyclerView
    private lateinit var emptyStateLayout: LinearLayout
    private lateinit var backButton: ImageView

    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var viewModel: HistoryViewModel
    private val historyItems = mutableListOf<SearchHistoryItem>()

    private var isFavouriteFilterActive = false
    private var isGroupByDetections = false
    private var selectedDate: String? = null
    private var searchQuery = ""

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        initViews()
        setupViewModel()
        setupRecyclerView()
        setupListeners()
        observeViewModel()
        loadHistoryItems()
    }

    private fun initViews() {
        searchEditText = findViewById(R.id.search_edit_text)
        dateEditText = findViewById(R.id.date_edit_text)
        clearFiltersButton = findViewById(R.id.clear_filters_button)
        clearHistoryButton = findViewById(R.id.clear_history_button)
        groupByDetectionsButton = findViewById(R.id.group_by_detections_button)
        historyRecyclerView = findViewById(R.id.history_recycler_view)
        emptyStateLayout = findViewById(R.id.empty_state_layout)
        backButton = findViewById(R.id.back_button)
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[HistoryViewModel::class.java]
    }

    private fun observeViewModel() {
        viewModel.historyItems.observe(this) { items ->
            historyItems.clear()
            historyItems.addAll(items)
            historyAdapter.updateItems(historyItems)

            if (historyItems.isEmpty()) {
                historyRecyclerView.visibility = android.view.View.GONE
                emptyStateLayout.visibility = android.view.View.VISIBLE
            } else {
                historyRecyclerView.visibility = android.view.View.VISIBLE
                emptyStateLayout.visibility = android.view.View.GONE
            }
        }
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(historyItems, this, this)
        historyRecyclerView.layoutManager = LinearLayoutManager(this)
        historyRecyclerView.adapter = historyAdapter
        (historyRecyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    private fun setupListeners() {
        backButton.setOnClickListener {
            onBackPressed()
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString() ?: ""
                loadHistoryItems()
            }
        })

        dateEditText.setOnClickListener {
            showDatePicker()
        }


        groupByDetectionsButton.setOnClickListener {
            applyGroupByDetections(!isGroupByDetections)
        }

        clearFiltersButton.setOnClickListener {
            clearFilters()
        }

        clearHistoryButton.setOnClickListener {
            showClearHistoryDialog()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedCalendar = Calendar.getInstance()
                selectedCalendar.set(year, month, dayOfMonth)
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                selectedDate = dateFormat.format(selectedCalendar.time)
                dateEditText.setText(
                    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(
                        selectedCalendar.time
                    )
                )
                loadHistoryItems()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun applyGroupByDetections(enabled: Boolean) {
        val changed = isGroupByDetections != enabled
        isGroupByDetections = enabled
        groupByDetectionsButton.setText(
            if (enabled) R.string.group_by_detections_enabled else R.string.group_by_detections
        )
        groupByDetectionsButton.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, if (enabled) R.color.purple else R.color.grey)
        )
        historyAdapter.setGroupByDetections(enabled)
        if (changed) {
            historyRecyclerView.scrollToPosition(0)
        }
    }

    private fun clearFilters() {
        applyGroupByDetections(false)
        searchEditText.setText("")
        dateEditText.setText("")
        selectedDate = null
        searchQuery = ""
        loadHistoryItems()
    }

    private fun showClearHistoryDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_history))
            .setMessage(getString(R.string.clear_history_confirmation))
            .setPositiveButton(getString(R.string.clear_history)) { _, _ ->
                clearAllHistory()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun loadHistoryItems() {
        when {
            isFavouriteFilterActive && selectedDate != null -> {
                viewModel.loadFavouriteHistoryItemsByDate(selectedDate!!)
            }

            isFavouriteFilterActive -> {
                if (searchQuery.isNotEmpty()) {
                    viewModel.searchFavouriteHistoryItems(searchQuery)
                } else {
                    viewModel.loadFavouriteHistoryItems()
                }
            }

            selectedDate != null -> {
                viewModel.loadHistoryItemsByDate(selectedDate!!)
            }

            searchQuery.isNotEmpty() -> {
                viewModel.searchHistoryItems(searchQuery)
            }

            else -> {
                viewModel.loadAllHistoryItems()
            }
        }
    }

    private fun clearAllHistory() {
        viewModel.clearAllHistory()
    }

    override fun onAddToFavourites(item: SearchHistoryItem) {
        viewModel.addToFavourites(item)
    }

    override fun onRemoveFromFavourites(item: SearchHistoryItem) {
        viewModel.removeFromFavourites(item)
    }

    override fun onDeleteItem(item: SearchHistoryItem) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_record))
            .setMessage(getString(R.string.delete_record_confirmation))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                viewModel.deleteItem(item)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        AnimationHelper.finishActivityWithAnimation(this)
    }
}
