package com.xiaoyi.pikafish;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.xiaoyi.pikafish.plugins.PikafishEnginePlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // 注册插件
        registerPlugin(PikafishEnginePlugin.class);
        
        super.onCreate(savedInstanceState);
    }
}
