// Activity with a recycle view, populates with thumbnails - image is downloaded in Viewer activity.
// This does most of the connection initialization (maybe move somewhere else?)
// Copyright 2023 Daniel C - https://github.com/petabyt/fujiapp
package dev.danielc.fujiapp;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.net.ConnectivityManager;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

public class Gallery extends AppCompatActivity {
    private static Gallery instance;

    final int GRID_SIZE = 4;

    public static Gallery getInstance() {
        return instance;
    }

    private RecyclerView recyclerView;
    private ImageAdapter imageAdapter;

    Handler handler;

    public void setErrorText(String arg) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                TextView error_msg = findViewById(R.id.gallery_logs);
                error_msg.setText(arg);
            }
        });
    }

    void showWarning(String text) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                TextView warn_msg = findViewById(R.id.bottomDialogText);
                warn_msg.setText(text);

                View b = findViewById(R.id.bottomDialog);
                b.setVisibility(View.VISIBLE);
            }
        });

        // Give time for the warning to show and warn the user (in case it breaks)
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            return;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        instance = this;

        ConnectivityManager m = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);

        recyclerView = findViewById(R.id.galleryView);
        recyclerView.setLayoutManager(new GridLayoutManager(this, GRID_SIZE));

        handler = new Handler(Looper.getMainLooper());

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                int rc;
                if (Backend.cPtpFujiInit() == 0) {
                    Backend.print("Initialized connection.");
                } else {
                    Backend.reportError(Backend.PTP_IO_ERR, "Failed to init socket");
                    return;
                }

                // Fuji cameras require delay after init
                try {
                    Thread.sleep(500);
                } catch (Exception e) {
                    return;
                }

                try {
                    Camera.openSession();
                } catch (Exception e) {
                    Backend.reportError(Backend.PTP_IO_ERR, "Failed to open session.");
                    return;
                }

                Backend.print("Waiting for device access...");
                if (Backend.cPtpFujiWaitUnlocked() == 0) {
                    Backend.print("Gained access to device.");
                } else {
                    Backend.reportError(Backend.PTP_IO_ERR, "Failed to gain access to device.");
                    return;
                }

                // Camera mode must be set before anything else
                if (Backend.cFujiConfigInitMode() != 0) {
                    Backend.reportError(Backend.PTP_IO_ERR, "Failed to configure mode with the camera.");
                    return;
                }

                if (Backend.cIsMultipleMode()) {
                    showWarning("Multiple/single import is unsupported, don't expect it to work.");
                } else if (Backend.cIsUntestedMode()) {
                    showWarning("This camera is untested, support is under development.");
                }

                Backend.print("Configuring versions and stuff..");
                rc = Backend.cFujiConfigVersion();
                if (rc != 0) {
                    Backend.reportError(rc, "Failed to configure camera versions.");
                    return;
                }

                // Enter and 'exit' remote mode
                if (Backend.cCameraWantsRemote()) {
                    Backend.print("Entering remote mode..");
                    rc = Backend.cFujiTestStartRemoteSockets();
                    if (rc != 0) {
                        Backend.reportError(rc, "Failed to init remote mode");
                        return;
                    }

                    if (Backend.wifi.fujiConnectEventAndVideo(m)) {
                        Backend.reportError(Backend.PTP_RUNTIME_ERR, "Failed to enter remote mode");
                        return;
                    }

                    rc = Backend.cFujiEndRemoteMode();
                    if (rc != 0) {
                        Backend.reportError(rc, "Failed to exit remote mode");
                        return;
                    }
                }

                Backend.print("Entering image gallery..");
                rc = Backend.cFujiConfigImageGallery();
                if (rc != 0) {
                    Backend.reportError(rc, "Failed to start image gallery");
                    return;
                }

                int[] objectHandles = Backend.cGetObjectHandles();

                if (objectHandles == null) {
                    Backend.print("No JPEG images available.");
                    Backend.print("Maybe you only have RAW files? Fuji doesn't let us view RAW over WiFi :(");
                } else {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            imageAdapter = new ImageAdapter(Gallery.this, objectHandles);
                            recyclerView.setAdapter(imageAdapter);
                            recyclerView.setItemViewCacheSize(50);
                            recyclerView.setNestedScrollingEnabled(false);
                        }
                    });
                }

                // After init, use this thread to ping the camera for events
                while (true) {
                    if (Backend.cPtpFujiPing() == 0) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            return;
                        }
                    } else {
                        handler.post(new Runnable() {
                        @Override
                            public void run() {
                                // Do nothing if connection doesn't exist anymore
                                if (Backend.wifi.killSwitch) return;

                                Intent intent = new Intent(Gallery.this, MainActivity.class);
                                startActivity(intent);
                                Backend.reportError(Backend.PTP_IO_ERR, "Failed to ping camera, disconnected");
                            }
                        });
                        return;
                    }
                }
            }
        });
        thread.start();
    }

    // When back pressed in gallery, do nothing
    @Override
    public void onBackPressed() {
        // TODO: Press again to terminate connection
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Backend.reportError(Backend.PTP_OK, "Graceful disconnect");
                finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

