package chin.com.frdict.asyncTask;

import android.content.Context;
import android.os.AsyncTask;
import android.webkit.WebView;

import java.util.regex.Pattern;

import chin.com.frdict.database.BaseDictionarySqliteDatabase;

public class SearchWordAsyncTask extends AsyncTask<Void, Void, String> {
    protected WebView webView;
    protected String word;
    protected String highlight;
    protected Context context;
    protected BaseDictionarySqliteDatabase db;

    /**
     * Constructor
     * @param context A context
     * @param webView The webview to display the word definition
     * @param db The database helper to work with an offline database
     * @param word The word to look up
     */
    public SearchWordAsyncTask(Context context, WebView webView, BaseDictionarySqliteDatabase db, String word) {
        this.context = context;
        this.webView = webView;
        this.word = word;
        this.db = db;
    }

    /**
     * Constructor
     * @param context A context
     * @param webView The webview to display the word definition
     * @param db The database helper to work with an offline database
     * @param word The word to look up
     * @param highlight The string to highlight and scroll to in the word's definition
     */
    public SearchWordAsyncTask(Context context, WebView webView, BaseDictionarySqliteDatabase db, String word, String highlight) {
        this.context = context;
        this.webView = webView;
        this.word = word;
        this.highlight = highlight;
        this.db = db;
    }

    @Override
    protected String doInBackground(Void... params) {
        return getWordDefinitionOffline();
    }

    @Override
    protected void onPostExecute(String html) {
        if (highlight != null) {
            html = html.replaceAll("(?i)" + Pattern.quote(highlight),
                    "<span id='highlight' style='background-color: yellow'>$0</span>");
            html += "<script> function scrollToHighlight() { window.location.hash = '#highlight';}</script>";
        }

        webView.loadDataWithBaseURL("", html, "text/html", "UTF-8", "");
    }

    protected String getWordDefinitionOffline() {
        String definition = db.getWordDefinition(word);
        if (definition == null) {
            definition = "Word not found: " + word;
        }
        return definition;
    }
}
