package chin.com.frdict;

import java.util.List;

import android.app.Service;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;
import chin.com.frdict.activity.DictionaryActivity;
import chin.com.frdict.database.BaseDictionarySqliteDatabase;
import chin.com.frdict.database.OxfordHachetteSqliteDatabase;
import chin.com.frdict.database.WiktionarySqliteDatabase;

public class ChatHeadService extends Service {
    public static WindowManager windowManager;
    public RelativeLayout chatheadView, removeView;
    public ImageView removeImg;
    public Point szWindow = new Point();
    public static ChatHeadService instance;
    ClipboardManager clipMan;
    static boolean hasClipChangedListener = false;

    // dictionaries
    public static BaseDictionarySqliteDatabase wiktionaryDb;
    public static BaseDictionarySqliteDatabase oxfordHachetteDb;

    // word list
    public static AccentInsensitiveFilterArrayAdapter adapter;

    public static final String INTENT_FROM_CLIPBOARD = "FromClipboard";

    /**
     * Event handler for looking up the word that was just copied into the clipboard
     */
    ClipboardManager.OnPrimaryClipChangedListener primaryClipChangedListener = new ClipboardManager.OnPrimaryClipChangedListener() {
        @Override
        public void onPrimaryClipChanged() {
            String str = (String) clipMan.getText();
            if (str != null && str.trim().length() > 0) {
                str = str.trim();
                if (str.contains("s'") || str.contains("s’")) {
                	str = str.replace("s'", "").replace("s’", "");
                }
                // execute SearchWordAsyncTask ourselves, or let MyDialog do it, depending whether it is active or not
                if (!DictionaryActivity.active) {
                    Intent intent = new Intent(ChatHeadService.this, DictionaryActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.putExtra(INTENT_FROM_CLIPBOARD, str);
                    startActivity(intent);
                }
                else {
                    ChatHeadService.searchWord(str);
                    DictionaryActivity.instance.edt.setText(str);
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(Utility.LogTag, "ChatHeadService.onCreate()");
        instance = this;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        ChatHeadService.wiktionaryDb = WiktionarySqliteDatabase.getInstance(this);
        ChatHeadService.oxfordHachetteDb = OxfordHachetteSqliteDatabase.getInstance(this);

        if (adapter == null) {
            Toast.makeText(ChatHeadService.this, "AutoCompleteTextView is initializing", Toast.LENGTH_SHORT).show();
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    Log.i("frdict", "Start getting word list");
                    List<String> wordList = wiktionaryDb.getWordList();
                    Log.i("frdict", "End getting word list, start creating adapter");

                    long start = System.currentTimeMillis();
                    List<String> accentRemovedList = ChatHeadService.wiktionaryDb.getNoAccentWordList();
                    long end = System.currentTimeMillis();
                    long duration = (end - start);
                    Log.i("frdict", "AccentInsensitiveFilterArrayAdapter - creating accentRemovedList time: " + duration + "ms");

                    adapter = new AccentInsensitiveFilterArrayAdapter(ChatHeadService.this, R.layout.autocomplete_dropdown_item, wordList, accentRemovedList);
                    Log.i("frdict", "End creating adapter");
                    return null;
                }

                @Override
                protected void onPostExecute(Void param) {
                    Toast.makeText(ChatHeadService.this, "AutoCompleteTextView is now ready", Toast.LENGTH_SHORT).show();
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }

        // the remove view
        removeView = (RelativeLayout) inflater.inflate(R.layout.remove, null);
        WindowManager.LayoutParams paramRemove = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        paramRemove.gravity = Gravity.TOP | Gravity.START;
        removeView.setVisibility(View.GONE);
        removeImg = (ImageView) removeView.findViewById(R.id.remove_img);
        windowManager.addView(removeView, paramRemove);

        // chathead
        chatheadView = (RelativeLayout) inflater.inflate(R.layout.chathead, null);
        windowManager.getDefaultDisplay().getSize(szWindow);
        WindowManager.LayoutParams chatheadParams = new WindowManager.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        chatheadParams.gravity = Gravity.TOP | Gravity.START;
        chatheadParams.x = 0;
        chatheadParams.y = 100;
        windowManager.addView(chatheadView, chatheadParams);

        chatheadView.setOnTouchListener(new ChatheadOnTouchListener(this));

        // automatically search word when copy to clipboard
        clipMan = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        regPrimaryClipChanged();
    }

    public static void searchWord(String word) {
        new SearchWordAsyncTask(ChatHeadService.instance, DictionaryActivity.webViewWiktionary, ChatHeadService.wiktionaryDb, word).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        new SearchWordAsyncTask(ChatHeadService.instance, DictionaryActivity.webViewOxfordHachette, ChatHeadService.oxfordHachetteDb, word).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        windowManager.getDefaultDisplay().getSize(szWindow);
        WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) chatheadView.getLayoutParams();

        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.d(Utility.LogTag, "ChatHeadService.onConfigurationChanged -> landscap");

            if (layoutParams.y + (chatheadView.getHeight() + getStatusBarHeight()) > szWindow.y) {
                layoutParams.y = szWindow.y - (chatheadView.getHeight() + getStatusBarHeight());
                windowManager.updateViewLayout(chatheadView, layoutParams);
            }

            if (layoutParams.x != 0 && layoutParams.x < szWindow.x) {
                resetPosition(szWindow.x);
            }

        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.d(Utility.LogTag, "ChatHeadService.onConfigurationChanged -> portrait");

            if (layoutParams.x > szWindow.x) {
                resetPosition(szWindow.x);
            }
        }
    }

    public void resetPosition(int x_cord_now) {
        int w = chatheadView.getWidth();

        if (x_cord_now == 0 || x_cord_now == szWindow.x - w) {

        } else if (x_cord_now + w / 2 <= szWindow.x / 2) {
            moveToLeft(x_cord_now);
        } else if (x_cord_now + w / 2 > szWindow.x / 2) {
            moveToRight(x_cord_now);
        }
    }

    public void moveToLeft(int x_cord_now) {
        final int x = x_cord_now;
        new CountDownTimer(500, 5) {
            WindowManager.LayoutParams mParams = (WindowManager.LayoutParams) chatheadView.getLayoutParams();

            @Override
            public void onTick(long t) {
                long step = (500 - t) / 5;
                mParams.x = (int) (double) bounceValue(step, x);
                windowManager.updateViewLayout(chatheadView, mParams);
            }

            @Override
            public void onFinish() {
                mParams.x = 0;
                windowManager.updateViewLayout(chatheadView, mParams);
            }
        }.start();
    }

    public void moveToRight(int x_cord_now) {
        final int x = x_cord_now;
        new CountDownTimer(500, 5) {
            WindowManager.LayoutParams mParams = (WindowManager.LayoutParams) chatheadView.getLayoutParams();

            @Override
            public void onTick(long t) {
                long step = (500 - t) / 5;
                mParams.x = szWindow.x + (int) (double) bounceValue(step, x) - chatheadView.getWidth();
                windowManager.updateViewLayout(chatheadView, mParams);
            }

            @Override
            public void onFinish() {
                mParams.x = szWindow.x - chatheadView.getWidth();
                windowManager.updateViewLayout(chatheadView, mParams);
            }
        }.start();
    }

    private double bounceValue(long step, long scale) {
        double value = scale * java.lang.Math.exp(-0.055 * step) * java.lang.Math.cos(0.08 * step);
        return value;
    }

    public int getStatusBarHeight() {
        int statusBarHeight = (int) Math.ceil(25 * getApplicationContext().getResources().getDisplayMetrics().density);
        return statusBarHeight;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(Utility.LogTag, "ChatHeadService.onStartCommand()");
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    /**
     * Register the clipboard event handler
     */
    private void regPrimaryClipChanged() {
        if (!hasClipChangedListener) {
            clipMan.addPrimaryClipChangedListener(primaryClipChangedListener);
            hasClipChangedListener = true;
        }
    }

    /**
     * Unregister the clipboard event handler
     */
    private void unRegPrimaryClipChanged() {
        if (hasClipChangedListener) {
            clipMan.removePrimaryClipChangedListener(primaryClipChangedListener);
            hasClipChangedListener = false;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(Utility.LogTag, "ChatHeadService.onDestroy()");
        if (chatheadView != null) {
            windowManager.removeView(chatheadView);
        }

        if (removeView != null) {
            windowManager.removeView(removeView);
        }

        unRegPrimaryClipChanged();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(Utility.LogTag, "ChatHeadService.onBind()");
        return null;
    }
}