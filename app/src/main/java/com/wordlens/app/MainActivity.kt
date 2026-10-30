package com.wordlens.app

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private val adapter = WordAdapter()
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var preview: ImageView
    private lateinit var btnCopy: Button

    private var photoUri: Uri? = null
    private var lookupJob: Job? = null
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) photoUri?.let { processImage(it) }
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) processImage(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.status)
        progress = findViewById(R.id.progress)
        preview = findViewById(R.id.preview)
        btnCopy = findViewById(R.id.btnCopy)

        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<Button>(R.id.btnCamera).setOnClickListener { launchCamera() }
        findViewById<Button>(R.id.btnGallery).setOnClickListener { pickImage.launch("image/*") }
        btnCopy.setOnClickListener { copyAll() }
    }

    private fun launchCamera() {
        try {
            val dir = File(cacheDir, "photos").apply { mkdirs() }
            val file = File(dir, "page_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            photoUri = uri
            takePicture.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "ক্যামেরা খোলা যায়নি: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun processImage(uri: Uri) {
        lookupJob?.cancel()
        adapter.submit(emptyList())
        btnCopy.visibility = View.GONE
        progress.visibility = View.GONE

        preview.visibility = View.VISIBLE
        preview.setImageURI(uri)
        status.text = "ছবি থেকে লেখা পড়ছি…"

        val image = try {
            InputImage.fromFilePath(this, uri)
        } catch (e: Exception) {
            status.text = "ছবি খোলা যায়নি: ${e.message}"
            return
        }

        recognizer.process(image)
            .addOnSuccessListener { result -> handleText(result.text) }
            .addOnFailureListener { e -> status.text = "লেখা পড়া যায়নি: ${e.message}" }
    }

    private fun extractWords(raw: String): List<String> {
        val cleaned = raw.replace("-\n", "").replace('’', '\'')
        val regex = Regex("[A-Za-z]+(?:'[A-Za-z]+)?")
        val seen = LinkedHashSet<String>()
        for (m in regex.findAll(cleaned)) {
            val w = m.value.lowercase().removeSuffix("'s")
            if (w.length >= 2 || w == "a" || w == "i") seen.add(w)
        }
        return seen.toList()
    }

    private fun handleText(raw: String) {
        val words = extractWords(raw)
        if (words.isEmpty()) {
            status.text = "কোনো ইংরেজি শব্দ পাওয়া যায়নি। আরও পরিষ্কার ছবি তুলে আবার চেষ্টা করুন।"
            return
        }

        val items = words.map { WordItem(it) }
        adapter.submit(items)
        btnCopy.visibility = View.VISIBLE

        progress.max = items.size
        progress.progress = 0
        progress.visibility = View.VISIBLE
        status.text = "মোট ${items.size}টি শব্দ পাওয়া গেছে। অর্থ খুঁজছি… (0/${items.size})"

        lookupJob = lifecycleScope.launch {
            val sem = Semaphore(5)
            var done = 0
            coroutineScope {
                items.forEachIndexed { index, item ->
                    launch {
                        val entry = sem.withPermit {
                            withContext(Dispatchers.IO) { Dictionary.lookup(item.word) }
                        }
                        item.bangla = entry.bangla
                        item.synonyms = entry.synonyms
                        done++
                        adapter.changed(index)
                        progress.progress = done
                        status.text =
                            "মোট ${items.size}টি শব্দ। অর্থ খোঁজা হয়েছে: $done/${items.size}"
                    }
                }
            }
            status.text = "মোট ${items.size}টি শব্দ — সব অর্থ পাওয়া গেছে ✔"
            progress.visibility = View.GONE
        }
    }

    private fun copyAll() {
        val text = adapter.items().joinToString("\n\n") {
            "${it.word}\n${it.bangla ?: ""}\nসমার্থক: ${it.synonyms ?: ""}"
        }
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Word Lens", text))
        Toast.makeText(this, "কপি হয়েছে", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer.close()
    }
}
