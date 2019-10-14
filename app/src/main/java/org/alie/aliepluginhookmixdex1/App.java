package org.alie.aliepluginhookmixdex1;

import android.app.Application;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Environment;

import java.lang.reflect.Method;

/**
 * Created by Alie on 2019/10/6.
 * 运行本app的说明：
 * 本demo是按照6.0源码进行的hook
 * 版本
 */
public class App extends Application {
    private HookUtil hookUtil;
    private AssetManager assetManager;
    private Resources newResource;

    @Override
    public void onCreate() {
        super.onCreate();
        hookUtil = new HookUtil();
        hookUtil.hookStartActivity(this);
        hookUtil.hookActivtyMh();
        hookUtil.injectPluginDex(this);
        String apkPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/playchess-debug.apk";
        try {
            assetManager = AssetManager.class.newInstance();
            Method addAssetPathMethod = assetManager.getClass().getDeclaredMethod("addAssetPath", String.class);
            addAssetPathMethod.setAccessible(true);
            addAssetPathMethod.invoke(assetManager, apkPath);
//        手动实例化
            Method ensureStringBlocks = AssetManager.class.getDeclaredMethod("ensureStringBlocks");
            ensureStringBlocks.setAccessible(true);
            ensureStringBlocks.invoke(assetManager); // 这里调用下，那么StringBlocks数组就被初始化了，也就是说资源文件已经被转化到内存StringBlock数组中了
//            插件的StringBloac被实例化了
            Resources supResource = getResources();
            newResource = new Resources(assetManager, supResource.getDisplayMetrics(), supResource.getConfiguration());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public AssetManager getAssetManager() {
        return assetManager==null?super.getAssets():assetManager;
    }

    @Override
    public Resources getResources() {
        return newResource==null?super.getResources():newResource;
    }
}
