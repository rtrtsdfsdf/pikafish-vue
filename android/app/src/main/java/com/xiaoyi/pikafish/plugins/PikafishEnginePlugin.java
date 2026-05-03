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
import java.util.concurrent.TimeUnit;
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
    
    // 等待 readyok 用
    private final AtomicBoolean readyOkReceived = new AtomicBoolean(false);
    
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
                
                File nnueFile = copyNnueToFilesDir();
                if (nnueFile != null) {
                    debug("NNUE model ready: " + nnueFile.getAbsolutePath());
                }
                
                String enginePath = findEngine();
                
                if (enginePath == null) {
                    throw new RuntimeException("Engine not found");
                }
                
                debug("Using engine: " + enginePath);
                debug("Starting engine process...");
                
                String nativeLibDir = getContext().getApplicationInfo().nativeLibraryDir;
                String[] envp = new String[] {
                    "LD_LIBRARY_PATH=" + nativeLibDir,
                    "PIKAFISH_LIB_DIR=" + nativeLibDir
                };
                debug("Setting PIKAFISH_LIB_DIR: " + nativeLibDir);
                if (nnueFile != null) {
                    debug("NNUE file path: " + nnueFile.getAbsolutePath());
                }
                
                                
                debug("Executing: " + enginePath);
                engineProcess = Runtime.getRuntime().exec(new String[]{enginePath}, envp, engineWorkDir);
                
                engineWriter = new BufferedWriter(new OutputStreamWriter(engineProcess.getOutputStream()));
                engineReader = new BufferedReader(new InputStreamReader(engineProcess.getInputStream()));
                
                // 同时读取错误流
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(engineProcess.getErrorStream()));
                
                isRunning.set(true);
                debug("Process started!");
                
                // 启动错误流监听
                new Thread(() -> {
                    try {
                        String line;
                        while (engineProcess.isAlive()) {
                            line = errorReader.readLine();
                            if (line != null) {
                                debug("STDERR: " + line);
                            }
                        }
                        debug("STDERR thread exiting, process alive: " + engineProcess.isAlive());
                    } catch (IOException e) {
                        debug("Error reader: " + e.getMessage());
                    }
                }).start();
                
                startOutputListener();
                
                // 等待一下，检查进程是否存活
                Thread.sleep(500);
                if (!engineProcess.isAlive()) {
                    debug("Process exited with code: " + engineProcess.exitValue());
                    throw new RuntimeException("Engine process exited immediately");
                }
                debug("Process is alive, continuing...");
                
                // 初始化 UCI
                debug("Sending uci command...");
                writeLine("uci");
                
                // 等待 uciok（最多 3 秒）
                int waitCount = 0;
                while (!uciOkReceived.get() && waitCount < 30) {
                    Thread.sleep(100);
                    waitCount++;
                }
                debug("Waited " + (waitCount * 100) + "ms for uciok, received: " + uciOkReceived.get());
                
                if (!uciOkReceived.get()) {
                    throw new RuntimeException("uciok not received within timeout");
                }
                
                // 发送 EvalFile 命令，显式指定 NNUE 路径（非必须，引擎自动查找）
                // 但如果 NNUE 在工作目录中，引擎会自行找到
                // 不再显式发送 EvalFile，避免引擎崩溃
                
                // 发送 isready 等待引擎就绪
                readyOkReceived.set(false);
                writeLine("isready");
                
                int readyCount = 0;
                while (!readyOkReceived.get() && readyCount < 30) {
                    Thread.sleep(100);
                    readyCount++;
                }
                debug("Ready check: " + (readyCount * 100) + "ms, received: " + readyOkReceived.get());
                
                if (!readyOkReceived.get()) {
                    debug("WARNING: readyok not received, continuing anyway");
                }
                
                isRunning.set(true);
                isReady.set(true);
                
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    JSObject result = new JSObject();
                    result.put("success", true);
                    result.put("message", "Engine initialized");
                    call.resolve(result);
                });
                
            } catch (Exception e) {
                debug("ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                cleanup();
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
     * 统一写入方法，失败时标记 isRunning=false
     */
    private void writeLine(String line) throws IOException {
        if (!isRunning.get() || engineWriter == null) {
            throw new IOException("Engine not running");
        }
        if (!engineProcess.isAlive()) {
            isRunning.set(false);
            debug("Process died while writing, sending error stream");
            // 尝试读取剩余的 stderr/stdout 来诊断
            throw new IOException("Process exited with code: " + engineProcess.exitValue());
        }
        engineWriter.write(line + "\n");
        engineWriter.flush();
    }
    
    /**
     * 复制 NNUE 模型到 filesDir
     */
    private File copyNnueToFilesDir() {
        try {
            String nnueName = "pikafish.nnue";
            
            File targetFile = new File(engineWorkDir, nnueName);
            
            debug("NNUE target path: " + targetFile.getAbsolutePath());
            debug("Files dir: " + getContext().getFilesDir().getAbsolutePath());
            
            if (targetFile.exists() && targetFile.length() > 40000000) {
                debug("Using cached NNUE: " + targetFile.length() + " bytes");
                return targetFile;
            }
            
            String[] assets = getContext().getAssets().list("engine");
            debug("Assets in engine/: " + (assets != null ? java.util.Arrays.toString(assets) : "null"));
            
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
            
            targetFile.setReadable(true, false);
            
            debug("Copied NNUE: " + total + " bytes to " + targetFile.getAbsolutePath());
            debug("File readable after copy: " + targetFile.canRead());
            return targetFile;
            
        } catch (IOException e) {
            debug("NNUE copy FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }
    
    private String findEngine() {
        String nativeLibDir = getContext().getApplicationInfo().nativeLibraryDir;
        debug("nativeLibraryDir: " + nativeLibDir);

        File engine = new File(nativeLibDir, "libpikafish.so");
        if (engine.exists()) {
            debug("FOUND in nativeLibraryDir: " + engine.getAbsolutePath());
            return engine.getAbsolutePath();
        }

        File parentDir = new File(nativeLibDir).getParentFile();
        if (parentDir != null && parentDir.exists()) {
            File[] subdirs = parentDir.listFiles();
            if (subdirs != null) {
                for (File subdir : subdirs) {
                    File altEngine = new File(subdir, "libpikafish.so");
                    if (altEngine.exists()) {
                        debug("FOUND in " + subdir.getName() + ": " + altEngine.getAbsolutePath());
                        return altEngine.getAbsolutePath();
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
        
        if (!isRunning.get() || engineWriter == null || !engineProcess.isAlive()) {
            call.reject("Engine not running or stream closed");
            return;
        }
        
        executor.execute(() -> {
            try {
                debug(">>> " + command);
                writeLine(command);
                
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    JSObject result = new JSObject();
                    result.put("success", true);
                    call.resolve(result);
                });
            } catch (IOException e) {
                debug("Send error: " + e.getMessage());
                isRunning.set(false);
                isReady.set(false);
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    call.reject(e.getMessage());
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
                if (engineWriter != null && engineProcess.isAlive()) {
                    writeLine("quit");
                    engineProcess.waitFor(2000, TimeUnit.MILLISECONDS);
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
                        
                        if (line.startsWith("readyok")) {
                            readyOkReceived.set(true);
                            isReady.set(true);
                        }
                        
                        // 也响应 option 行
                        if (line.startsWith("option")) {
                            // 继续等待 uciok
                        }
                        
                        // 将原始消息发送到 TypeScript 层
                        JSObject data = new JSObject();
                        data.put("message", line);
                        notifyListeners("engineMessage", data);
                    } else {
                        // readLine() 返回 null 表示 EOF -> 进程退出
                        debug("Output stream ended (EOF)");
                        isRunning.set(false);
                        isReady.set(false);
                        break;
                    }
                }
            } catch (IOException e) {
                if (isRunning.get()) {
                    debug("Read error: " + e.getMessage());
                    isRunning.set(false);
                    isReady.set(false);
                }
            }
        });
        outputThread.start();
    }
    
    private void cleanup() {
        isRunning.set(false);
        isReady.set(false);
        uciOkReceived.set(false);
        readyOkReceived.set(false);
        
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
                engineProcess.waitFor(1000, TimeUnit.MILLISECONDS);
                if (engineProcess.isAlive()) {
                    engineProcess.destroyForcibly();
                }
                engineProcess = null;
            }
        } catch (IOException e) {
            debug("Cleanup error: " + e.getMessage());
        } catch (InterruptedException e) {
            debug("Cleanup interrupted: " + e.getMessage());
            if (engineProcess != null) {
                engineProcess.destroyForcibly();
            }
        }
    }
    
    @Override
    protected void handleOnDestroy() {
        cleanup();
        super.handleOnDestroy();
    }
}
