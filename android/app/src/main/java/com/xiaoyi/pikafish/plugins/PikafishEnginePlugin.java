package com.xiaoyi.pikafish.plugins;

import android.content.Context;
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
    
    private void debug(String msg) {
        Log.d(TAG, msg);
        JSObject data = new JSObject();
        data.put("message", "[DEBUG] " + msg);
        notifyListeners("engineMessage", data);
    }
    
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
                debug("=== Starting engine initialization ===");
                
                engineWorkDir = new File(getContext().getFilesDir(), "engine");
                if (!engineWorkDir.exists()) {
                    engineWorkDir.mkdirs();
                }
                debug("Work dir: " + engineWorkDir.getAbsolutePath());
                
                // 复制 NNUE 模型到工作目录
                File nnueFile = copyNnueFromAssets();
                if (nnueFile != null) {
                    debug("NNUE model ready: " + nnueFile.getAbsolutePath());
                }
                
                // 查找引擎
                String enginePath = findEngine();
                
                if (enginePath == null) {
                    throw new RuntimeException("Engine not found");
                }
                
                debug("Using engine: " + enginePath);
                
                // 启动引擎
                debug("Starting engine process...");
                engineProcess = Runtime.getRuntime().exec(new String[]{enginePath}, null, engineWorkDir);
                
                engineWriter = new BufferedWriter(new OutputStreamWriter(engineProcess.getOutputStream()));
                engineReader = new BufferedReader(new InputStreamReader(engineProcess.getInputStream()));
                
                isRunning.set(true);
                debug("Process started!");
                
                startOutputListener();
                Thread.sleep(500);
                
                // 加载 NNUE 模型
                if (nnueFile != null) {
                    debug("Loading NNUE model...");
                    engineWriter.write("setoption name EvalFile value " + nnueFile.getName() + "\n");
                    engineWriter.flush();
                    Thread.sleep(200);
                }
                
                // 初始化 UCI
                engineWriter.write("uci\n");
                engineWriter.flush();
                Thread.sleep(500);
                
                engineWriter.write("isready\n");
                engineWriter.flush();
                
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    JSObject result = new JSObject();
                    result.put("success", true);
                    result.put("message", "Engine initialized");
                    call.resolve(result);
                });
                
            } catch (Exception e) {
                debug("ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
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
     * 从 assets 复制 NNUE 模型
     */
    private File copyNnueFromAssets() {
        try {
            String nnueName = "pikafish.nnue";
            File targetFile = new File(engineWorkDir, nnueName);
            
            // 缓存检查
            if (targetFile.exists() && targetFile.length() > 40000000) {
                debug("Using cached NNUE: " + targetFile.length() + " bytes");
                return targetFile;
            }
            
            // 从 assets 复制
            InputStream is = getContext().getAssets().open("engine/" + nnueName);
            FileOutputStream fos = new FileOutputStream(targetFile);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            long total = 0;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                total += bytesRead;
            }
            
            fos.close();
            is.close();
            
            debug("Copied NNUE: " + total + " bytes");
            return targetFile;
            
        } catch (IOException e) {
            debug("NNUE not available: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 查找引擎文件
     */
    private String findEngine() {
        String nativeLibDir = getContext().getApplicationInfo().nativeLibraryDir;
        debug("nativeLibraryDir: " + nativeLibDir);
        
        // 方法1：直接检查 nativeLibraryDir
        File libDir = new File(nativeLibDir);
        if (libDir.exists() && libDir.isDirectory()) {
            File[] files = libDir.listFiles();
            debug("nativeLibraryDir files: " + (files != null ? files.length : 0));
            if (files != null) {
                for (File f : files) {
                    debug("  " + f.getName() + " exec:" + f.canExecute());
                    if (f.getName().contains("pikafish") && f.canExecute()) {
                        debug("FOUND in nativeLibraryDir: " + f.getAbsolutePath());
                        return f.getAbsolutePath();
                    }
                }
            }
        }
        
        // 方法2：检查父目录的其他 ABI
        File parentDir = libDir.getParentFile();
        if (parentDir != null && parentDir.exists()) {
            File[] subdirs = parentDir.listFiles();
            if (subdirs != null) {
                for (File subdir : subdirs) {
                    File engine = new File(subdir, "libpikafish.so");
                    if (engine.exists() && engine.canExecute()) {
                        debug("FOUND in " + subdir.getName() + ": " + engine.getAbsolutePath());
                        return engine.getAbsolutePath();
                    }
                }
            }
        }
        
        debug("Engine not found in nativeLibraryDir");
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
                debug(">>> " + command);
                engineWriter.write(command + "\n");
                engineWriter.flush();
                
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    JSObject result = new JSObject();
                    result.put("success", true);
                    call.resolve(result);
                });
            } catch (IOException e) {
                debug("Send error: " + e.getMessage());
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
                debug("Quit error: " + e.getMessage());
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
                        debug("<<< " + line);
                        
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
                    debug("Read error: " + e.getMessage());
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
            debug("Cleanup error: " + e.getMessage());
        }
    }
    
    @Override
    protected void handleOnDestroy() {
        cleanup();
        super.handleOnDestroy();
    }
}
