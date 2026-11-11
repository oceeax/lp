package com.example.screenwake;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

public class AppClickAccessService extends AccessibilityService {
    private static AppClickAccessService instance;

    public static AppClickAccessService getInstance(){
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    public boolean clickViewById(String viewId) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if(root == null) return false;
        String fullId = getPackageName() + ":id/" + viewId;
        List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByViewId(fullId);
        if(list.isEmpty()){
            root.recycle();
            return false;
        }
        AccessibilityNodeInfo node = list.get(0);
        if(node.isClickable()){
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        node.recycle();
        root.recycle();
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override
    public void onInterrupt() {}
}
