package org.renpy.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;
import android.util.Log;
import android.util.DisplayMetrics;
import android.os.Debug;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.File;
import java.io.IOException;

import java.util.zip.GZIPInputStream;

public class PythonActivity extends Activity implements Runnable {

    // The SDLSurfaceView we contain.
    private SDLSurfaceView mView = null;    

    // Did we launch our thread?
    private boolean mLaunchedThread = false;

    private ResourceManager resourceManager;

    // The path to the directory contaning our external storage.
    public File externalStorage;
    
    // The path to the directory containing the game.
    private File mPath = null;
    
    private String gamepath;
    private String savepath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Hardware.context = this;

        getWindowManager().getDefaultDisplay().getMetrics(Hardware.metrics);
        
        resourceManager = new ResourceManager(this);
        externalStorage = new File(Environment.getExternalStorageDirectory(), getPackageName());

        if (resourceManager.getString("public_version") != null) {
            mPath = externalStorage;           
        } else {
            mPath = getFilesDir();
        }
        
        Intent intent = getIntent();
        gamepath = intent.getStringExtra("renpypath");
        savepath =intent.getStringExtra("renpysave");

        // go to fullscreen mode
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        // Start showing an SDLSurfaceView.
        mView = new SDLSurfaceView(this, mPath.getAbsolutePath(),gamepath,savepath);

        Hardware.view = mView;
        setContentView(mView);                
    }

    /**
     * Show an error using a toast. (Only makes sense from non-UI
     * threads.)
     */
    public void toastError(final String msg) {

        final Activity thisActivity = this;

        runOnUiThread(new Runnable () {
                public void run() {
                    Toast.makeText(thisActivity, msg, Toast.LENGTH_LONG).show();
                }
            });
                
        // Wait to show the error.
        synchronized (this) {
            try {
                this.wait(1000);
            } catch (InterruptedException e) {                        
            }
        }
    }

    /**
     * This determines if unpacking one the zip files included in
     * the .apk is necessary. If it is, the zip file is unpacked.
     */
    public void unpackData(final String resource, File target) {

        // The version of data in memory and on disk.
        String data_version = resourceManager.getString(resource + "_version");
        String disk_version = null;
        
        // If no version, no unpacking is necessary.
        if (data_version == null) {
            return;
        }

        // Check the current disk version, if any.
        String filesDir = target.getAbsolutePath();
        String disk_version_fn = filesDir + "/" + resource + ".version";
        
        try {
            byte buf[] = new byte[64];            
            InputStream is = new FileInputStream(disk_version_fn);
            int len = is.read(buf);
            disk_version = new String(buf, 0, len);              
            is.close();
        } catch (Exception e) {
            disk_version = "";
        }

        // If the disk data is out of date, extract it and write the
        // version file.
        if (! data_version.equals(disk_version)) {        
            Log.v("python", "Extracting " + resource + " assets.");

            target.mkdirs();
            
            AssetExtract ae = new AssetExtract(this);            
            if (!ae.extractTar(resource + ".mp3", target.getAbsolutePath())) {                                
                toastError("Could not extract " + resource + " data.");
            }

            try {
                // Write .nomedia.
                new File(target, ".nomedia").createNewFile();

                // Write version file.
                FileOutputStream os = new FileOutputStream(disk_version_fn);
                os.write(data_version.getBytes());
                os.close();                
            } catch (Exception e) {
                Log.w("python", e);
            }
        }

    }
    
    public void run() {

    	 unpackData("private", getFilesDir());
         unpackData("public", externalStorage);
         
         System.loadLibrary("sdl");
         System.loadLibrary("sdl_image");
         System.loadLibrary("sdl_ttf");
         System.loadLibrary("python2.7");
         System.loadLibrary("application");
         System.loadLibrary("sdl_main");

         System.load(getFilesDir() + "/_io.so");
         System.load(getFilesDir() + "/unicodedata.so");
         
         try {
             System.loadLibrary("sqlite3");
             System.load(getFilesDir() + "/_sqlite3.so");
         } catch(UnsatisfiedLinkError e) {
         }

         try {
             System.load(getFilesDir() + "/_imaging.so");
             System.load(getFilesDir() + "/_imagingft.so");
             System.load(getFilesDir() + "/_imagingmath.so");
         } catch(UnsatisfiedLinkError e) {
         }

        runOnUiThread(new Runnable () {
                public void run() {
                    mView.start();
                }
            });
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mView != null) {
            mView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mLaunchedThread) {
            mLaunchedThread = true;
            new Thread(this).start();
        }
        
        if (mView != null) {
            mView.onResume();
        }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, final KeyEvent event) {
        if (mView != null && mView.mStarted && SDLSurfaceView.nativeKey(keyCode, 1)) {
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, final KeyEvent event) {
        if (mView != null && mView.mStarted && SDLSurfaceView.nativeKey(keyCode, 0)) {
            return true;
        } else {
            return super.onKeyUp(keyCode, event);
        }
    }

    @Override
    public boolean dispatchTouchEvent(final MotionEvent ev) {

        if (mView != null){
            mView.onTouchEvent(ev);
            return true;
        } else {            
            return super.dispatchTouchEvent(ev);
        }
    }

    // Ensure we only have one task. 
    public void onDestroy() {
        System.exit(0);
    }

}
