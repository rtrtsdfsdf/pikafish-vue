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
import java.io.FileOutputStream;
import java.io.IOException;
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
     * 优先使用 nativeLibraryDir（有正确的 SELinux 上下文）
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
        
        // 方法3：从 APK 提取（最后的手段，可能因 SELinux 失败）
        debug("Engine not in nativeLibraryDir, trying to extract from APK...");
        File extracted = extractFromApk();
        if (extracted != null && extracted.canExecute()) {
            return extracted.getAbsolutePath();
        }
        
        return null;
    }
    
    private File extractFromApk() {
        try {
            String apkPath = getContext().getPackageResourcePath();
            File targetFile = new File(getContext().getFilesDir(), "pikafish");
            
            // 缓存检查
            if (targetFile.exists() && targetFile.length() > 1000000) {
                debug("Using cached extraction: " + targetFile.length() + " bytes");
                if (targetFile.canExecute()) {
                    return targetFile;
                }
                // 尝试设置权限
                targetFile.setExecutable(true, false);
                try {
                    Runtime.getRuntime().exec(new String[]{"chmod", "755", targetFile.getAbsolutePath()}).waitFor();
                } catch (Exception ignored) {}
                if (targetFile.canExecute()) {
                    return targetFile;
                }
                debug("Cached file not executable, re-extracting...");
            }
            
            debug("Extracting from APK: " + apkPath);
            ZipFile zipFile = new ZipFile(apkPath);
            
            String[] paths = {"lib/arm64-v8a/libpikafish.so", "lib/armeabi-v7a/libpikafish.so"};
            ZipEntry entry = null;
            for (String path : paths) {
                entry = zipFile.getEntry(path);
                if (entry != null) {
                    debug("Found: " + path);
                    break;
                }
            }
            
            if (entry == null) {
                debug("Engine not found in APK");
                zipFile.close();
                return null;
            }
            
            // 提取
            java.io.InputStream is = zipFile.getInputStream(entry);
            FileOutputStream fos = new FileOutputStream(targetFile);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fos.close();
            is.close();
            zipFile.close();
            
            debug("Extracted: " + targetFile.length() + " bytes");
            
            // 设置权限
            targetFile.setExecutable(true, false);
            targetFile.setReadable(true, false);
            try {
                Runtime.getRuntime().exec(new String[]{"chmod", "755", targetFile.getAbsolutePath()}).waitFor();
            } catch (Exception ignored) {}
            
            debug("Can execute: " + targetFile.canExecute());
            return targetFile;
            
        } catch (Exception e) {
            debug("Extraction failed: " + e.getMessage());
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
