// 探针：isVip=true + 以 App 自身 UID 启动指定 Activity（结构取证用，不提取付费数据）
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
        console.log('[probe] isVip hooked=' + n);
    } catch (e) { console.log('[probe] hook fail ' + e); }

    Java.scheduleOnMainThread(function () {
        try {
            var AT = Java.use('android.app.ActivityThread');
            var ctx = AT.currentApplication().getApplicationContext();
            var Intent = Java.use('android.content.Intent');
            var i = Intent.$new();
            i.setClassName('space.xinzhi.globe', '__TARGET__');
            i.addFlags(0x10000000);
            ctx.startActivity(i);
            console.log('[probe] started __TARGET__');
        } catch (e) { console.log('[probe] start fail ' + e); }
    });
});
