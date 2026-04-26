package com.xiaoyi.pikafish.plugins;

import android.content.pm.ApplicationInfo;
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
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
    
    // 收集调试信息
    private final List<String> debugMessages = new ArrayList<>();
    
    private void debug(String msg) {
        Log.d(TAG, msg);
        debugMessages.add(msg);
        // 发送调试消息到前端
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
                
                // 创建引擎工作目录
                engineWorkDir = new File(getContext().getFilesDir(), "engine");
                if (!engineWorkDir.exists()) {
                    engineWorkDir.mkdirs();
                }
                debug("Work dir: " + engineWorkDir.getAbsolutePath());
                
                // 获取引擎文件路径
                String enginePath = findEngine();
                
                if (enginePath == null) {
                    throw new RuntimeException("Engine library not found. See debug logs for details.");
                }
                
                debug("Using engine: " + enginePath);
                
                // 启动引擎
                debug("Starting process...");
                engineProcess = Runtime.getRuntime().exec(new String[]{enginePath}, null, engineWorkDir);
                
                engineWriter = new BufferedWriter(new OutputStreamWriter(engineProcess.getOutputStream()));
                engineReader = new BufferedReader(new InputStreamReader(engineProcess.getInputStream()));
                
                isRunning.set(true);
                debug("Process started, waiting for output...");
                
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
     * 查找引擎文件
     */
    private String findEngine() {
        debug("--- Searching for engine ---");
        
        ApplicationInfo appInfo = getContext().getApplicationInfo();
        String nativeLibDir = appInfo.nativeLibraryDir;
        
        debug("Application info:");
        debug("  sourceDir: " + appInfo.sourceDir);
        debug("  nativeLibraryDir: " + nativeLibDir);
        debug("  device ABI: " + Build.SUPPORTED_ABIS[0]);
        
        // 方法1：直接检查 nativeLibraryDir
        File libDir = new File(nativeLibDir);
        debug("Method 1: Check nativeLibraryDir");
        debug("  Path: " + nativeLibDir);
        debug("  Exists: " + libDir.exists());
        debug("  IsDirectory: " + libDir.isDirectory());
        
        if (libDir.exists() && libDir.isDirectory()) {
            File[] files = libDir.listFiles();
            debug("  Files count: " + (files != null ? files.length : "null"));
            if (files != null) {
                for (File f : files) {
                    debug("    " + f.getName() + " - size:" + f.length() + " exec:" + f.canExecute() + " read:" + f.canRead());
                    if (f.getName().contains("pikafish") && f.canExecute()) {
                        debug("  FOUND: " + f.getAbsolutePath());
                        return f.getAbsolutePath();
                    }
                }
            }
        }
        
        // 方法2：检查父目录的其他子目录
        File parentDir = libDir.getParentFile();
        debug("Method 2: Check parent directory");
        debug("  Parent: " + (parentDir != null ? parentDir.getAbsolutePath() : "null"));
        
        if (parentDir != null && parentDir.exists()) {
            File[] subdirs = parentDir.listFiles();
            debug("  Subdirs count: " + (subdirs != null ? subdirs.length : "null"));
            if (subdirs != null) {
                for (File subdir : subdirs) {
                    debug("    Subdir: " + subdir.getName());
                    File engine = new File(subdir, "libpikafish.so");
                    if (engine.exists()) {
                        debug("      Found libpikafish.so - size:" + engine.length() + " exec:" + engine.canExecute());
                        if (engine.canExecute()) {
                            debug("  FOUND: " + engine.getAbsolutePath());
                            return engine.getAbsolutePath();
                        }
                    }
                }
            }
        }
        
        // 方法3：检查 APK 中的 lib 目录
        debug("Method 3: Check APK structure");
        String apkPath = appInfo.sourceDir;
        debug("  APK: " + apkPath);
        
        // 列出 APK 中的 lib 目录
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"unzip", "-l", apkPath});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < 50) {
                if (line.contains("lib/") && line.contains(".so")) {
                    debug("  APK content: " + line.trim());
                    count++;
                }
            }
            process.waitFor();
        } catch (Exception e) {
            debug("  Failed to list APK: " + e.getMessage());
        }
        
        // 方法4：尝试使用 app_lib 目录（某些设备的特殊路径）
        String appLibPath = "/data/app-lib/" + getContext().getPackageName();
        File appLibDir = new File(appLibPath);
        debug("Method 4: Check app_lib");
        debug("  Path: " + appLibPath);
        debug("  Exists: " + appLibDir.exists());
        
        if (appLibDir.exists()) {
            File[] files = appLibDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    debug("    " + f.getName());
                    if (f.getName().contains("pikafish") && f.canExecute()) {
                        debug("  FOUND: " + f.getAbsolutePath());
                        return f.getAbsolutePath();
                    }
                }
            }
        }
        
        debug("--- Engine NOT FOUND ---");
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
