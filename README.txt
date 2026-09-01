Damage Stats 0.1.0（Minecraft Forge 1.20.1）
================================================

开源许可：MIT

安装：
  - 单人模式：将本 JAR 放入客户端 mods。
  - 多人模式：服务端和所有玩家客户端都安装相同版本。

用法：
  /damagestats on
  /damagestats off
  /damagestats last [编号]
  /damagestats history [1..100]
  /damagestats history time [1..100]
  /damagestats history damage [1..100]
  /damagestats compare [编号A 编号B]

  /damagestats last 会打开最新记录；附加编号可直接打开当前历史中的指定记录。
  /damagestats compare 无参数时对比本次与上次；附加两个编号时对比指定记录。
  伤害构成界面可切换真实顺序、即时增量、移除损失和按 Mod 分类。
  “移除损失”会移除指定步骤后重放后续加法/乘算，减少后置乘算的位置偏差。
  列表默认为紧凑行，点击展开来源类、说明和 UUID；证据、运算和说明均相同的未知来源可折叠为 n×。
  有具体物品的条目会显示图标，悬浮图标可查看原生物品提示。
  右键条目可在条目旁查看完整信息、从物品栏/装备栏/Curios 饰品栏选择持久化图标，或清除手动标注。
  未知来源的手动标注按阶段、Mod、来源、显示名、运算特征和现场证据细分，避免不同未知来源串用图标。
  所有按钮均可悬停查看用途；界面和命令反馈支持 Minecraft 1.20.1 的标准语言列表。
  每条伤害有当前服务器会话内的递增编号；history 打开可滚动历史 GUI，compare 打开阶段差异 GUI。
  三个列表 GUI 都支持鼠标滚轮和右侧拖动滚动条。

0.1.0 范围：
  - 主入口跟踪玩家 Player.attack 的主目标攻击。
  - 同时在 LivingEntity.hurt 提供玩家归属伤害的后备入口，用于记录绕过 Player.attack 的模组伤害及投射物。
  - 记录攻击属性、冷却、暴击、附魔、Forge 伤害事件、护甲、魔法减免、吸收和实际扣血。
  - 可将攻击属性修饰符归因到装备；安装 Curios 时尝试归因到对应饰品。
  - 对 AttributesLib/Apotheosis 暴击采用算法匹配推断。无法确定的外部修改会标记为“未知归因”，不伪造来源。
  - 不保证跟踪无法追溯到玩家的持续伤害，也尚未聚合连招时间窗。
  - LivingHurtEvent 和 LivingDamageEvent 优先逐监听器精确采样；无法插桩时退回优先级区间候选。
  - 监听器类/方法会与攻击者和目标当时的物品、附魔、Curios 饰品及状态效果做通用弱匹配；结果明确标为“推断”。
  - 已开启统计的玩家攻击会向 latest.log 写入 [DamageStats/Audit] 审计行，用于排查未建立记录的攻击。

开发构建（JDK 17）：
  .\\gradlew.bat build

产物：build\\libs\\damagestats-0.1.0.jar
