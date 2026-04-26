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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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
                
                // 从 APK 提取引擎
                File engineFile = extractEngineFromApk();
                
                if (engineFile == null || !engineFile.exists()) {
                    throw new RuntimeException("Failed to extract engine from APK");
                }
                
                debug("Engine extracted: " + engineFile.getAbsolutePath());
                debug("Engine size: " + engineFile.length());
                debug("Can execute: " + engineFile.canExecute());
                
                // 尝试设置执行权限
                if (!engineFile.canExecute()) {
                    // 方法1：Java API
                    engineFile.setExecutable(true, false);
                    debug("setExecutable result: " + engineFile.canExecute());
                    
                    // 方法2：chmod
                    if (!engineFile.canExecute()) {
                        try {
                            Process chmod = Runtime.getRuntime().exec(new String[]{
                                "chmod", "755", engineFile.getAbsolutePath()
                            });
                            chmod.waitFor();
                            debug("chmod result: " + engineFile.canExecute());
                        } catch (Exception e) {
                            debug("chmod failed: " + e.getMessage());
                        }
                    }
                }
                
                if (!engineFile.canExecute()) {
                    throw new RuntimeException("Cannot set executable permission");
                }
                
                debug("Starting engine process...");
                engineProcess = Runtime.getRuntime().exec(new String[]{engineFile.getAbsolutePath()}, null, engineWorkDir);
                
                engineWriter = new BufferedWriter(new OutputStreamWriter(engineProcess.getOutputStream()));
                engineReader = new BufferedReader(new InputStreamReader(engineProcess.getInputStream()));
                
                isRunning.set(true);
                debug("Process started!");
                
                startOutputListener();
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
     * 从 APK 中提取引擎文件
     */
    private File extractEngineFromApk() {
        try {
            String apkPath = getContext().getPackageResourcePath();
            debug("APK path: " + apkPath);
            
            File targetFile = new File(getContext().getFilesDir(), "pikafish");
            debug("Target: " + targetFile.getAbsolutePath());
            
            // 如果已存在且大小正确，直接返回
            if (targetFile.exists() && targetFile.length() > 1000000) {
                debug("Using cached engine");
                return targetFile;
            }
            
            // 使用 ZipFile 提取
            ZipFile zipFile = new ZipFile(apkPath);
            
            // 尝试不同的 ABI 路径
            String[] possiblePaths = {
                "lib/arm64-v8a/libpikafish.so",
                "lib/arm64/libpikafish.so",
                "lib/armeabi-v7a/libpikafish.so"
            };
            
            ZipEntry entry = null;
            for (String path : possiblePaths) {
                entry = zipFile.getEntry(path);
                if (entry != null) {
                    debug("Found in APK: " + path);
                    break;
                }
            }
            
            if (entry == null) {
                debug("Engine not found in APK, listing all .so files:");
                java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    if (e.getName().endsWith(".so")) {
                        debug("  " + e.getName() + " (" + e.getSize() + " bytes)");
                    }
                }
                zipFile.close();
                return null;
            }
            
            // 提取文件
            debug("Extracting " + entry.getName() + " (" + entry.getSize() + " bytes)");
            InputStream is = zipFile.getInputStream(entry);
            FileOutputStream fos = new FileOutputStream(targetFile);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            
            fos.close();
            is.close();
            zipFile.close();
            
            debug("Extraction complete, size: " + targetFile.length());
            
            return targetFile;
            
        } catch (Exception e) {
            debug("Extraction failed: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
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
