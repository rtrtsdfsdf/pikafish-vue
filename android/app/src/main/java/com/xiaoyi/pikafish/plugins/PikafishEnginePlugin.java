package com.xiaoyi.pikafish.plugins;

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
                
                // 复制 NNUE 文件（如果存在）
                File nnueFile = new File(engineWorkDir, "pikafish.nnue");
                if (!nnueFile.exists()) {
                    try {
                        InputStream nnueStream = getContext().getAssets().open("engine/pikafish.nnue");
                        FileOutputStream nnueOut = new FileOutputStream(nnueFile);
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = nnueStream.read(buffer)) != -1) {
                            nnueOut.write(buffer, 0, bytesRead);
                        }
                        nnueOut.close();
                        nnueStream.close();
                        Log.d(TAG, "NNUE file copied");
                    } catch (IOException e) {
                        Log.w(TAG, "NNUE file not found, engine will work without NNUE");
                    }
                }
                
                // 获取原生库路径
                String nativeLibDir = getContext().getApplicationInfo().nativeLibraryDir;
                File engineFile = new File(nativeLibDir, "libpikafish.so");
                
                Log.d(TAG, "Native lib dir: " + nativeLibDir);
                Log.d(TAG, "Engine file: " + engineFile.getAbsolutePath());
                Log.d(TAG, "Engine exists: " + engineFile.exists());
                
                if (!engineFile.exists()) {
                    throw new RuntimeException("Engine library not found: " + engineFile.getAbsolutePath());
                }
                
                // 启动引擎进程
                ProcessBuilder pb = new ProcessBuilder(engineFile.getAbsolutePath());
                pb.directory(engineWorkDir);  // 设置工作目录，引擎会在这里找 NNUE 文件
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
                        
                        // 检查 readyok
                        if (line.contains("readyok")) {
                            isReady.set(true);
                        }
                        
                        // 发送事件到 JavaScript
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
