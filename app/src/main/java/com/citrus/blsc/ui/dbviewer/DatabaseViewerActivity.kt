package com.citrus.blsc.ui.dbviewer

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.citrus.blsc.R
import com.citrus.blsc.utils.DatabaseHelper
import com.citrus.blsc.utils.AnimationHelper
import com.citrus.blsc.utils.ThemeHelper
import java.io.File

class DatabaseViewerActivity : AppCompatActivity() {

    private lateinit var tvDatabaseInfo: TextView
    private lateinit var tvSchema: TextView
    private lateinit var spinnerTables: Spinner
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnExport: Button
    private lateinit var btnBack: Button
    private lateinit var progressBar: ProgressBar

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_database_viewer)

        initViews()
        loadDatabaseInfo()
        setupTableSpinner()
        setupButtons()
    }

    private fun initViews() {
        tvDatabaseInfo = findViewById(R.id.tvDatabaseInfo)
        tvSchema = findViewById(R.id.tvSchema)
        spinnerTables = findViewById(R.id.spinnerTables)
        recyclerView = findViewById(R.id.recyclerView)
        btnExport = findViewById(R.id.btnExport)
        btnBack = findViewById(R.id.btnBack)
        progressBar = findViewById(R.id.progressBarDb)

        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun loadDatabaseInfo() {
        // Показываем файловую информацию сразу
        val fileInfo = DatabaseHelper.getDatabaseFileInfo(this)
        tvDatabaseInfo.text = "Загрузка статистики...\n\n$fileInfo"

        // Загружаем статистику
        progressBar.visibility = ProgressBar.VISIBLE

        DatabaseHelper.getDatabaseInfo(this) { info ->
            tvDatabaseInfo.text = info
            progressBar.visibility = ProgressBar.GONE
        }

        // Схема базы
        val schema = DatabaseHelper.getDatabaseSchema(this)
        tvSchema.text = schema
    }

    private fun setupTableSpinner() {
        val tables = listOf("search_history", "device_coordinates", "fav_items")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, tables)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTables.adapter = adapter

        spinnerTables.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val tableName = tables[position]
                loadTableData(tableName)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadTableData(tableName: String) {
        val data = DatabaseHelper.getTableData(this, tableName)
        val adapter = DatabaseAdapter(data)
        recyclerView.adapter = adapter

        // Показываем количество записей
        Toast.makeText(this, "Загружено ${data.size} записей из таблицы $tableName", Toast.LENGTH_SHORT).show()
    }

    private fun setupButtons() {
        btnExport.setOnClickListener {
            exportDatabase()
        }

        btnBack.setOnClickListener {
            onBackPressed()
        }
    }

    private fun exportDatabase() {
        progressBar.visibility = ProgressBar.VISIBLE
        btnExport.isEnabled = false

        Thread {
            val exportedFile = DatabaseHelper.exportDatabase(this)

            runOnUiThread {
                progressBar.visibility = ProgressBar.GONE
                btnExport.isEnabled = true

                if (exportedFile != null) {
                    Toast.makeText(this, "База данных экспортирована", Toast.LENGTH_SHORT).show()
                    shareDatabaseFile(exportedFile)
                } else {
                    Toast.makeText(this, "Ошибка экспорта", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun shareDatabaseFile(file: File) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
        } else {
            Uri.fromFile(file)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/x-sqlite3"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Bluetooth Scanner Database Backup")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(intent, "Экспортировать базу данных"))
    }

    override fun onBackPressed() {
        super.onBackPressed()
        AnimationHelper.finishActivityWithAnimation(this)
    }
}