package dev.danielc.fujiapp;

import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.Arrays;

import camlib.Camlib;
import camlib.WiFiComm;

public class Frontend {
    public static String parseErr(int rc) {
        switch (rc) {
            case Camlib.PTP_NO_DEVICE: return "No device found.";
            case Camlib.PTP_NO_PERM: return "Invalid permissions.";
            case Camlib.PTP_OPEN_FAIL: return "Couldn't connect to device.";
            case WiFiComm.NOT_AVAILABLE: return "WiFi not ready yet.";
            case WiFiComm.NOT_CONNECTED: return "WiFi is not connected. Wait a few seconds or check your settings.";
            case WiFiComm.UNSUPPORTED_SDK: return "Unsupported SDK";
            default: return "Unknown error";
        }
    }

    public static void discoveryIsActive() {
        MainActivity.instance.handler.post(new Runnable() {
            @Override
            public void run() {
                MainActivity.instance.findViewById(R.id.discoveryProgressBar).setVisibility(View.VISIBLE);
                TextView tv = MainActivity.instance.findViewById(R.id.discoveryMessage);
                tv.setVisibility(View.GONE);
            }
        });
    }

    public static void discoveryFailed() {
        MainActivity.instance.handler.post(new Runnable() {
            @Override
            public void run() {
                MainActivity.instance.findViewById(R.id.discoveryProgressBar).setVisibility(View.GONE);
                TextView tv = MainActivity.instance.findViewById(R.id.discoveryMessage);
                tv.setVisibility(View.VISIBLE);
                tv.setText("discovery failed...");
            }
        });
    }

    public static void discoveryWaitWifi() {
        MainActivity.instance.handler.post(new Runnable() {
            @Override
            public void run() {
                MainActivity.instance.findViewById(R.id.discoveryProgressBar).setVisibility(View.GONE);
                TextView tv = MainActivity.instance.findViewById(R.id.discoveryMessage);
                tv.setVisibility(View.VISIBLE);
                tv.setText("waiting on wifi...");
            }
        });
    }

    final static int MAX_LOG_LINES = 3;

    // debug function for both Java frontend and JNI backend
    private static String basicLog = "";

    public static String getString(int res) {
        return MainActivity.instance.getString(res);
    }

    public static void clearPrint() {
        basicLog = "";
        updateLog();
    }

    public static void print(String arg) {
        Log.d("fudge", arg);

        basicLog += arg + "\n";

        String[] lines = basicLog.split("\n");
        if (lines.length > MAX_LOG_LINES) {
            basicLog = String.join("\n", Arrays.copyOfRange(lines, 1, lines.length)) + "\n";
        }

        updateLog();
    }

    public static void print(int resID) {
        print(getString(resID));
    }

    public static void updateLog() {
        if (MainActivity.instance != null) {
            MainActivity.instance.setLogText(basicLog.trim());
        }
        if (Gallery.instance != null) {
            Gallery.instance.setLogText(basicLog.trim());
        }
    }

    public static void sendCamName(String value) {
        if (Gallery.instance == null) return;
        Gallery.instance.setTitleCamName(value);
    }
}