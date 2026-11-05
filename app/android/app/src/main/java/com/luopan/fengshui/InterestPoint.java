package com.luopan.fengshui;

/** 收藏点（本地库一行）：坐标 + 当前坐向。 */
final class InterestPoint {

    final long id;
    final String name;
    final double lat;
    final double lng;
    /** 坐（度，0..360） */
    final double sittingDeg;
    /** 向（度，0..360，= 坐 + 180 归一化） */
    final double facingDeg;
    /** 毫秒时间戳 */
    final long createdAt;

    InterestPoint(long id, String name, double lat, double lng,
                  double sittingDeg, double facingDeg, long createdAt) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.sittingDeg = sittingDeg;
        this.facingDeg = facingDeg;
        this.createdAt = createdAt;
    }
}
