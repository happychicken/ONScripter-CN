/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// This string is autogenerated by ChangeAppSettings.sh, do not change spaces amount
package org.renpy.android;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.opengl.GLUtils;
import android.os.PowerManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import cn.natdon.onscripterv2.Globals;

public class SDLSurfaceView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    // The activity we're a part of.
    private static Activity mActivity;

    // Have we started yet?
    public boolean mStarted = false;

    // Is Python ready to receive input events?
    static boolean mInputActivated = false;
    
    // The number of swaps we should skip. Experimentally derived from
    // watching SDL initialize.
    private int mSwapSkips = 2;

    // The number of times we should clear the screen after swap.
    private int mClears = 2;
    
    // Has the display been changed?
    private boolean mChanged = false;

    // Are we running yet?
    private boolean mRunning = false;
        
    // The EGL used by our thread.
    private EGL10 mEgl = null;

    // The EGL Display used.
    private EGLDisplay mEglDisplay = null;
    
    // The EGL Context used.
    private EGLContext mEglContext = null;

    // The EGL Surface used.
    private EGLSurface mEglSurface = null;

    // The EGL Config used.
    private EGLConfig mEglConfig = null;

    // The user program is not participating in the pause protocol.
    public final int PAUSE_NOT_PARTICIPATING = 0;
    
    // A pause has not been requested by the OS.
    public final int PAUSE_NONE = 1;

    // A pause has been requested by Android, but the user program has
    // not bothered responding yet.
    public final int PAUSE_REQUEST = 2;

    // The user program is waiting in waitForResume.
    public final int PAUSE_WAIT_FOR_RESUME = 3;

    // This stores the state of the pause system.
    private int mPause = PAUSE_NOT_PARTICIPATING;

    private PowerManager.WakeLock wakeLock;  
    
    // The width and height. (This should be set at startup time -
    // these values just prevent segfaults and divide by zero, etc.)
    int mWidth = 100;
    int mHeight = 100;        

    // The name of the directory where the context stores its files.
    String mFilesDirectory = null;

    // The value of the argument passed in.
    String mArgument = null;

    // The resource manager we use.
    ResourceManager mResourceManager;
    
    String mpath = null;
    String savePath = null;
    
    public SDLSurfaceView(Activity act, String argument,String gamePath ,String spath) {
        super(act);

        mActivity = act;
        mResourceManager = new ResourceManager(act);
        mpath=gamePath;
        savePath=spath;
        
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        holder.setType(SurfaceHolder.SURFACE_TYPE_GPU);

        mFilesDirectory = mActivity.getFilesDir().getAbsolutePath();
        mArgument = argument;

        PowerManager pm = (PowerManager) act.getSystemService(Context.POWER_SERVICE);  
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "Screen On");          
    }


    /**
     * The user program should call this frequently to check if a
     * pause has been requested by android. If this ever returns
     * true, the user program should clean up and call waitForResume.
     */
    public int checkPause() {
        if (mPause == PAUSE_NOT_PARTICIPATING) {
            mPause = PAUSE_NONE;
        }

        if (mPause == PAUSE_REQUEST) {
            return 1;
        } else {
            return 0;
        }
    }
    

    /**
     * The user program should call this quickly after checkPause
     * returns true. This causes the android application to sleep,
     * waiting for resume. While sleeping, it should not have any
     * activity. (Notably, it should stop all timers.)
     *
     * While we're waiting in this method, android is allowed to
     * kill us to reclaim memory, without any further warning.
     */     
    public void waitForResume() {
        synchronized (this) {
            mPause = PAUSE_WAIT_FOR_RESUME;

            // Notify any threads waiting in onPause.
            this.notifyAll();

            while (mPause == PAUSE_WAIT_FOR_RESUME) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }
    
    /**
     * Inform the view that the activity is paused. The owner of this view must
     * call this method when the activity is paused. Calling this method will
     * pause the rendering thread.
     * Must not be called before a renderer has been set.
     */
    public void onPause() {

        synchronized (this) {        
            if (mPause == PAUSE_NONE) {
                mPause = PAUSE_REQUEST;

                while (mPause == PAUSE_REQUEST) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        // pass
                    }
                }
            }
        }

        wakeLock.release();
        
    }
    
    /**
     * Inform the view that the activity is resumed. The owner of this view must
     * call this method when the activity is resumed. Calling this method will
     * recreate the OpenGL display and resume the rendering
     * thread.
     * Must not be called before a renderer has been set.
     */
    public void onResume() {
        synchronized (this) {        
            if (mPause == PAUSE_WAIT_FOR_RESUME) {
                mPause = PAUSE_NONE;
                this.notifyAll(); 
            }
        }

        wakeLock.acquire();
    }

    /**
     * This method is part of the SurfaceHolder.Callback interface, and is
     * not normally called or subclassed by clients of GLSurfaceView.
     */
    public void surfaceCreated(SurfaceHolder holder) {
    }

    /**
     * This method is part of the SurfaceHolder.Callback interface, and is
     * not normally called or subclassed by clients of GLSurfaceView.
     */
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    /**
     * This method is part of the SurfaceHolder.Callback interface, and is
     * not normally called or subclassed by clients of GLSurfaceView.
     */
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        mWidth = w;
        mHeight = h;

        if (mActivity.getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE &&
            mWidth < mHeight) {
            return;
        }

        if (mActivity.getRequestedOrientation() == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT &&
            mWidth > mHeight) {
            return;
        }

        if (!mRunning) {
            mRunning = true;       
            new Thread(this).start();
        } else {
            mChanged = true;
        }
    }


    public void run() {
        mEgl = (EGL10) EGLContext.getEGL();

        mEglDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

        int[] version = new int[2];
        mEgl.eglInitialize(mEglDisplay, version);

        // Pick an appropriate config. We just take the first config
        // the system offers to us, because anything more complicated
        // than that stands a really good chance of not working.
        int[] configSpec = {
            // RENDERABLE_TYPE = OpenGL ES is the default.
            EGL10.EGL_NONE
        };

        EGLConfig[] configs = new EGLConfig[1];
        int[] num_config = new int[1];
        mEgl.eglChooseConfig(mEglDisplay, configSpec, configs, 1, num_config);

        mEglConfig = configs[0];
        mEglContext = mEgl.eglCreateContext(mEglDisplay, mEglConfig, EGL10.EGL_NO_CONTEXT, null);

        createSurface();

        waitForStart();

        // Figure out the source path.
        String apkFilePath;
        ApplicationInfo appInfo;
        PackageManager packMgmr = mActivity.getApplication().getPackageManager();

        try {
            appInfo = packMgmr.getApplicationInfo(mActivity.getPackageName(), 0);
            apkFilePath = appInfo.sourceDir;
        } catch (NameNotFoundException e) {
            apkFilePath = "";
        }
        
        nativeResize(mWidth, mHeight);
        nativeInitJavaCallbacks();
        nativeSetEnv("ANDROID_PRIVATE", mFilesDirectory);
        nativeSetEnv("ANDROID_ARGUMENT", savePath);
        nativeSetEnv("ANDROID_APK", mpath);
        nativeSetEnv("PYTHONOPTIMIZE", "2");
        nativeSetEnv("PYTHONHOME", mFilesDirectory);
        nativeSetEnv("PYTHONPATH", mActivity.getPackageName()+"/lib");
        nativeSetMouseUsed();
        nativeInit();

        System.exit(0);
    }

    private void glCheck(GL10 gl) {
        int gle = gl.glGetError();
        if (gle != GL10.GL_NO_ERROR) {
            throw new RuntimeException("GL Error: " + gle);
        }        
    }
    
    private void waitForStart() {

        int presplashId = mResourceManager.getIdentifier("presplash", "drawable");
        InputStream is = mActivity.getResources().openRawResource(presplashId);

        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeStream(is);            
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        } finally {
            try {
                is.close();
            } catch (IOException e) { }
        }

        int textures[] = new int[1];

        GL10 gl = (GL10) mEglContext.getGL();

        gl.glGenTextures(1, textures, 0);
        int tex = textures[0];

        // The maximum dimension of the texture containing the bitmap.
        int TEXDIM = 1024;
        
        gl.glBindTexture(GL10.GL_TEXTURE_2D, tex);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);

        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        gl.glTexImage2D(GL10.GL_TEXTURE_2D, 0, GL10.GL_RGBA, TEXDIM, TEXDIM, 0, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, null);

        GLUtils.texSubImage2D(GL10.GL_TEXTURE_2D, 0, 0, 0, bitmap);
        gl.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        
        // Set up the viewport.
        gl.glViewport(0, 0, mWidth, mHeight);

        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glOrthof(0f, mWidth, mHeight, 0f, -1.0f, 1.0f);
        gl.glMatrixMode(GL10.GL_MODELVIEW);
        gl.glLoadIdentity();

        // Turn on texturing.
        gl.glEnable(GL10.GL_TEXTURE_2D);

        // Get the size of the texture, sans borders.
        float texWidth = 1.0f * (bitmap.getWidth() - 1);
        float texHeight = 1.0f * (bitmap.getHeight() - 2);

        // Figure out the physical width and height of the image.
        float factor = Math.min(mWidth / texWidth, mHeight / texHeight);
        float width = texWidth * factor;
        float height = texHeight * factor;

        // The coordinates of the sides of the image.
        float left = (mWidth - width) / 2.0f;
        float right = mWidth - left;
        float top = (mHeight - height) / 2.0f;
        float bottom = mHeight - top;

        // The coordinates of the sides of the texture.
        float texLeft = 1.0f / TEXDIM;
        float texTop = 1.0f / TEXDIM;
        float texRight = 1.0f * (bitmap.getWidth() - 1) / TEXDIM;
        float texBottom = 1.0f * (bitmap.getHeight() - 1) / TEXDIM;
        
        // The vertices.
        float verts[] = {
            0, 0,
            mWidth, 0,
            0, mHeight,
            mWidth, mHeight,
            
            left, top,
            right, top,
            left, bottom,
            right, bottom
        };

        // The texture coordinates.
        float texCoords[] = {
            0, 0,
            texLeft, 0,
            0, texTop,
            texLeft, texTop,

            texLeft, texTop,
            texRight, texTop,
            texLeft, texBottom,
            texRight, texBottom
        };

        // Create the buffers.
        ByteBuffer vbb = ByteBuffer.allocateDirect(verts.length * 4);
        vbb.order(ByteOrder.nativeOrder());
        FloatBuffer vertBuffer = vbb.asFloatBuffer();
        vertBuffer.put(verts);
        vertBuffer.position(0);

        ByteBuffer tbb = ByteBuffer.allocateDirect(texCoords.length * 4);
        tbb.order(ByteOrder.nativeOrder());
        FloatBuffer texBuffer = tbb.asFloatBuffer();
        texBuffer.put(texCoords);
        texBuffer.position(0);
        
        // Set up client state.
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glVertexPointer(2, GL10.GL_FLOAT, 0, vertBuffer);
        gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, texBuffer);

        // Draw & Flip.
        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);
        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 4, 4);
        mEgl.eglSwapBuffers(mEglDisplay, mEglSurface);

        // Wait to be notified it's okay to start Python.
        synchronized (this) {
            while (!mStarted) {

                // Draw & Flip.
                gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);
                gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 4, 4);
                mEgl.eglSwapBuffers(mEglDisplay, mEglSurface);

                try {
                    this.wait(250);
                } catch (InterruptedException e) {
                    continue;
                }           
            }
        }

        // Reset projection matrix.
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glMatrixMode(GL10.GL_MODELVIEW);
        
        // Disable client state.
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glDisableClientState(GL10.GL_TEXTURE_COORD_ARRAY);

        // Delete texture.
        gl.glDeleteTextures(1, textures, 0);
        
        
    }

    
    public void start() {
        this.setFocusableInTouchMode(true);
        this.setFocusable(true);
        this.requestFocus();       
        
        synchronized (this) {            
            mStarted = true;
            this.notify();
        }

    }
    
    public void createSurface() {        
        mChanged = false;
        
        // Destroy the old surface.
        if (mEglSurface != null) {

            /*
             * Unbind and destroy the old EGL surface, if
             * there is one.
             */
            mEgl.eglMakeCurrent(mEglDisplay, EGL10.EGL_NO_SURFACE,
                                EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            mEgl.eglDestroySurface(mEglDisplay, mEglSurface);
        }

        // Create a new surface.
        mEglSurface = mEgl.eglCreateWindowSurface(
            mEglDisplay, mEglConfig, getHolder(), null);

        // Make the new surface current.
        mEgl.eglMakeCurrent(
            mEglDisplay, mEglSurface, mEglSurface, mEglContext);

        if (mStarted) {
            nativeResize(mWidth, mHeight);
        }

    }

    public int swapBuffers() {

        // Prevent us from drawing too early, at startup.
        if (mSwapSkips-- > 0) {
            return 1;
        }
        
        // If the display has been changed, then disregard all the
        // rendering we've done to it, and make a new surface.
        //
        // Otherwise, swap the buffers.
        if (mChanged) {
            createSurface();
            mClears = 2;
            return 0;
            
        } else {

            mEgl.eglSwapBuffers(mEglDisplay, mEglSurface);

            if (mClears-- != 0) {
                GL10 gl = (GL10) mEglContext.getGL();
                gl.glClear(GL10.GL_COLOR_BUFFER_BIT);
            }
            
            return 1;
        }

    }    

    @Override
    public boolean onTouchEvent(final MotionEvent event) {

        for ( int i = 0; i < event.getPointerCount(); i++ ) {
            int action = -1;
            if ( event.getAction() == MotionEvent.ACTION_DOWN ) {
                action = 0;
            } else if ( event.getAction() == MotionEvent.ACTION_UP ) {
                action = 1;
            } else if ( event.getAction() == MotionEvent.ACTION_MOVE ) {
                action = 2;
            }

            if (action >= 0 && mInputActivated) {
                SDLSurfaceView.nativeMouse(
                    (int)event.getX(i),
                    (int)event.getY(i),
                    action,
                    event.getPointerId(i),
                    (int)(event.getPressure(i) * 1000.0),
                    (int)(event.getSize(i) * 1000.0));
            }
        }

        synchronized (this) {
            try {
                this.wait(1000 / 60);
            } catch (InterruptedException e) { }
        }
        
        return true;
    };
    
    @Override
    public boolean onKeyDown(int keyCode, final KeyEvent event) {
        if (mInputActivated && nativeKey(keyCode, 1)) {
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, final KeyEvent event) {
        if (mInputActivated && nativeKey(keyCode, 0)) {
            return true;
        } else {
            return super.onKeyUp(keyCode, event);
        }
    }

    static void activateInput() {
        mInputActivated = true;
    }

    static void openUrl(String url) {
    	Log.i("python", "Opening URL: " + url);

    	Intent i = new Intent(Intent.ACTION_VIEW);
    	i.setData(Uri.parse(url));
    	mActivity.startActivity(i);
    }
    
    public static native void nativeSetEnv(String name, String value);
    public static native void nativeInit();

    public static native void nativeMouse( int x, int y, int action, int pointerId, int pressure, int radius );
    public static native boolean nativeKey(int keyCode, int down);
    public static native void nativeSetMouseUsed();
    
    public native void nativeResize(int width, int height);
    public native void nativeInitJavaCallbacks();
    
}
