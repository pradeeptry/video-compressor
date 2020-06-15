package com.youtopin.videoconverter;
/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.util.Log;
import android.view.Surface;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;


class OutputSurface implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "OutputSurface";
    private static final boolean VERBOSE = false;
    private static final int EGL_OPENGL_ES2_BIT = 4;
    private EGL10 mEGL;
    private EGLDisplay mEGLDisplay;
    private EGLContext mEGLContext;
    private EGLSurface mEGLSurface;
    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    private Object mFrameSyncObject = new Object();     // guards mFrameAvailable
    private boolean mFrameAvailable;
    private TextureRenderer mTextureRender;

    public OutputSurface(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException();
        }
        eglSetup(width, height);
        makeCurrent();
        setup();
    }

    public OutputSurface() {
        setup();
    }

    private void setup() {
        mTextureRender = new TextureRenderer();
        mTextureRender.surfaceCreated();

        if (VERBOSE) Log.d(TAG, "textureID=" + mTextureRender.getTextureId());
        mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());
        mSurfaceTexture.setOnFrameAvailableListener(this);
        mSurface = new Surface(mSurfaceTexture);
    }


    private void eglSetup(int width, int height) {
        mEGL = (EGL10)EGLContext.getEGL();
        mEGLDisplay = mEGL.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (!mEGL.eglInitialize(mEGLDisplay, null)) {
            throw new RuntimeException("unable to initialize EGL10");
        }

        int[] attribList = {
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL10.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!mEGL.eglChooseConfig(mEGLDisplay, attribList, configs, 1, numConfigs)) {
            throw new RuntimeException("unable to find RGB888+pbuffer EGL config");
        }
        int[] attrib_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL10.EGL_NONE
        };
        mEGLContext = mEGL.eglCreateContext(mEGLDisplay, configs[0], EGL10.EGL_NO_CONTEXT,
                attrib_list);
        checkEglError("eglCreateContext");
        if (mEGLContext == null) {
            throw new RuntimeException("null context");
        }

        int[] surfaceAttribs = {
                EGL10.EGL_WIDTH, width,
                EGL10.EGL_HEIGHT, height,
                EGL10.EGL_NONE
        };
        mEGLSurface = mEGL.eglCreatePbufferSurface(mEGLDisplay, configs[0], surfaceAttribs);
        checkEglError("eglCreatePbufferSurface");
        if (mEGLSurface == null) {
            throw new RuntimeException("surface was null");
        }
    }


    public void release() {
        if (mEGL != null) {
            if (mEGL.eglGetCurrentContext().equals(mEGLContext)) {
                mEGL.eglMakeCurrent(mEGLDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
                        EGL10.EGL_NO_CONTEXT);
            }
            mEGL.eglDestroySurface(mEGLDisplay, mEGLSurface);
            mEGL.eglDestroyContext(mEGLDisplay, mEGLContext);
        }
        mSurface.release();
        mEGLDisplay = null;
        mEGLContext = null;
        mEGLSurface = null;
        mEGL = null;
        mTextureRender = null;
        mSurface = null;
        mSurfaceTexture = null;
    }


    public void makeCurrent() {
        if (mEGL == null) {
            throw new RuntimeException("not configured for makeCurrent");
        }
        checkEglError("before makeCurrent");
        if (!mEGL.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }


    public Surface getSurface() {
        return mSurface;
    }


    public void changeFragmentShader(String fragmentShader) {
        mTextureRender.changeFragmentShader(fragmentShader);
    }


    public void awaitNewImage() {
        final int TIMEOUT_MS = 2500;
        synchronized (mFrameSyncObject) {
            while (!mFrameAvailable) {
                try {
                    mFrameSyncObject.wait(TIMEOUT_MS);
                    if (!mFrameAvailable) {
                        throw new RuntimeException("Surface frame wait timed out");
                    }
                } catch (InterruptedException ie) {
                    throw new RuntimeException(ie);
                }
            }
            mFrameAvailable = false;
        }
        mTextureRender.checkGlError("before updateTexImage");
        mSurfaceTexture.updateTexImage();
    }


    public void drawImage() {
        mTextureRender.drawFrame(mSurfaceTexture);
    }
    @Override
    public void onFrameAvailable(SurfaceTexture st) {
        if (VERBOSE) Log.d(TAG, "new frame available");
        synchronized (mFrameSyncObject) {
            if (mFrameAvailable) {
                throw new RuntimeException("mFrameAvailable already set, frame could be dropped");
            }
            mFrameAvailable = true;
            mFrameSyncObject.notifyAll();
        }
    }


    private void checkEglError(String msg) {
        boolean failed = false;
        int error;
        while ((error = mEGL.eglGetError()) != EGL10.EGL_SUCCESS) {
            Log.e(TAG, msg + ": EGL error: 0x" + Integer.toHexString(error));
            failed = true;
        }
        if (failed) {
            throw new RuntimeException("EGL error encountered (see log)");
        }
    }
}