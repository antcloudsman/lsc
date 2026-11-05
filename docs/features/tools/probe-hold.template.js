// 常驻探针：仅把会员判断改成 true（不提取数据），供点击流程用
Java.perform(function () {
    try {
        var A = Java.use('space.xinzhi.globe.model.AccountModelKt');
        var n = 0;
        A.class.getDeclaredMethods().forEach(function (m) {
            if (m.getName() === 'isVip') {
                try {
                    A[m.getName()].overloads.forEach(function (o) {
                        o.implementation = function () { return true; };
                        n++;
                    });
                } catch (e) {}
            }
        });
        console.log('[probe] hold isVip=' + n + ' pid=' + Process.id);
    } catch (e) { console.log('[probe] fail ' + e); }
});
