package com.u1x.damagestats;

import com.mojang.logging.LogUtils;
import com.u1x.damagestats.network.DamageStatsNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 伤害统计 Mod 的公共入口。
 *
 * <p>当前版本只建立可加载、可打包的 Forge 骨架。伤害事件监听与数据持久化将在
 * 统计口径（玩家、实体、DOT、间接伤害和多人同步）确定后再添加。</p>
 */
@Mod(DamageStatsMod.MOD_ID)
public final class DamageStatsMod {
    public static final String MOD_ID = "damagestats";
    private static final Logger LOGGER = LogUtils.getLogger();

    public DamageStatsMod() {
        DamageStatsNetwork.initialize();
        MinecraftForge.EVENT_BUS.register(new DamageStatsEvents());
        LOGGER.info("Damage Stats loaded");
    }
}
