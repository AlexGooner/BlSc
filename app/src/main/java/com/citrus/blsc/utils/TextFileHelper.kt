package com.citrus.blsc.utils

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class TextFileHelper(private val context: Context) {

    private val fileName = "cache"
    private val dir: File = File(context.getExternalFilesDir(null), "cache")


    init {
        // Создаем папку для текстового файла, если она не существует
        if (!dir.exists()) {
            dir.mkdirs()
        }
    }


    fun writeToFile(data: String) {
        val file = File(dir, fileName)
        try {
            FileOutputStream(file, true).use { output ->
                output.write((data + "\n").toByteArray()) // Записываем данные в файл с новой строки
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}