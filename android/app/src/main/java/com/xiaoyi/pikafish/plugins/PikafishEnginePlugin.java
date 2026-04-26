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
                
                // 查找或提取引擎库文件
                File engineFile = findOrExtractEngine();
                
                if (engineFile == null || !engineFile.exists()) {
                    throw new RuntimeException("Engine library not found");
                }
                
                // 确保文件有执行权限
                if (!engineFile.canExecute()) {
                    boolean success = engineFile.setExecutable(true, false);
                    Log.d(TAG, "Set executable: " + success + ", canExecute: " + engineFile.canExecute());
                    
                    // 如果 setExecutable 失败，尝试使用 chmod
                    if (!engineFile.canExecute()) {
                        try {
                            Process chmod = Runtime.getRuntime().exec(new String[]{
                                "chmod", "755", engineFile.getAbsolutePath()
                            });
                            chmod.waitFor();
                            Log.d(TAG, "chmod result, canExecute: " + engineFile.canExecute());
                        } catch (Exception e) {
                            Log.e(TAG, "chmod failed", e);
                        }
                    }
                }
                
                if (!engineFile.canExecute()) {
                    throw new RuntimeException("Cannot set executable permission on " + engineFile.getAbsolutePath());
                }
                
                Log.d(TAG, "Using engine: " + engineFile.getAbsolutePath());
                Log.d(TAG, "Engine size: " + engineFile.length() + " bytes");
                Log.d(TAG, "Can execute: " + engineFile.canExecute());
                Log.d(TAG, "Can read: " + engineFile.canRead());
                
                // 启动引擎进程
                ProcessBuilder pb = new ProcessBuilder(engineFile.getAbsolutePath());
                pb.directory(engineWorkDir);
                pb.redirectErrorStream(true);
                
                engineProcess = pb.start();
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
     * 查找或提取引擎库文件
     */
    private File findOrExtractEngine() throws IOException {
        String nativeLibDir = getContext().getApplicationInfo().nativeLibraryDir;
        Log.d(TAG, "nativeLibraryDir: " + nativeLibDir);
        Log.d(TAG, "Primary ABI: " + Build.SUPPORTED_ABIS[0]);
        
        // 目标文件（缓存）
        File targetFile = new File(getContext().getFilesDir(), "libpikafish.so");
        
        // 如果缓存文件存在且大小正确，直接使用
        if (targetFile.exists() && targetFile.length() > 1000000) {
            Log.d(TAG, "Using cached engine: " + targetFile.getAbsolutePath());
            return targetFile;
        }
        
        // 尝试从 nativeLibraryDir 复制
        File sourceFile = new File(nativeLibDir, "libpikafish.so");
        Log.d(TAG, "Checking native lib: " + sourceFile.getAbsolutePath() + " -> exists: " + sourceFile.exists());
        
        if (sourceFile.exists()) {
            copyFile(sourceFile, targetFile);
            Log.d(TAG, "Copied from native lib dir");
            return targetFile;
        }
        
        // 尝试其他路径
        String[] altPaths = {
            nativeLibDir.replace("/lib/arm64", "/lib/arm64-v8a") + "/libpikafish.so",
            getContext().getApplicationInfo().sourceDir
        };
        
        for (String path : altPaths) {
            Log.d(TAG, "Checking alternative: " + path);
            File f = new File(path);
            if (f.exists()) {
                if (path.endsWith(".so")) {
                    copyFile(f, targetFile);
                    return targetFile;
                } else if (path.endsWith(".apk")) {
                    // 从 APK 提取
                    extractFromApk(path, targetFile);
                    if (targetFile.exists() && targetFile.length() > 1000000) {
                        return targetFile;
                    }
                }
            }
        }
        
        // 最后尝试从 APK 提取
        String apkPath = getContext().getPackageResourcePath();
        Log.d(TAG, "Trying to extract from APK: " + apkPath);
        extractFromApk(apkPath, targetFile);
        
        if (targetFile.exists() && targetFile.length() > 1000000) {
            return targetFile;
        }
        
        return null;
    }
    
    /**
     * 从 APK 中提取引擎
     */
    private void extractFromApk(String apkPath, File targetFile) throws IOException {
        Log.d(TAG, "Extracting from APK: " + apkPath);
        
        // 使用 unzip 提取
        File tempDir = new File(getContext().getCacheDir(), "extract");
        tempDir.mkdirs();
        
        Process process = Runtime.getRuntime().exec(new String[]{
            "unzip", "-o", apkPath, "lib/arm64-v8a/libpikafish.so", "-d", tempDir.getAbsolutePath()
        });
        
        try {
            int exitCode = process.waitFor();
            Log.d(TAG, "Unzip exit code: " + exitCode);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 移动到目标位置
        File extracted = new File(tempDir, "lib/arm64-v8a/libpikafish.so");
        if (extracted.exists()) {
            copyFile(extracted, targetFile);
            Log.d(TAG, "Extracted engine, size: " + targetFile.length());
        } else {
            Log.e(TAG, "Extraction failed, file not found at: " + extracted.getAbsolutePath());
        }
    }
    
    /**
     * 复制文件
     */
    private void copyFile(File src, File dst) throws IOException {
        Log.d(TAG, "Copying " + src.getAbsolutePath() + " -> " + dst.getAbsolutePath());
        
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        
        // 设置权限
        dst.setReadable(true, false);
        dst.setWritable(true, false);
        dst.setExecutable(true, false);
        
        Log.d(TAG, "Copy done, canExecute: " + dst.canExecute());
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
