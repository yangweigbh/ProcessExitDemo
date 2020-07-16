package com.github.yangweigbh.processexitdemo;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String PREV_DETECT_TIME_KEY = "PREV_DETECT_TIME_KEY";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.button_crash).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                throw new RuntimeException();
            }
        });

        findViewById(R.id.button_anr).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                }
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            List<ApplicationExitInfo> applicationExitInfos = am.getHistoricalProcessExitReasons(null, 0, 0);

            if (applicationExitInfos == null || applicationExitInfos.size() == 0) return;
            long lastExitTimestamp = getPreferences(0).getLong(PREV_DETECT_TIME_KEY, 0);

            List<ApplicationExitInfo> unprocessedExitInfos = new ArrayList<>();
            for (ApplicationExitInfo exitInfo: applicationExitInfos) {
                if (exitInfo.getTimestamp() > lastExitTimestamp) {
                    unprocessedExitInfos.add(exitInfo);
                } else {
                    break;
                }
            }

            if (unprocessedExitInfos.size() > 0) {
                getPreferences(0).edit().putLong(PREV_DETECT_TIME_KEY, unprocessedExitInfos.get(0).getTimestamp()).apply();
            }

            for (ApplicationExitInfo exitInfo: unprocessedExitInfos) {
                Log.e(TAG, String.format("Exit reason: %d, description: %s", exitInfo.getReason(), exitInfo.getDescription()));

                if (exitInfo.getReason() == ApplicationExitInfo.REASON_ANR) {
                    UUID uuid = UUID.randomUUID();
                    String fileName = "anr_info_" + uuid.toString() + ".trace";
                    File outFile = new File(getFilesDir().getAbsolutePath() + "/" + fileName);

                    try (InputStream inputStream = exitInfo.getTraceInputStream()) {
                        copyStreamToFile(inputStream, outFile);
                    } catch (IOException e) {
                        Log.e(TAG, "copyStreamToFile: ", e);
                    }

                    // upload the file to server
                }
            }
        }
    }

    public static void copyStreamToFile(InputStream is, File outFile) throws IOException {
        if (is == null || outFile == null) return;

        File tmpOutputFile = new File(outFile.getPath() + ".tmp");
        try (OutputStream os = new FileOutputStream(tmpOutputFile)) {
            copyStream(is, os);
        }
        if (!tmpOutputFile.renameTo(outFile)) {
            throw new IOException();
        }
    }

    public static void copyStream(InputStream inputStream, OutputStream outputStream)
            throws IOException {
        byte[] buffer = new byte[8192];
        int amountRead;
        while ((amountRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, amountRead);
        }
    }
}