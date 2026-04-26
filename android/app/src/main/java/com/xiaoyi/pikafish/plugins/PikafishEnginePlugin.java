package com.xiaoyi.pikafish.plugins;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@CapacitorPlugin(name = "PikafishEngine")
public class PikafishEnginePlugin extends Plugin {
    private static final String TAG = "PikafishEngine";
    
    private Process engineProcess;
    private BufferedWriter engineWriter;
    private BufferedReader engineReader;
    private ExecutorService executor;
    private Thread outputThread;
    private File engineWorkDir;
    
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isReady = new AtomicBoolean(false);
    
    @Override
    public void load() {
        executor = Executors.newSingleThreadExecutor();
    }
    
    @PluginMethod
    public void init(PluginCall call) {
        if (isRunning.get()) {
            JSObject result = new JSObject();
            result.put("success", true);
            result.put("message", "Engine already running");
            call.resolve(result);
            return;
        }
        
        executor.execute(() -> {
            try {
                // 创建引擎工作目录
                engineWorkDir = new File(getContext().getFilesDir(), "engine");
                if (!engineWorkDir.exists()) {
                    engineWorkDir.mkdirs();
                }
                
                // 获取引擎文件路径
                String enginePath = getEnginePath();
                
                if (enginePath == null) {
                    throw new RuntimeException("Engine library not found");
                }
                
                Log.d(TAG, "Engine path: " + enginePath);
                Log.d(TAG, "Work dir: " + engineWorkDir.getAbsolutePath());
                
                // 使用 Runtime.exec() 而不是 ProcessBuilder
                // 这在某些 Android 版本上更可靠
                String[] envp = new String[0];
                String[] cmd = new String[]{enginePath};
                
                engineProcess = Runtime.getRuntime().exec(cmd, envp, engineWorkDir);
                
                engineWriter = new BufferedWriter(new OutputStreamWriter(engineProcess.getOutputStream()));
                engineReader = new BufferedReader(new InputStreamReader(engineProcess.getInputStream()));
                
                isRunning.set(true);
                
                // 启动输出监听线程
                startOutputListener();
                
                // 等待引擎启动
                Thread.sleep(500);
                
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    JSObject result = new JSObject();
                    result.put("success", true);
                    result.put("message", "Engine initialized");
                    call.resolve(result);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize engine", e);
                isRunning.set(false);
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    JSObject result = new JSObject();
                    result.put("success", false);
                    result.put("error", e.getMessage());
                    call.resolve(result);
                });
            }
        });
    }
    
    /**
     * 获取引擎可执行文件路径
     * Android 10+ 不允许执行 filesDir 中的文件，必须使用 nativeLibraryDir
     */
    private String getEnginePath() {
        // 方法1：直接使用 nativeLibraryDir（推荐）
        String nativeLibDir = getContext().getApplicationInfo().nativeLibraryDir;
        File nativeEngine = new File(nativeLibDir, "libpikafish.so");
        
        Log.d(TAG, "Method 1 - nativeLibraryDir: " + nativeLibDir);
        Log.d(TAG, "Native engine exists: " + nativeEngine.exists());
        Log.d(TAG, "Native engine canExecute: " + nativeEngine.canExecute());
        
        if (nativeEngine.exists() && nativeEngine.canExecute()) {
            return nativeEngine.getAbsolutePath();
        }
        
        // 方法2：尝试列出 nativeLibraryDir 目录内容
        File libDir = new File(nativeLibDir);
        if (libDir.exists() && libDir.isDirectory()) {
            Log.d(TAG, "Listing " + nativeLibDir + ":");
            File[] files = libDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    Log.d(TAG, "  " + f.getName() + " - canExecute: " + f.canExecute());
                    if (f.getName().contains("pikafish")) {
                        return f.getAbsolutePath();
                    }
                }
            }
        }
        
        // 方法3：检查父目录
        File parentDir = libDir.getParentFile();
        if (parentDir != null && parentDir.exists()) {
            Log.d(TAG, "Checking parent dir: " + parentDir.getAbsolutePath());
            File[] subdirs = parentDir.listFiles();
            if (subdirs != null) {
                for (File subdir : subdirs) {
                    Log.d(TAG, "  Subdir: " + subdir.getName());
                    File engine = new File(subdir, "libpikafish.so");
                    if (engine.exists()) {
                        Log.d(TAG, "  Found engine at: " + engine.getAbsolutePath());
                        Log.d(TAG, "  Can execute: " + engine.canExecute());
                        if (engine.canExecute()) {
                            return engine.getAbsolutePath();
                        }
                    }
                }
            }
        }
        
        // 方法4：使用 linker 执行（Android 特殊方式）
        File linker = new File("/system/bin/linker64");
        if (!linker.exists()) {
            linker = new File("/system/bin/linker");
        }
        
        if (linker.exists() && nativeEngine.exists()) {
            Log.d(TAG, "Trying linker method with: " + linker.getAbsolutePath());
            return linker.getAbsolutePath() + " " + nativeEngine.getAbsolutePath();
        }
        
        return null;
    }
    
    @PluginMethod
    public void sendCommand(PluginCall call) {
        String command = call.getString("command");
        if (command == null || command.isEmpty()) {
            call.reject("Command is required");
            return;
        }
        
        if (!isRunning.get() || engineWriter == null) {
            call.reject("Engine not initialized");
            return;
        }
        
        executor.execute(() -> {
            try {
                Log.d(TAG, ">>> " + command);
                engineWriter.write(command + "\n");
                engineWriter.flush();
                
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    JSObject result = new JSObject();
                    result.put("success", true);
                    call.resolve(result);
                });
            } catch (IOException e) {
                Log.e(TAG, "Failed to send command", e);
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    JSObject result = new JSObject();
                    result.put("success", false);
                    result.put("error", e.getMessage());
                    call.resolve(result);
                });
            }
        });
    }
    
    @PluginMethod
    public void setMessageCallback(PluginCall call) {
        call.resolve();
    }
    
    @PluginMethod
    public void quit(PluginCall call) {
        if (!isRunning.get()) {
            call.resolve();
            return;
        }
        
        executor.execute(() -> {
            try {
                if (engineWriter != null) {
                    engineWriter.write("quit\n");
                    engineWriter.flush();
                }
                if (engineProcess != null) {
                    engineProcess.waitFor();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error quitting engine", e);
            } finally {
                cleanup();
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> call.resolve());
            }
        });
    }
    
    private void startOutputListener() {
        outputThread = new Thread(() -> {
            try {
                String line;
                while (isRunning.get() && engineProcess != null && engineProcess.isAlive()) {
                    line = engineReader.readLine();
                    if (line != null) {
                        Log.d(TAG, "<<< " + line);
                        
                        if (line.contains("readyok")) {
                            isReady.set(true);
                        }
                        
                        JSObject data = new JSObject();
                        data.put("message", line);
                        notifyListeners("engineMessage", data);
                    }
                }
            } catch (IOException e) {
                if (isRunning.get()) {
                    Log.e(TAG, "Error reading engine output", e);
                }
            }
        });
        outputThread.start();
    }
    
    private void cleanup() {
        isRunning.set(false);
        isReady.set(false);
        
        try {
            if (engineWriter != null) {
                engineWriter.close();
                engineWriter = null;
            }
            if (engineReader != null) {
                engineReader.close();
                engineReader = null;
            }
            if (engineProcess != null) {
                engineProcess.destroy();
                engineProcess = null;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error cleaning up", e);
        }
    }
    
    @Override
    protected void handleOnDestroy() {
        cleanup();
        super.handleOnDestroy();
    }
}
