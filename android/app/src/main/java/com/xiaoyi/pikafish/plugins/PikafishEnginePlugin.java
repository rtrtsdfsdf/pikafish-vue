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
                
                // 查找引擎库文件
                File engineFile = findEngineLibrary();
                
                if (engineFile == null || !engineFile.exists()) {
                    throw new RuntimeException("Engine library not found. Searched paths logged above.");
                }
                
                Log.d(TAG, "Using engine: " + engineFile.getAbsolutePath());
                
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
     * 查找引擎库文件
     */
    private File findEngineLibrary() {
        String nativeLibDir = getContext().getApplicationInfo().nativeLibraryDir;
        Log.d(TAG, "nativeLibraryDir: " + nativeLibDir);
        
        // 可能的路径列表
        String[] possiblePaths = {
            nativeLibDir + "/libpikafish.so",
            nativeLibDir + "/../arm64-v8a/libpikafish.so",
            nativeLibDir + "/../arm64/libpikafish.so",
            getContext().getFilesDir() + "/libpikafish.so",
        };
        
        // 也尝试从 APK 中提取
        String abi = Build.SUPPORTED_ABIS[0];
        Log.d(TAG, "Primary ABI: " + abi);
        
        for (String path : possiblePaths) {
            File file = new File(path);
            Log.d(TAG, "Checking: " + path + " -> exists: " + file.exists());
            if (file.exists() && file.canExecute()) {
                return file;
            }
        }
        
        // 尝试从 APK 中提取
        try {
            File extractedFile = extractEngineFromApk();
            if (extractedFile != null) {
                return extractedFile;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract engine from APK", e);
        }
        
        return null;
    }
    
    /**
     * 从 APK 中提取引擎
     */
    private File extractEngineFromApk() throws IOException {
        // 获取 APK 路径
        String apkPath = getContext().getPackageResourcePath();
        Log.d(TAG, "APK path: " + apkPath);
        
        // 目标文件
        File targetFile = new File(getContext().getFilesDir(), "libpikafish.so");
        
        // 如果已经提取过，直接返回
        if (targetFile.exists() && targetFile.length() > 1000000) {
            targetFile.setExecutable(true);
            Log.d(TAG, "Using cached engine: " + targetFile.getAbsolutePath());
            return targetFile;
        }
        
        // 使用 unzip 提取
        Process process = Runtime.getRuntime().exec(new String[]{
            "unzip", "-o", apkPath, "lib/arm64-v8a/libpikafish.so", "-d", getContext().getCacheDir().getAbsolutePath()
        });
        
        try {
            int exitCode = process.waitFor();
            Log.d(TAG, "Unzip exit code: " + exitCode);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 移动到目标位置
        File extracted = new File(getContext().getCacheDir(), "lib/arm64-v8a/libpikafish.so");
        if (extracted.exists()) {
            copyFile(extracted, targetFile);
            targetFile.setExecutable(true);
            targetFile.setReadable(true);
            Log.d(TAG, "Extracted engine to: " + targetFile.getAbsolutePath());
            return targetFile;
        }
        
        return null;
    }
    
    /**
     * 复制文件
     */
    private void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new java.io.FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
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
