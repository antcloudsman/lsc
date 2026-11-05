package com.luopan.fengshui;

/** 工具管理页的一行：目录项 + 本地排序/启用状态。 */
final class ToolState {

    final String key;
    final String label;
    final int sort;
    final boolean enabled;

    ToolState(String key, String label, int sort, boolean enabled) {
        this.key = key;
        this.label = label;
        this.sort = sort;
        this.enabled = enabled;
    }
}
