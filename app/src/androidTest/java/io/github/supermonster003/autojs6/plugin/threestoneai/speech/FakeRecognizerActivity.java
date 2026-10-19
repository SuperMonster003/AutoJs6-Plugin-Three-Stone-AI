package io.github.supermonster003.autojs6.plugin.threestoneai.speech;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;

import java.util.ArrayList;

/**
 * Answers RECOGNIZE_SPEECH with the reply scripted in the request's test extras. With
 * {@link #EXTRA_ECHO} it returns the standard request extras instead, so the test can check them.
 * A script ({@link #EXTRA_SCRIPT_ID} with codes and texts) answers successive requests in order and
 * repeats its last entry; it lives in this process, which stays up between requests.
 */
public final class FakeRecognizerActivity extends Activity {
    static final String EXTRA_RESULT_CODE = "io.github.supermonster003.threestoneai.test.RESULT_CODE";
    static final String EXTRA_RESULT_TEXT = "io.github.supermonster003.threestoneai.test.RESULT_TEXT";
    static final String EXTRA_ECHO = "io.github.supermonster003.threestoneai.test.ECHO";
    static final String EXTRA_SCRIPT_ID = "io.github.supermonster003.threestoneai.test.SCRIPT_ID";
    static final String EXTRA_SCRIPT_CODES = "io.github.supermonster003.threestoneai.test.SCRIPT_CODES";
    static final String EXTRA_SCRIPT_TEXTS = "io.github.supermonster003.threestoneai.test.SCRIPT_TEXTS";

    private static String scriptId;
    private static int scriptIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent request = getIntent();
        int code = request.getIntExtra(EXTRA_RESULT_CODE, RESULT_CANCELED);
        String text = request.getStringExtra(EXTRA_RESULT_TEXT);
        String id = request.getStringExtra(EXTRA_SCRIPT_ID);
        int[] codes = request.getIntArrayExtra(EXTRA_SCRIPT_CODES);
        String[] texts = request.getStringArrayExtra(EXTRA_SCRIPT_TEXTS);
        if (id != null && codes != null && texts != null && codes.length > 0 && codes.length == texts.length) {
            if (!id.equals(scriptId)) {
                scriptId = id;
                scriptIndex = 0;
            }
            int index = Math.min(scriptIndex++, codes.length - 1);
            code = codes[index];
            text = texts[index];
        }
        if (request.getBooleanExtra(EXTRA_ECHO, false)) {
            text = request.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL)
                + "|" + request.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)
                + "|" + request.getStringExtra(RecognizerIntent.EXTRA_PROMPT)
                + "|" + request.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS, -1);
        }
        Intent data = null;
        if (text != null && !text.isEmpty()) {
            ArrayList<String> results = new ArrayList<>();
            results.add(text);
            data = new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, results);
        }
        setResult(code, data);
        finish();
    }
}
