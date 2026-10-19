package io.github.supermonster003.autojs6.plugin.threestoneai.speech;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;

import java.util.ArrayList;

/**
 * Answers RECOGNIZE_SPEECH with the reply scripted in the request's test extras. With
 * {@link #EXTRA_ECHO} it returns the standard request extras instead, so the test can check them.
 */
public final class FakeRecognizerActivity extends Activity {
    static final String EXTRA_RESULT_CODE = "io.github.supermonster003.threestoneai.test.RESULT_CODE";
    static final String EXTRA_RESULT_TEXT = "io.github.supermonster003.threestoneai.test.RESULT_TEXT";
    static final String EXTRA_ECHO = "io.github.supermonster003.threestoneai.test.ECHO";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent request = getIntent();
        int code = request.getIntExtra(EXTRA_RESULT_CODE, RESULT_CANCELED);
        String text = request.getStringExtra(EXTRA_RESULT_TEXT);
        if (request.getBooleanExtra(EXTRA_ECHO, false)) {
            text = request.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL)
                + "|" + request.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)
                + "|" + request.getStringExtra(RecognizerIntent.EXTRA_PROMPT)
                + "|" + request.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS, -1);
        }
        Intent data = null;
        if (text != null) {
            ArrayList<String> results = new ArrayList<>();
            results.add(text);
            data = new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, results);
        }
        setResult(code, data);
        finish();
    }
}
