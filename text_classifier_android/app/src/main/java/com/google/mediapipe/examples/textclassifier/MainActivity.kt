package com.google.mediapipe.examples.textclassifier

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.mediapipe.examples.textclassifier.databinding.ActivityMainBinding
import com.google.mediapipe.tasks.text.textclassifier.TextClassifierResult
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var _activityMainBinding: ActivityMainBinding? = null
    private val activityMainBinding get() = _activityMainBinding!!
    private lateinit var classifierHelper: TextClassifierHelper
    private var tts: TextToSpeech? = null
    private var status = ""
    private val REQUEST_CODE_SPEECH_INPUT = 1

    private val adapter by lazy {
        ResultsAdapter()
    }

    private val listener = object :
        TextClassifierHelper.TextResultsListener {
        override fun onResult(
            results: TextClassifierResult,
            inferenceTime: Long
        ) {
            status = results.classificationResult()
                .classifications().first()
                .categories().sortedByDescending { it.score() }[0].categoryName()

            speakOut()

            runOnUiThread {
                activityMainBinding.bottomSheetLayout.inferenceTimeVal.text =
                    String.format("%d ms", inferenceTime)

                adapter.updateResult(
                    results.classificationResult()
                        .classifications().first()
                        .categories().sortedByDescending { it.score() },
                    classifierHelper.currentModel
                )
            }
        }

        override fun onError(error: String) {
            Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

        classifierHelper = TextClassifierHelper(context = this@MainActivity, listener = listener)

        tts = TextToSpeech(this, this)

        activityMainBinding.classifyBtn.setOnClickListener {
            if (activityMainBinding.inputText.text.isNullOrEmpty()) {
                classifierHelper.classify(getString(R.string.default_edit_text))
            } else {
                classifierHelper.classify(activityMainBinding.inputText.text.toString())
            }
        }

        // Speech to text button integration
        activityMainBinding.speechBtn.setOnClickListener {
            startSpeechToText()
        }

        activityMainBinding.results.adapter = adapter
        initBottomSheetControls()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts!!.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "The Language is not supported!")
            }
        }
    }

    private fun speakOut() {
        var text = status
        if (status == "1") text = "Positive"
        if (status == "0") text = "Negative"
        tts!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    // Initiate speech-to-text
    private fun startSpeechToText() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to classify")
        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT)
        } catch (e: Exception) {
            Toast.makeText(this, "Speech recognition not available: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
            val result: ArrayList<String> = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) as ArrayList<String>
            val spokenText = result[0]
            activityMainBinding.inputText.setText(spokenText)
            classifierHelper.classify(spokenText)
        }
    }

    private fun initBottomSheetControls() {
        val behavior = BottomSheetBehavior.from(activityMainBinding.bottomSheetLayout.bottomSheetLayout)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        activityMainBinding.bottomSheetLayout.modelSelector.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.wordvec -> {
                    classifierHelper.currentModel = TextClassifierHelper.WORD_VEC
                    classifierHelper.initClassifier()
                }
                R.id.mobilebert -> {
                    classifierHelper.currentModel = TextClassifierHelper.MOBILEBERT
                    classifierHelper.initClassifier()
                }
            }
        }
    }

    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            finishAfterTransition()
        } else {
            super.onBackPressed()
        }
    }
}