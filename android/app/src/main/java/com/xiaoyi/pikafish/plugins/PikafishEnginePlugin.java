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
    private final AtomicBoolean uciOkReceived = new AtomicBoolean(false);
    
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
                File nnueFile = copyNnueToFilesDir();
                if (nnueFile != null) {
                    debug("NNUE model ready: " + nnueFile.getAbsolutePath());
                }
                
                // 复制引擎到工作目录（因为 nativeLibraryDir 中的文件无法直接执行）
                File engineFile = copyEngineToFilesDir();
                if (engineFile == null) {
                    throw new RuntimeException("Failed to copy engine");
                }
                
                String enginePath = engineFile.getAbsolutePath();
                debug("Using engine: " + enginePath);
                
                // 启动引擎
                debug("Starting engine process...");
                
                // 设置环境变量，让引擎知道 NNUE 文件位置
                String[] envp = null;
                if (nnueFile != null) {
                    envp = new String[] {
                        "PIKAFISH_NNUE_PATH=" + nnueFile.getAbsolutePath()
                    };
                    debug("Setting NNUE env: " + nnueFile.getAbsolutePath());
                }
                
                // Android 10+ SELinux 限制：filesDir 中的文件不能直接执行
                // 使用 linker64 来加载和执行
                String[] cmd;
                if (android.os.Build.VERSION.SDK_INT >= 29) {  // Android 10+
                    // 使用 linker64 执行
                    cmd = new String[]{"/system/bin/linker64", enginePath};
                    debug("Using linker64 for Android 10+");
                } else {
                    cmd = new String[]{enginePath};
                }
                
                engineProcess = Runtime.getRuntime().exec(cmd, envp, engineWorkDir);
                
                engineWriter = new BufferedWriter(new OutputStreamWriter(engineProcess.getOutputStream()));
                engineReader = new BufferedReader(new InputStreamReader(engineProcess.getInputStream()));
                
                isRunning.set(true);
                debug("Process started!");
                
                startOutputListener();
                Thread.sleep(500);
                
                // 初始化 UCI
                debug("Sending uci command...");
                engineWriter.write("uci\n");
                engineWriter.flush();
                
                // 等待 uciok（最多 5 秒）
                int waitCount = 0;
                while (!uciOkReceived.get() && waitCount < 50) {
                    Thread.sleep(100);
                    waitCount++;
                }
                debug("Waited " + (waitCount * 100) + "ms for uciok, received: " + uciOkReceived.get());
                
                // 不需要发送 EvalFile 命令！
                // 引擎会在启动时自动从工作目录加载 pikafish.nnue
                // 只要 NNUE 文件在工作目录中，引擎就能找到它
                if (nnueFile != null) {
                    debug("NNUE file in work dir: " + nnueFile.getAbsolutePath());
                    debug("NNUE exists: " + nnueFile.exists());
                    debug("Engine will auto-load from work directory");
                }
                
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
     * 复制 NNUE 模型到 filesDir（可写目录）
     */
    private File copyNnueToFilesDir() {
        try {
            String nnueName = "pikafish.nnue";
            
            // 复制到工作目录（引擎会在工作目录查找 NNUE）
            File targetFile = new File(engineWorkDir, nnueName);
            
            debug("NNUE target path: " + targetFile.getAbsolutePath());
            debug("Files dir: " + getContext().getFilesDir().getAbsolutePath());
            
            // 缓存检查
            if (targetFile.exists() && targetFile.length() > 40000000) {
                debug("Using cached NNUE: " + targetFile.length() + " bytes");
                return targetFile;
            }
            
            // 检查 assets 中是否存在
            String[] assets = getContext().getAssets().list("engine");
            debug("Assets in engine/: " + (assets != null ? java.util.Arrays.toString(assets) : "null"));
            
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
            
            // 设置文件可读权限
            targetFile.setReadable(true, false);
            
            debug("Copied NNUE: " + total + " bytes to " + targetFile.getAbsolutePath());
            debug("File readable after copy: " + targetFile.canRead());
            return targetFile;
            
        } catch (IOException e) {
            debug("NNUE copy FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 复制引擎到 filesDir（因为 nativeLibraryDir 中的文件无法直接执行）
     */
    private File copyEngineToFilesDir() {
        try {
            String engineName = "pikafish";
            File targetFile = new File(engineWorkDir, engineName);
            
            debug("Engine target path: " + targetFile.getAbsolutePath());
            
            // 缓存检查
            if (targetFile.exists() && targetFile.length() > 500000 && targetFile.canExecute()) {
                debug("Using cached engine: " + targetFile.length() + " bytes");
                return targetFile;
            }
            
            // 从 assets 复制
            InputStream is = getContext().getAssets().open("engine/" + engineName);
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
            
            // 设置可执行权限
            targetFile.setExecutable(true, false);
            targetFile.setReadable(true, false);
            
            debug("Copied engine: " + total + " bytes to " + targetFile.getAbsolutePath());
            debug("File executable: " + targetFile.canExecute());
            return targetFile;
            
        } catch (IOException e) {
            debug("Engine copy FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 查找引擎文件
     */
    private String findEngine() {
        // 新方法：从 filesDir 查找复制的引擎
        File engineInFiles = new File(engineWorkDir, "pikafish");
        if (engineInFiles.exists() && engineInFiles.canExecute()) {
            debug("FOUND in filesDir: " + engineInFiles.getAbsolutePath());
            return engineInFiles.getAbsolutePath();
        }
        
        // 旧方法：从 nativeLibraryDir 查找（备用）
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
                        
                        if (line.equals("uciok")) {
                            uciOkReceived.set(true);
                            debug("uciok received!");
                        }
                        
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
