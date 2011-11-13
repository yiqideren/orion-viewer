package com.google.code.orion_viewer;

/*Orion Viewer is a pdf viewer for Nook Classic based on mupdf

Copyright (C) 2011  Michael Bogdanov

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.*/

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.google.code.orion_viewer.device.NookDevice;
import com.google.code.orion_viewer.pdf.PdfDocument;

import java.io.*;

public class OrionViewerActivity extends Activity {

    private static String LOGTAG = "com.google.code.orion_viewer.OrionViewerActivity";

    private static final int MAIN_SCREEN = 0;

    private static final int PAGE_SCREEN = 1;

    private static final int ZOOM_SCREEN = 2;

    private static final int CROP_SCREEN = 3;

    private static final int HELP_SCREEN = 4;

    private static final int FILE_SCREEN = 5;

    private OrionView view;

    private ViewAnimator animator;

    private SeekBar pageSeek;

    private SeekBar zoomSeek;

    private TextView pageNumberText;

    private  TextView zoomText;
    // TextView m_name;

    private Handler m_Handler = new Handler();

    private LastPageInfo pageInfo;

    //left, right, top, bottom
    private int [] cropBorders;

    private Controller controller;

    private Device device = new NookDevice(this);

    private OperationHolder operation = new OperationHolder();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);

        device.onCreate(this);

        setContentView(R.layout.main);

        // Check the intent for the content to view
        Intent intent = getIntent();

        view = (OrionView) findViewById(R.id.view);

        animator = (ViewAnimator) findViewById(R.id.viewanim);

        ListView view = (ListView) findViewById(R.id.file_chooser);
        view.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                File file = (File) parent.getItemAtPosition(position);
                if (file.isDirectory()) {
                    ((FileChooser) parent.getAdapter()).changeFolder(file);
                } else {
                    openFile(file.getAbsolutePath());
                }
            }
        });

        view = (ListView) findViewById(R.id.file_chooser);
        view.setAdapter(new FileChooser(this, Environment.getRootDirectory()));

        onNewIntent(intent);

        initButtons();
//        initListener();

        cropBorders = new int[4];

        //page chooser
        pageSeek = (SeekBar) findViewById(R.id.page_picker_seeker);

        pageNumberText = (TextView) findViewById(R.id.page_picker_message);
        //initial state
        pageNumberText.setText("" + 1);

        pageSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                pageNumberText.setText("" + (progress + 1));
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        ImageButton closePagePeeker = (ImageButton) findViewById(R.id.page_picker_close);

        ImageButton plus = (ImageButton) findViewById(R.id.page_picker_plus);
        plus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pageSeek.incrementProgressBy(1);
            }
        });

        ImageButton minus = (ImageButton) findViewById(R.id.page_picker_minus);
        minus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (pageSeek.getProgress() != 0) {
                    pageSeek.incrementProgressBy(-1);
                }
            }
        });

        closePagePeeker.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                controller.drawPage(Integer.valueOf(pageNumberText.getText().toString()) - 1);
                //main menu
                animator.setDisplayedChild(MAIN_SCREEN);
            }
        });

        ImageButton page_preview = (ImageButton) findViewById(R.id.page_preview);
        page_preview.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                controller.drawPage(Integer.valueOf(pageNumberText.getText().toString()) -1);
            }
        });

        initZoomScreen();

        initCropScreen();
    }

    public void updateLabels() {
        pageSeek = (SeekBar) findViewById(R.id.page_picker_seeker);
        pageSeek.setMax(controller.getPageCount() - 1);
        zoomText = (TextView) findViewById(R.id.zoom_picker_message);
        zoomText.setText("" + controller.getZoomFactor());
    }

    protected void onNewIntent(Intent intent) {
        Log.d(Common.LOGTAG,  "Runtime.getRuntime().totalMemory() = " + Runtime.getRuntime().totalMemory());
        Log.d(Common.LOGTAG, "Debug.getNativeHeapSize() = " + Debug.getNativeHeapSize());

        if (controller != null) {
            controller.destroy();
            controller = null;
        }

        if (intent.getData() != null) {
            Uri uri = intent.getData();

            String file = uri.getPath();

            openFile(file);
        } else if (intent.getAction().endsWith("MAIN")) {
            animator.setDisplayedChild(FILE_SCREEN);
        }
    }

    public void openFile(String file) {
        PdfDocument doc = null;
        try {
            doc = new PdfDocument(file);

            new MediaScannerNotifier(file);

            LayoutStrategy str = new SimpleLayoutStrategy(doc);

            int idx = file.lastIndexOf('/');
            file = file.substring(idx + 1) + ".userData";

            controller = new Controller(doc, str, view);
            try {
                ObjectInputStream inp = new ObjectInputStream(openFileInput(file));
                pageInfo = (LastPageInfo) inp.readObject();
                inp.close();
            } catch (Exception e) {
                pageInfo = new LastPageInfo();
            }
            pageInfo.fileName = file;
            controller.init(pageInfo);
            updateLabels();
            animator.setDisplayedChild(MAIN_SCREEN);
            controller.drawPage();
        } catch (Exception e) {
            if (doc != null) {
                doc.destroy();
            }
        }
    }


    public void onPause() {
        super.onPause();
        device.onPause();
        if (controller != null) {
            controller.onPause();
            saveData();
        }
    }

    public void onUserInteraction() {
        super.onUserInteraction();
        device.onUserInteraction();
    }

    public void initZoomScreen() {
        //zoom screen
        zoomText = (TextView) findViewById(R.id.zoom_picker_message);

        zoomSeek = (SeekBar) findViewById(R.id.zoom_picker_seeker);
        zoomSeek.setMax(300);
        zoomSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                zoomText.setText(progress + "%");
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        ImageButton zplus = (ImageButton) findViewById(R.id.zoom_picker_plus);
        zplus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                zoomSeek.incrementProgressBy(1);
            }
        });

        ImageButton zminus = (ImageButton) findViewById(R.id.zoom_picker_minus);
        zminus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (zoomSeek.getProgress() != 0) {
                    zoomSeek.incrementProgressBy(-1);
                }
            }
        });

        ImageButton closeZoomPeeker = (ImageButton) findViewById(R.id.zoom_picker_close);
        closeZoomPeeker.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                controller.changeZoom(zoomSeek.getProgress());
                //main menu
                animator.setDisplayedChild(MAIN_SCREEN);
            }
        });

        ImageButton zoom_preview = (ImageButton) findViewById(R.id.zoom_preview);
        zoom_preview.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                controller.changeZoom(zoomSeek.getProgress());
            }
        });
    }


    public void initCropScreen() {
        ListView cropList = (ListView) findViewById(R.id.crop_borders);
        cropList.setAdapter(new ArrayAdapter(this, R.layout.crop, new String[] {"Left", "Right", "Top", "Bottom"}) {

            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView;
                if (v == null) {
                    LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    v = vi.inflate(R.layout.crop, null);
                }

                String cropHeader = (String) getItem(position);

                //crop header
                TextView header = (TextView) v.findViewById(R.id.crop_text);
                header.setText(cropHeader);

                TextView valueView = (TextView) v.findViewById(R.id.crop_value);

                valueView.setText("" + cropBorders[position]);

                ImageButton plus = (ImageButton) v.findViewById(R.id.crop_plus);
                ImageButton minus = (ImageButton) v.findViewById(R.id.crop_minus);
                linkCropButtonsAndText(minus, plus, (TextView) v.findViewById(R.id.crop_value), position);

                return v;
            }
        });

        ImageButton preview = (ImageButton) findViewById(R.id.crop_preview);
        preview.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                controller.changeMargins(cropBorders[0], cropBorders[2], cropBorders[1], cropBorders[3]);
            }
        });

        ImageButton close = (ImageButton) findViewById(R.id.crop_close);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //main menu
                controller.changeMargins(cropBorders[0], cropBorders[2], cropBorders[1], cropBorders[3]);
                animator.setDisplayedChild(MAIN_SCREEN);
            }
        });
    }

    public void linkCropButtonsAndText(final ImageButton minus, final ImageButton plus, final TextView text, final int cropIndex) {
        minus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //main menu
                if (cropBorders[cropIndex] != 0) {
                    cropBorders[cropIndex] = cropBorders[cropIndex] - 1;
                    text.setText("" + cropBorders[cropIndex]);
                }
            }
        });

        minus.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                cropBorders[cropIndex] = cropBorders[cropIndex] - 30;
                if (cropBorders[cropIndex] < 0) {
                    cropBorders[cropIndex] = 0;
                }
                text.setText("" + cropBorders[cropIndex]);
                return true;
            }
        });

        plus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //main menu
                //int value = Integer.valueOf(text.getText().toString());
                cropBorders[cropIndex] = cropBorders[cropIndex] + 1;
                text.setText("" + cropBorders[cropIndex]);
            }
        });

        plus.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                cropBorders[cropIndex] = cropBorders[cropIndex] + 30;
                text.setText("" + cropBorders[cropIndex]);
                return true;
            }
        });
    }

    private void initButtons() {
        ImageButton btn = (ImageButton) findViewById(R.id.exit);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                //controller.destroy();
                finish();
            }
        });
        btn = (ImageButton) findViewById(R.id.prev_page);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                changePage(-1);
            }
        });
        btn.setOnLongClickListener(new View.OnLongClickListener() {

            public boolean onLongClick(View v) {
                pageSeek.setProgress(controller.getCurrentPage());
                //page seeker
                animator.setDisplayedChild(PAGE_SCREEN);
                return true;
            }
        });

        btn = (ImageButton) findViewById(R.id.next_page);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                changePage(1);
            }
        });
        btn.setOnLongClickListener(new View.OnLongClickListener() {

            public boolean onLongClick(View v) {
                pageSeek.setProgress(controller.getCurrentPage());
                //page seeker
                animator.setDisplayedChild(PAGE_SCREEN);
                return true;
            }
        });

        btn = (ImageButton) findViewById(R.id.switch_page);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                controller.setRotation((controller.getRotation() - 1) % 2);
            }
        });
        btn.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                controller.setRotation((controller.getRotation() + 1) % 2);
                return true;
            }
        });

        btn = (ImageButton) findViewById(R.id.zoom);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                zoomSeek.setProgress(controller.getZoomFactor());
                animator.setDisplayedChild(ZOOM_SCREEN);
            }
        });

        btn = (ImageButton) findViewById(R.id.crop_menu);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                animator.setDisplayedChild(CROP_SCREEN);
            }
        });

        btn = (ImageButton) findViewById(R.id.help);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                animator.setDisplayedChild(HELP_SCREEN);
            }
        });

        btn = (ImageButton) findViewById(R.id.help);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                animator.setDisplayedChild(HELP_SCREEN);
            }
        });

        btn = (ImageButton) findViewById(R.id.help_close);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                animator.setDisplayedChild(MAIN_SCREEN);
            }
        });

    }

    protected void onResume() {
        super.onResume();
        device.onResume();
        if (controller != null) {
            Log.d(Common.LOGTAG, "on RESUME");
            controller.onStart();
            controller.drawPage();
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        Log.d(Common.LOGTAG, "on DESTROY");
        if (controller != null) {
            controller.destroy();
        }
    }

    private void saveData() {
       if (controller != null) {
            try {
                controller.serialize(pageInfo);
                ObjectOutputStream out =
                    new ObjectOutputStream(OrionViewerActivity.this.openFileOutput(pageInfo.fileName,
                        Context.MODE_PRIVATE));
                out.writeObject(pageInfo);
                out.close();
            } catch (Exception ex) {
                Log.e(LOGTAG, ex.getMessage(), ex);
            }
       }
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (device.onKeyDown(keyCode, event, operation)) {
            changePage(operation.value);
            return true;
        }
        return false;
    }

    public void changePage(int operation) {
        if (controller != null) {
            if (operation == 1 && controller.getRotation() != -1 || operation == - 1 && controller.getRotation() == -1) {
                controller.drawNext();
            } else {
                controller.drawPrev();
            }
        }
    }

    class MediaScannerNotifier implements MediaScannerConnection.MediaScannerConnectionClient {
        private MediaScannerConnection mConnection;
        private String mPath;

        public synchronized void scanFile(String path) {
            if (path == null) { return; }
            String mime = "ebook/";
            String ext = path.substring(path.lastIndexOf(".") + 1).toLowerCase();
            mime += ext;
            mConnection.scanFile(path, mime);
        }

        public MediaScannerNotifier(String path) {
            mConnection = new MediaScannerConnection(OrionViewerActivity.this, this);
            mConnection.connect();
            mPath = path;
        }

        public void onMediaScannerConnected() {
            scanFile(mPath);
        }

        public void onScanCompleted(String path, Uri arg1) {
            if (path.equals(mPath)) {
                Log.d(Common.LOGTAG, "On Scan completed" + path + "  " + arg1);
                String[] columns = {
                    "title", "authors"
                };
                final Cursor dbCursor = getContentResolver().query(arg1, columns, null, null, null);
                dbCursor.moveToFirst();
                m_Handler.post(new Runnable() {
                    public void run() {
                        device.updateTitle(dbCursor.getString(0));
                        dbCursor.close();
                    }
                });
                mConnection.disconnect();
            }
        }
    }

}